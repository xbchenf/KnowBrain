package com.knowbrain.im.adapter;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.im.model.ImIncomingMessage;
import com.knowbrain.im.model.ImOutgoingMessage;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 企业微信 Bot 适配器 — 回调消息加解密 + 主动回复。
 *
 * <h3>加解密</h3>
 * 按企业微信官方文档实现 AES-256-CBC + PKCS7 + SHA1 签名：
 * <ul>
 *   <li>AESKey = Base64(EncodingAESKey + "=")</li>
 *   <li>IV = AESKey 前 16 字节</li>
 *   <li>加密：random(16B) + msg_len(4B 网络序) + msg + receiveid → AES → Base64</li>
 *   <li>解密：Base64 → AES → 跳过 16B 随机数 → 读 4B 长度 → msg → 验证 receiveid</li>
 *   <li>签名：SHA1(sort(token, timestamp, nonce, encrypt))</li>
 * </ul>
 *
 * <h3>AccessToken 缓存</h3>
 * 在内存中缓存，过期前 5 分钟自动刷新。多实例部署时建议改为 Redis 共享缓存。
 *
 * <h3>安全说明</h3>
 * 企微 API 强制要求 {@code access_token} 和 {@code corpsecret} 通过 Query String
 * 传递（不支持 Header Bearer 认证）。错误日志中已避免打印完整 URL，但仍需确保
 * 网络层（nginx/负载均衡）对 {@code /cgi-bin/*} 路径的访问日志脱敏或关闭 Query 记录。
 * 这是已知的企微 API 设计限制，属于文档化的残余风险。
 *
 * @see <a href="https://developer.work.weixin.qq.com/document/path/91144">加解密方案说明</a>
 * @see <a href="https://developer.work.weixin.qq.com/document/path/100719">接收消息</a>
 */
@Slf4j
@Component
public class WecomBotAdapter implements ImBotAdapter {

    @Value("${im.wecom.corp-id}")
    private String corpId;

    @Value("${im.wecom.callback-token}")
    private String token;

    @Value("${im.wecom.encoding-aes-key}")
    private String encodingAesKey;

    @Value("${im.wecom.agent-id}")
    private String agentId;

    @Value("${im.wecom.secret}")
    private String secret;

    /** AES 密钥（EncodingAESKey Base64 解码后的 32 字节） */
    private byte[] aesKey;

    /** AccessToken 内存缓存（多实例部署建议改用 Redis） */
    private volatile String accessToken;
    private volatile long accessTokenExpiry;

    private static final String WECOM_API_HOST = "https://qyapi.weixin.qq.com";
    private static final DocumentBuilderFactory DBF = createDocumentBuilderFactory();

    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // 禁用外部实体解析，防止 XXE
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignored) {
            // 某些实现不支持，忽略
        }
        return factory;
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        // EncodingAESKey(43位) → 补 "=" → Base64 解码 → 32 字节 AESKey
        if (encodingAesKey != null && encodingAesKey.length() == 43) {
            this.aesKey = Base64.decode(encodingAesKey + "=");
        } else if (encodingAesKey != null) {
            this.aesKey = Base64.decode(encodingAesKey);
        }
        log.info("[企微] 适配器初始化: corpId={}, agentId={}, aesKeyLen={}",
                corpId, agentId, aesKey != null ? aesKey.length : 0);
    }

    // ==================== ImBotAdapter 实现 ====================

    @Override
    public String platform() {
        return "wecom";
    }

    @Override
    public boolean verifySignature(HttpServletRequest request, String body) {
        String msgSignature = request.getParameter("msg_signature");
        String timestamp = request.getParameter("timestamp");
        String nonce = request.getParameter("nonce");

        if (StrUtil.hasBlank(msgSignature, timestamp, nonce)) {
            log.warn("[企微] 签名参数缺失");
            return false;
        }

        // 从 body XML 中提取 Encrypt 字段用于签名计算
        String encrypt = extractXmlField(body, "Encrypt");
        if (encrypt == null) {
            log.warn("[企微] body 中无 Encrypt 字段");
            return false;
        }

        String expected = sha1(sortAndJoin(token, timestamp, nonce, encrypt));
        boolean valid = expected.equals(msgSignature);

        if (!valid) {
            log.debug("[企微] 签名不匹配: expected={}, got={}", expected, msgSignature);
        }
        return valid;
    }

    @Override
    public String handleUrlVerification(HttpServletRequest request, String body) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        String msgSignature = request.getParameter("msg_signature");
        String timestamp = request.getParameter("timestamp");
        String nonce = request.getParameter("nonce");
        String echoStr = request.getParameter("echostr");

        if (StrUtil.hasBlank(msgSignature, timestamp, nonce, echoStr)) {
            log.warn("[企微] URL 验证参数缺失");
            throw new BizException(400, "URL 验证参数缺失");
        }

        // 验证签名
        String expected = sha1(sortAndJoin(token, timestamp, nonce, echoStr));
        if (!expected.equals(msgSignature)) {
            log.warn("[企微] URL 验证签名不匹配");
            throw new BizException(403, "URL 验证签名不匹配");
        }

        // 解密 EchoStr
        String plainText = aesDecrypt(echoStr, corpId);
        log.info("[企微] URL 验证成功");
        return plainText;
    }

    @Override
    public ImIncomingMessage parse(HttpServletRequest request, String body) {
        // 注意：签名已在 verifySignature() 中验证，此处直接解密

        // 从 body XML 中提取 Encrypt 字段
        String encrypt = extractXmlField(body, "Encrypt");
        if (encrypt == null) {
            log.warn("[企微] 消息体无 Encrypt 字段: bodyLen={}, bodyStart={}",
                    body != null ? body.length() : 0,
                    body != null ? body.substring(0, Math.min(200, body.length())) : "null");
            return null;
        }

        // 解密
        String xml = aesDecrypt(encrypt, corpId);

        // XML → ImIncomingMessage
        return parseMessageXml(xml);
    }

    @Override
    public void sendReply(ImOutgoingMessage reply, ImIncomingMessage originalMsg) {
        String accessToken = getAccessToken();
        String content = buildMarkdownContent(reply);

        Map<String, Object> body = Map.of(
                "touser", originalMsg.getFromUserId(),
                "msgtype", "markdown",
                "agentid", Integer.parseInt(agentId),
                "markdown", Map.of("content", content)
        );

        String url = WECOM_API_HOST + "/cgi-bin/message/send?access_token=" + accessToken;
        try (HttpResponse resp = HttpRequest.post(url)
                .body(JSONUtil.toJsonStr(body))
                .timeout(10000)
                .execute()) {
            JSONObject result = JSONUtil.parseObj(resp.body());
            int errcode = result.getInt("errcode", -1);
            if (errcode != 0) {
                log.warn("[企微] 消息发送失败: errcode={}, errmsg={}", errcode, result.getStr("errmsg"));
            }
        } catch (Exception e) {
            log.error("[企微] 消息发送异常", e);
        }
    }

    // ==================== 消息解析 ====================

    /**
     * 将企微 XML 消息体解析为统一消息模型。
     *
     * <pre>{@code
     * <xml>
     *   <ToUserName><![CDATA[corpId]]></ToUserName>
     *   <FromUserName><![CDATA[UserId]]></FromUserName>
     *   <CreateTime>1408091189</CreateTime>
     *   <MsgType><![CDATA[text]]></MsgType>
     *   <Content><![CDATA[问题内容]]></Content>
     *   <MsgId>1234567890</MsgId>
     *   <AgentID>1000002</AgentID>
     *   <ChatId><![CDATA[wrChatId]]></ChatId>
     *   <ChatType><![CDATA[group]]></ChatType>
     * </xml>
     * }</pre>
     */
    private ImIncomingMessage parseMessageXml(String xml) {
        try {
            Document doc = DBF.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();

            String msgType = getText(root, "MsgType");
            if (!"text".equals(msgType)) {
                log.debug("[企微] 非文本消息，忽略: msgType={}", msgType);
                return null;
            }

            String content = getText(root, "Content");
            if (content != null && !content.isEmpty()) {
                // 去除 @机器人 前缀（企微格式: "@机器人名 问题内容"）
                content = content.replaceFirst("^@\\S+\\s+", "").trim();
            }

            ImIncomingMessage msg = new ImIncomingMessage();
            msg.setPlatform("wecom");
            msg.setMessageId(getText(root, "MsgId"));
            msg.setFromUserId(getText(root, "FromUserName"));
            msg.setChatId(getText(root, "ChatId"));
            msg.setChatType(normalizeChatType(getText(root, "ChatType")));
            msg.setContent(content);

            // 企微时间戳为秒级，转为毫秒
            String createTime = getText(root, "CreateTime");
            if (createTime != null) {
                msg.setTimestamp(Long.parseLong(createTime) * 1000);
            }

            // 企微通过 API 主动回复，无 replyTarget
            msg.setReplyTarget(null);
            msg.setReplyTargetExpiry(0);

            return msg;
        } catch (Exception e) {
            log.error("[企微] XML 解析失败", e);
            return null;
        }
    }

    private String normalizeChatType(String raw) {
        if (raw == null) return "single";
        return switch (raw.toLowerCase()) {
            case "group" -> "group";
            case "single" -> "single";
            default -> "single";
        };
    }

    // ==================== AccessToken ====================

    /**
     * 获取企业微信 API 调用的 AccessToken。
     *
     * <p>有效期 7200 秒，提前 5 分钟刷新。使用 double-checked locking
     * 确保多线程安全。多实例部署时建议改为 Redis 缓存共享。
     */
    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 300_000) {
            return accessToken;
        }

        synchronized (this) {
            // double-check
            if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 300_000) {
                return accessToken;
            }

            // 企微 API 要求 corpsecret 在 Query String 中传递（非 Header auth）
            String url = WECOM_API_HOST + "/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + secret;
            try (HttpResponse resp = HttpRequest.get(url).timeout(10000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                int errcode = result.getInt("errcode", -1);
                if (errcode != 0) {
                    log.error("[企微] 获取 AccessToken 失败: errcode={}, errmsg={}",
                            errcode, result.getStr("errmsg"));
                    throw new BizException(500, "获取企业微信 AccessToken 失败: errcode=" + errcode);
                }

                accessToken = result.getStr("access_token");
                int expiresIn = result.getInt("expires_in", 7200);
                accessTokenExpiry = System.currentTimeMillis() + expiresIn * 1000L;

                log.info("[企微] AccessToken 已刷新: expiresIn={}s", expiresIn);
                return accessToken;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("[企微] 获取 AccessToken 网络异常: {}", e.toString());
                throw new BizException(500, "获取企业微信 AccessToken 失败");
            }
        }
    }

    // ==================== 用户信息 API ====================

    /**
     * 通过企微 API 获取用户所属部门 ID 列表。
     *
     * <p>API: GET /cgi-bin/user/get?access_token=TOKEN&amp;userid=USERID
     * 响应: { "errcode": 0, "department": [1, 2], "name": "张三", ... }
     *
     * @param userId 企微用户 ID（即消息回调中的 FromUserName）
     * @return 部门 ID 列表（字符串形式），失败返回空列表
     */
    public List<String> fetchUserDepartments(String userId) {
        try {
            String accessToken = getAccessToken();
            String url = WECOM_API_HOST + "/cgi-bin/user/get?access_token="
                    + accessToken + "&userid=" + userId;
            try (HttpResponse resp = HttpRequest.get(url).timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                int errcode = result.getInt("errcode", -1);
                if (errcode != 0) {
                    log.warn("[企微] 获取用户信息失败: errcode={}, errmsg={}, userId={}",
                            errcode, result.getStr("errmsg"), userId);
                    return List.of();
                }
                List<String> deptIds = result.getBeanList("department", String.class);
                String name = result.getStr("name");
                log.debug("[企微] 获取用户信息成功: userId={}, hasName={}, departments={}",
                        userId, name != null, deptIds);
                return deptIds != null ? deptIds : List.of();
            }
        } catch (Exception e) {
            log.warn("[企微] 获取用户信息网络异常: userId={}, error={}", userId, e.toString());
            return List.of();
        }
    }

    /**
     * 通过企微 API 获取用户显示名。
     *
     * @param userId 企微用户 ID
     * @return 用户显示名，失败返回 null
     */
    public String fetchUserName(String userId) {
        try {
            String accessToken = getAccessToken();
            String url = WECOM_API_HOST + "/cgi-bin/user/get?access_token="
                    + accessToken + "&userid=" + userId;
            try (HttpResponse resp = HttpRequest.get(url).timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                int errcode = result.getInt("errcode", -1);
                if (errcode != 0) return null;
                return result.getStr("name");
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过企微 API 获取用户手机号，用于跨平台身份匹配。
     *
     * <p>API: GET /cgi-bin/user/get?access_token=TOKEN&amp;userid=USERID
     * 响应: { "errcode": 0, "mobile": "138xxxx1234", ... }
     *
     * @param userId 企微用户 ID
     * @return 手机号，失败返回 null（不抛异常，不阻塞主流程）
     */
    public String fetchUserMobile(String userId) {
        try {
            String accessToken = getAccessToken();
            String url = WECOM_API_HOST + "/cgi-bin/user/get?access_token="
                    + accessToken + "&userid=" + userId;
            try (HttpResponse resp = HttpRequest.get(url).timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                int errcode = result.getInt("errcode", -1);
                if (errcode != 0) {
                    log.debug("[企微] 获取用户手机号失败: errcode={}, userId={}", errcode, userId);
                    return null;
                }
                return result.getStr("mobile");
            }
        } catch (Exception e) {
            log.debug("[企微] 获取用户手机号失败: userId={}, error={}", userId, e.toString());
            return null;
        }
    }

    // ==================== 部门列表 API ====================

    /**
     * 获取企业微信完整部门列表（递归拉取所有子部门）。
     *
     * <p>API: GET /cgi-bin/department/list?access_token=TOKEN&id=ID
     * 返回单个部门;不传 id 时返回全量部门树（但为兼容子部门未知的情况，仍用递归方式）。
     *
     * @return 部门列表，每个元素包含 id / name / parentId；失败返回空列表
     */
    public List<Map<String, Object>> fetchDepartmentList() {
        List<Map<String, Object>> all = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        fetchWecomDeptsRecursive(1L, all, seen);
        return all;
    }

    private void fetchWecomDeptsRecursive(long parentId, List<Map<String, Object>> all, Set<Long> seen) {
        try {
            String token = getAccessToken();
            String url = WECOM_API_HOST + "/cgi-bin/department/list?access_token="
                    + token + "&id=" + parentId;
            try (HttpResponse resp = HttpRequest.get(url).timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                if (result.getInt("errcode", -1) != 0) {
                    log.debug("[企微] 获取部门列表: parentId={}, errcode={}",
                            parentId, result.getInt("errcode"));
                    return;
                }
                List<JSONObject> depts = result.getBeanList("department", JSONObject.class);
                if (depts == null || depts.isEmpty()) return;
                for (JSONObject dept : depts) {
                    long id = dept.getLong("id", 0L);
                    if (id <= 0 || !seen.add(id)) continue; // 去重
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(id));
                    item.put("name", dept.getStr("name", ""));
                    item.put("parentId", String.valueOf(dept.getLong("parentid", 0L)));
                    all.add(item);
                    // 递归获取子部门
                    if (id != parentId) {
                        fetchWecomDeptsRecursive(id, all, seen);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[企微] 获取部门列表失败: parentId={}, error={}", parentId, e.toString());
        }
    }

    // ==================== 加解密工具（package-private 用于单元测试） ====================

    /**
     * AES-256-CBC 解密。
     *
     * @param encrypted Base64 编码的密文
     * @param receiveId 企微自建应用填 corpId
     * @return 解密后的明文
     */
    String aesDecrypt(String encrypted, String receiveId) {
        try {
            byte[] ciphertext = Base64.decode(encrypted);

            // 使用 NoPadding 然后手动按 msgLen 提取 — 部分 WeCom 消息的 PKCS7
            // padding 值可能超出 AES 块大小（16），Java PKCS5Padding 会拒绝。
            // 结构: random(16) + msg_len(4) + msg + receiveId + padding
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(ciphertext);

            int msgLen = bytesToInt(decrypted, 16); // 跳过 16 字节随机数，读 4 字节长度
            String msg = new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);
            String tail = new String(decrypted, 20 + msgLen,
                    decrypted.length - 20 - msgLen, StandardCharsets.UTF_8);

            // 验证 receiveid（tail 可能包含 padding 字节，用 startsWith 容忍）
            if (receiveId != null && !tail.startsWith(receiveId)) {
                log.warn("[企微] receiveid 不匹配: expected={}, tailStart={}",
                        receiveId, tail.substring(0, Math.min(receiveId.length(), tail.length())));
            }

            return msg;
        } catch (Exception e) {
            log.error("[企微] AES 解密失败: encryptedLen={}, aesKeyLen={}, exceptionClass={}, exceptionMsg={}",
                    encrypted != null ? encrypted.length() : 0,
                    aesKey != null ? aesKey.length : 0,
                    e.getClass().getSimpleName(), e.getMessage());
            throw new BizException(500, "消息解密失败: " + e.getClass().getSimpleName());
        }
    }

    /** 4 字节大端序 → int */
    private int bytesToInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             | (data[offset + 3] & 0xFF);
    }

    /**
     * AES-256-CBC 加密（package-private 用于单元测试往返验证，生产代码不直接调用）。
     *
     * @param plainText 明文
     * @param receiveId 企微自建应用填 corpId
     * @return Base64 编码的密文
     */
    String aesEncrypt(String plainText, String receiveId) {
        try {
            byte[] random = new byte[16];
            new java.security.SecureRandom().nextBytes(random);

            byte[] textBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);
            byte[] msgLen = intToBytes(textBytes.length);

            // 拼接: random(16) + msg_len(4) + msg + receiveid
            byte[] plain = new byte[16 + 4 + textBytes.length + receiveIdBytes.length];
            System.arraycopy(random, 0, plain, 0, 16);
            System.arraycopy(msgLen, 0, plain, 16, 4);
            System.arraycopy(textBytes, 0, plain, 20, textBytes.length);
            System.arraycopy(receiveIdBytes, 0, plain, 20 + textBytes.length, receiveIdBytes.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(plain);
            return Base64.encode(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /** int → 4 字节大端序 */
    private byte[] intToBytes(int value) {
        return new byte[] {
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    /** SHA1 哈希 */
    String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("SHA1 计算失败", e);
        }
    }

    /** 字节数组 → 十六进制小写字符串 */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /** 字典序排序后拼接 */
    String sortAndJoin(String... items) {
        Arrays.sort(items);
        return String.join("", items);
    }

    // ==================== XML 工具 ====================

    /**
     * 从 XML 中提取指定字段的文本值。
     *
     * <p>优先尝试 CDATA 包裹格式（{@code <Field><![CDATA[value]]></Field>}），
     * 再尝试普通文本格式（{@code <Field>value</Field>}）。
     */
    private String extractXmlField(String xml, String fieldName) {
        if (xml == null) return null;

        // 优先尝试 CDATA 版本 — 避免捕获到 CDATA 标记本身
        String cdataStartTag = "<" + fieldName + "><![CDATA[";
        int start = xml.indexOf(cdataStartTag);
        if (start != -1) {
            start += cdataStartTag.length();
            String cdataEndTag = "]]></" + fieldName + ">";
            int end = xml.indexOf(cdataEndTag);
            return end != -1 ? xml.substring(start, end) : null;
        }

        // 回退：普通文本
        String startTag = "<" + fieldName + ">";
        start = xml.indexOf(startTag);
        if (start == -1) return null;
        start += startTag.length();
        String endTag = "</" + fieldName + ">";
        int end = xml.indexOf(endTag);
        return end != -1 ? xml.substring(start, end) : null;
    }

    /** 从 DOM Element 取子元素文本 */
    private String getText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() == 0) return null;
        String text = list.item(0).getTextContent();
        return (text != null && !text.isBlank()) ? text.trim() : null;
    }

    // ==================== 内容构造 ====================

    /**
     * 构造企微 Markdown 消息内容。
     *
     * <p>流式分段：非流式企微可直接推送完整 Markdown。
     * 流式场景下每次更新为"覆盖编辑"效果（通过连续发送同一条消息实现）。
     */
    private String buildMarkdownContent(ImOutgoingMessage reply) {
        StringBuilder sb = new StringBuilder();
        sb.append("**KnowBrain 回答**\n\n");
        sb.append(reply.getContent());

        if (reply.isStreaming() && !reply.isStreamEnd()) {
            sb.append("\n\n---\n⏳ *回答生成中…*");
        }
        if (reply.isStreamEnd() && reply.getSourceLinks() != null && !reply.getSourceLinks().isEmpty()) {
            sb.append("\n\n---\n📖 **参考来源**：\n");
            for (int i = 0; i < reply.getSourceLinks().size(); i++) {
                sb.append((i + 1)).append(". ").append(reply.getSourceLinks().get(i)).append("\n");
            }
        }

        return sb.toString();
    }
}
