package com.knowbrain.im;

import com.knowbrain.im.adapter.WecomBotAdapter;
import com.knowbrain.im.entity.ImDeptMapping;
import com.knowbrain.im.mapper.ImDeptMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 部门解析器单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IM 部门解析器")
class ImDeptResolverTest {

    @Mock
    private ImDeptMappingMapper mappingMapper;

    @Mock
    private WecomBotAdapter wecomAdapter;

    @InjectMocks
    private ImDeptResolver deptResolver;

    @BeforeEach
    void setUp() {
        // @InjectMocks 不会注入 @Autowired(required=false) 字段，需手动反射注入
        setField(deptResolver, "wecomAdapter", wecomAdapter);
    }

    // ==================== 映射表匹配 ====================

    @Test
    @DisplayName("映射命中：API 返回部门 → 查映射表 → 返回 KB 部门 ID")
    void resolveKbDeptIdHit() {
        // 模拟企微 API 返回部门 [1, 2]
        when(wecomAdapter.fetchUserDepartments("test-user"))
                .thenReturn(List.of("1", "2"));

        // 映射表: wecom+2 → kb_dept_8
        ImDeptMapping mapping = new ImDeptMapping();
        mapping.setKbDeptId(8L);
        when(mappingMapper.selectOne(any()))
                .thenReturn(null)       // 第一次查 dept_1 → 无映射
                .thenReturn(mapping);    // 第二次查 dept_2 → 命中!

        Long result = deptResolver.resolveKbDeptId("wecom", "test-user");
        assertEquals(8L, result);
    }

    @Test
    @DisplayName("映射未命中：API 返回部门但无映射 → 返回 null")
    void resolveKbDeptIdMiss() {
        when(wecomAdapter.fetchUserDepartments("test-user"))
                .thenReturn(List.of("99"));

        when(mappingMapper.selectOne(any())).thenReturn(null);

        assertNull(deptResolver.resolveKbDeptId("wecom", "test-user"));
    }

    @Test
    @DisplayName("API 返回空列表 → 返回 null")
    void resolveKbDeptIdEmptyDeptList() {
        when(wecomAdapter.fetchUserDepartments("test-user"))
                .thenReturn(List.of());

        assertNull(deptResolver.resolveKbDeptId("wecom", "test-user"));
    }

    @Test
    @DisplayName("非企微平台返回 null（兜底）")
    void resolveKbDeptIdUnknownPlatform() {
        assertNull(deptResolver.resolveKbDeptId("unknown-platform", "test-user"));
    }

    @Test
    @DisplayName("钉钉平台当前返回 null（未实现）")
    void resolveKbDeptIdDingtalk() {
        assertNull(deptResolver.resolveKbDeptId("dingtalk", "test-user"));
    }

    @Test
    @DisplayName("飞书平台当前返回 null（未实现）")
    void resolveKbDeptIdFeishu() {
        assertNull(deptResolver.resolveKbDeptId("feishu", "test-user"));
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("空 userId 不抛异常")
    void resolveKbDeptIdEmptyUserId() {
        assertNull(deptResolver.resolveKbDeptId("wecom", ""));
    }

    @Test
    @DisplayName("null userId 不抛异常")
    void resolveKbDeptIdNullUserId() {
        assertNull(deptResolver.resolveKbDeptId("wecom", null));
    }

    @Test
    @DisplayName("空平台名不抛异常")
    void resolveKbDeptIdEmptyPlatform() {
        assertNull(deptResolver.resolveKbDeptId("", "test-user"));
    }

    @Test
    @DisplayName("API 抛异常时降级返回 null")
    void resolveKbDeptIdApiException() {
        when(wecomAdapter.fetchUserDepartments("test-user"))
                .thenThrow(new RuntimeException("网络超时"));

        assertNull(deptResolver.resolveKbDeptId("wecom", "test-user"));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }
}
