package com.knowbrain.im;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowbrain.im.adapter.DingtalkBotAdapter;
import com.knowbrain.im.adapter.FeishuStreamBotHandler;
import com.knowbrain.im.adapter.WecomBotAdapter;
import com.knowbrain.im.entity.ImDeptMapping;
import com.knowbrain.im.mapper.ImDeptMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IM 用户 → KB 部门解析器。
 *
 * <h3>策略</h3>
 * <ol>
 *   <li>调用平台 API 获取用户所属部门 ID 列表</li>
 *   <li>查 {@code kb_im_dept_mapping} 映射表找对应 KB 部门</li>
 *   <li>匹配不到 → 返回 null（仅 PUBLIC 空间）</li>
 * </ol>
 *
 * <p>失败不抛异常：API 调用失败或映射不存在时安静降级，
 * 不阻塞消息处理主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImDeptResolver {

    private final ImDeptMappingMapper mappingMapper;

    /** 可选注入：未配置企微时此字段为 null */
    @Autowired(required = false)
    private WecomBotAdapter wecomAdapter;

    /** 可选注入：未配置钉钉时此字段为 null */
    @Autowired(required = false)
    private DingtalkBotAdapter dingtalkAdapter;

    /** 可选注入：未配置飞书时此字段为 null（@Lazy 打破循环依赖） */
    @Lazy
    @Autowired(required = false)
    private FeishuStreamBotHandler feishuHandler;

    /**
     * 解析 IM 用户对应的 KB 部门 ID。
     *
     * @param platform  IM 平台（wecom / dingtalk / feishu）
     * @param imUserId  平台用户 ID
     * @return KB 部门 ID；无法解析时返回 null（仅 PUBLIC 空间）
     */
    public Long resolveKbDeptId(String platform, String imUserId) {
        // 获取 IM 侧部门 ID 列表
        List<String> externalDeptIds;
        try {
            externalDeptIds = fetchExternalDeptIds(platform, imUserId);
        } catch (Exception e) {
            log.warn("[IM] 获取用户部门列表失败: platform={}, userId={}", platform, imUserId, e);
            return null;
        }
        if (externalDeptIds.isEmpty()) {
            return null;
        }

        // 查映射表（按顺序匹配第一个）
        for (String extDeptId : externalDeptIds) {
            ImDeptMapping mapping = mappingMapper.selectOne(
                    new LambdaQueryWrapper<ImDeptMapping>()
                            .eq(ImDeptMapping::getPlatform, platform)
                            .eq(ImDeptMapping::getExternalDeptId, extDeptId));
            if (mapping != null) {
                log.info("[IM] 部门映射命中: platform={}, extDept={} → kbDept={}",
                        platform, extDeptId, mapping.getKbDeptId());
                return mapping.getKbDeptId();
            }
        }

        log.debug("[IM] 部门映射未命中: platform={}, userId={}, extDepts={}",
                platform, imUserId, externalDeptIds);
        return null;
    }

    /**
     * 调用平台 API 获取用户所属部门 ID 列表。
     */
    private List<String> fetchExternalDeptIds(String platform, String imUserId) {
        switch (platform) {
            case "wecom":
                if (wecomAdapter != null) {
                    return wecomAdapter.fetchUserDepartments(imUserId);
                }
                log.debug("[IM] WecomBotAdapter 未配置，跳过部门解析");
                return List.of();
            case "dingtalk":
                if (dingtalkAdapter != null) {
                    return dingtalkAdapter.fetchUserDepartments(imUserId);
                }
                log.debug("[IM] DingtalkBotAdapter 未配置，跳过部门解析");
                return List.of();
            case "feishu":
                if (feishuHandler != null) {
                    return feishuHandler.fetchUserDepartments(imUserId);
                }
                log.debug("[IM] FeishuStreamBotHandler 未配置，跳过部门解析");
                return List.of();
            default:
                return List.of();
        }
    }
}
