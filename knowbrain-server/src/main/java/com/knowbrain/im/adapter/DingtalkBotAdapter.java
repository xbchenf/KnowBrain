package com.knowbrain.im.adapter;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 钉钉 API 工具类 — 提供 AccessToken 管理 + 用户/部门 API 查询。
 *
 * <p>消息收发已由 {@link DingtalkStreamBotHandler} + {@link DingtalkStreamConfig}
 * 通过 WebSocket 长连接处理。本类仅保留 REST API 工具方法，
 * 供管理后台（{@code ImAdminService}）和部门解析（{@code ImDeptResolver}）使用。
 *
 * @see <a href="https://open.dingtalk.com/document/resourcedownload/introduction-to-stream-mode">Stream 模式</a>
 */
@Slf4j
@Component
public class DingtalkBotAdapter {

    @Value("${im.dingtalk.app-key}")
    private String appKey;

    @Value("${im.dingtalk.app-secret}")
    private String appSecret;

    @Value("${im.dingtalk.robot-code}")
    private String robotCode;

    /** 钉钉 API 地址 */
    private static final String DINGTALK_API_HOST = "https://oapi.dingtalk.com";

    /** AccessToken 内存缓存 */
    private volatile String accessToken;
    private volatile long accessTokenExpiry;

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        log.info("[钉钉] API 客户端初始化: appKey={}, robotCode={}", appKey, robotCode);
    }

    // ==================== AccessToken ====================

    /**
     * 获取钉钉 API 调用 AccessToken。
     *
     * <p>API: GET /gettoken?appkey={appKey}&appsecret={appSecret}
     * 有效期 7200 秒，提前 5 分钟刷新。
     */
    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 300_000) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 300_000) {
                return accessToken;
            }
            String url = DINGTALK_API_HOST + "/gettoken?appkey=" + appKey + "&appsecret=" + appSecret;
            try (HttpResponse resp = HttpRequest.get(url).timeout(10000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                int errcode = result.getInt("errcode", -1);
                if (errcode != 0) {
                    log.error("[钉钉] 获取 AccessToken 失败: errcode={}, errmsg={}",
                            errcode, result.getStr("errmsg"));
                    throw new BizException(500, "获取钉钉 AccessToken 失败: errcode=" + errcode);
                }
                accessToken = result.getStr("access_token");
                accessTokenExpiry = System.currentTimeMillis() + 7_200_000L;
                log.info("[钉钉] AccessToken 已刷新");
                return accessToken;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("[钉钉] 获取 AccessToken 网络异常: {}", e.toString());
                throw new BizException(500, "获取钉钉 AccessToken 失败");
            }
        }
    }

    // ==================== 用户信息 API ====================

    /**
     * 通过钉钉 API 获取用户显示名。
     *
     * <p>API: POST /topapi/v2/user/get?access_token=TOKEN
     * Body: {"userid": "xxx"}
     * Response: {"errcode":0, "result": {"name":"张三", "mobile":"138...", "dept_id_list":[1,2]}}
     *
     * @param userId 钉钉用户 ID（senderStaffId）
     * @return 用户显示名，失败返回 null
     */
    public String fetchUserName(String userId) {
        try {
            String token = getAccessToken();
            String url = DINGTALK_API_HOST + "/topapi/v2/user/get?access_token=" + token;
            Map<String, Object> body = Map.of("userid", userId);
            try (HttpResponse resp = HttpRequest.post(url)
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                if (result.getInt("errcode", -1) != 0) return null;
                JSONObject user = result.getJSONObject("result");
                return user != null ? user.getStr("name") : null;
            }
        } catch (Exception e) {
            log.debug("[钉钉] 获取用户名称失败: userId={}, error={}", userId, e.toString());
            return null;
        }
    }

    /**
     * 通过钉钉 API 获取用户所属部门 ID 列表。
     *
     * @param userId 钉钉用户 ID（senderStaffId）
     * @return 部门 ID 列表（整数转为字符串），失败返回空列表
     */
    public List<String> fetchUserDepartments(String userId) {
        try {
            String token = getAccessToken();
            String url = DINGTALK_API_HOST + "/topapi/v2/user/get?access_token=" + token;
            Map<String, Object> body = Map.of("userid", userId);
            try (HttpResponse resp = HttpRequest.post(url)
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                if (result.getInt("errcode", -1) != 0) return List.of();
                JSONObject user = result.getJSONObject("result");
                if (user == null) return List.of();
                List<Integer> deptIds = user.getBeanList("dept_id_list", Integer.class);
                return deptIds != null
                        ? deptIds.stream().map(String::valueOf).toList()
                        : List.of();
            }
        } catch (Exception e) {
            log.warn("[钉钉] 获取用户部门失败: userId={}, error={}", userId, e.toString());
            return List.of();
        }
    }

    // ==================== 部门列表 API ====================

    /**
     * 获取钉钉完整部门列表（递归拉取所有子部门）。
     *
     * <p>API: POST /topapi/v2/department/listsub?access_token=TOKEN
     * Body: {"dept_id": 1}  — 从根部门开始递归
     *
     * @return 部门列表，每个元素包含 id / name / parentId；失败返回空列表
     */
    public List<Map<String, Object>> fetchDepartmentList() {
        List<Map<String, Object>> all = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // 先获取根部门自身（listsub 只返回子部门，不包含父部门）
        fetchDingtalkRootDept(all, seen);
        // 再递归获取所有子部门
        fetchDingtalkDeptsRecursive(1L, all, seen);
        return all;
    }

    /** 获取根部门（id=1）自身信息 */
    private void fetchDingtalkRootDept(List<Map<String, Object>> all, Set<Long> seen) {
        try {
            String token = getAccessToken();
            String url = DINGTALK_API_HOST + "/topapi/v2/department/get?access_token=" + token;
            Map<String, Object> reqBody = Map.of("dept_id", 1L);
            try (HttpResponse resp = HttpRequest.post(url)
                    .body(JSONUtil.toJsonStr(reqBody))
                    .timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                if (result.getInt("errcode", -1) != 0) {
                    log.debug("[钉钉] 获取根部门信息: errcode={}", result.getInt("errcode"));
                    return;
                }
                JSONObject dept = result.getJSONObject("result");
                if (dept == null) return;
                long deptId = dept.getLong("dept_id", 0L);
                if (deptId <= 0 || !seen.add(deptId)) return;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(deptId));
                item.put("name", dept.getStr("name", ""));
                item.put("parentId", String.valueOf(dept.getLong("parent_id", 0L)));
                all.add(item);
            }
        } catch (Exception e) {
            log.debug("[钉钉] 获取根部门失败: {}", e.toString());
        }
    }

    private void fetchDingtalkDeptsRecursive(long parentId, List<Map<String, Object>> all, Set<Long> seen) {
        try {
            String token = getAccessToken();
            String url = DINGTALK_API_HOST + "/topapi/v2/department/listsub?access_token=" + token;
            Map<String, Object> reqBody = Map.of("dept_id", parentId);
            try (HttpResponse resp = HttpRequest.post(url)
                    .body(JSONUtil.toJsonStr(reqBody))
                    .timeout(5000).execute()) {
                JSONObject result = JSONUtil.parseObj(resp.body());
                if (result.getInt("errcode", -1) != 0) {
                    log.debug("[钉钉] 获取子部门列表: parentId={}, errcode={}",
                            parentId, result.getInt("errcode"));
                    return;
                }
                List<JSONObject> depts = result.getBeanList("result", JSONObject.class);
                if (depts == null || depts.isEmpty()) return;
                for (JSONObject dept : depts) {
                    long deptId = dept.getLong("dept_id", 0L);
                    if (deptId <= 0 || !seen.add(deptId)) continue; // 去重
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(deptId));
                    item.put("name", dept.getStr("name", ""));
                    item.put("parentId", String.valueOf(dept.getLong("parent_id", 0L)));
                    all.add(item);
                    if (deptId != parentId) {
                        fetchDingtalkDeptsRecursive(deptId, all, seen);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[钉钉] 获取部门列表失败: parentId={}, error={}", parentId, e.toString());
        }
    }

    // ==================== 签名工具 ====================

    /**
     * HmacSHA256 签名。
     */
    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            log.error("[钉钉] HmacSHA256 计算失败", e);
            throw new BizException(500, "签名计算失败");
        }
    }
}
