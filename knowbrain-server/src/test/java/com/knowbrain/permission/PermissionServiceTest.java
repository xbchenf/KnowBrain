package com.knowbrain.permission;

import com.knowbrain.TestMockConfig;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.common.GlobalExceptionHandler.BizException;
import com.knowbrain.space.Space;
import com.knowbrain.space.SpaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 6：空间权限过滤
 *
 * 权限模型：
 *   ADMIN   → 全局读写
 *   MANAGER → 公开空间 + 本部门团队空间
 *   USER    → 按空间可见性 (PUBLIC/TEAM/PRIVATE) 决定
 */
@SpringBootTest
@ActiveProfiles("mock")
@Import(TestMockConfig.class)
@DisplayName("空间权限管理")
class PermissionServiceTest {

    @Autowired
    private SpaceMapper spaceMapper;
    @Autowired
    private SpaceMemberMapper memberMapper;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private PermissionService permissionService;

    private Long adminId, managerId, userAId, userBId;
    private Long dept1Id = 1L, dept2Id = 2L;
    private Long publicSpaceId, teamSpaceId, privateSpaceId;

    @BeforeEach
    void setUp() {
        // 清理
        memberMapper.delete(null);
        spaceMapper.delete(null);
        userMapper.delete(null);

        // 用户
        adminId = createUser("admin", "ADMIN", dept1Id);
        managerId = createUser("manager", "MANAGER", dept1Id);
        userAId = createUser("userA", "USER", dept1Id);
        userBId = createUser("userB", "USER", dept2Id);

        // 空间
        publicSpaceId = createSpace("公共空间", "PUBLIC", adminId, null);
        teamSpaceId = createSpace("部门空间", "TEAM", adminId, "1");   // departmentScope = 部门 1
        privateSpaceId = createSpace("私有空间", "PRIVATE", userAId, null);

        // 私有空间成员
        SpaceMember member = new SpaceMember();
        member.setSpaceId(privateSpaceId);
        member.setUserId(userAId);
        member.setRole("EDITOR");
        memberMapper.insert(member);
    }

    // ==================== 读权限 ====================

    @Nested
    @DisplayName("读权限检查")
    class ReadAccess {

        @Test
        @DisplayName("ADMIN 可读任意空间")
        void adminCanReadAnySpace() {
            assertDoesNotThrow(() -> permissionService.checkReadAccess(publicSpaceId, adminId));
            assertDoesNotThrow(() -> permissionService.checkReadAccess(teamSpaceId, adminId));
            assertDoesNotThrow(() -> permissionService.checkReadAccess(privateSpaceId, adminId));
        }

        @Test
        @DisplayName("所有用户可读 PUBLIC 空间")
        void everyoneCanReadPublicSpace() {
            assertDoesNotThrow(() -> permissionService.checkReadAccess(publicSpaceId, userAId));
            assertDoesNotThrow(() -> permissionService.checkReadAccess(publicSpaceId, userBId));
            assertDoesNotThrow(() -> permissionService.checkReadAccess(publicSpaceId, managerId));
        }

        @Test
        @DisplayName("TEAM 空间：同部门用户可读")
        void teamSpaceReadableBySameDept() {
            assertDoesNotThrow(() -> permissionService.checkReadAccess(teamSpaceId, userAId));
            assertDoesNotThrow(() -> permissionService.checkReadAccess(teamSpaceId, managerId));
        }

        @Test
        @DisplayName("TEAM 空间：不同部门用户被拒绝")
        void teamSpaceDeniedForOtherDept() {
            BizException ex = assertThrows(BizException.class,
                    () -> permissionService.checkReadAccess(teamSpaceId, userBId));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("PRIVATE 空间：非成员被拒绝")
        void privateSpaceDeniedForNonMember() {
            BizException ex = assertThrows(BizException.class,
                    () -> permissionService.checkReadAccess(privateSpaceId, userBId));
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("PRIVATE 空间：Owner 可读")
        void privateSpaceOwnerCanRead() {
            assertDoesNotThrow(() -> permissionService.checkReadAccess(privateSpaceId, userAId));
        }
    }

    // ==================== 写权限 ====================

    @Nested
    @DisplayName("写权限检查")
    class WriteAccess {

        @Test
        @DisplayName("ADMIN 可写任意空间")
        void adminCanWriteAnySpace() {
            assertDoesNotThrow(() -> permissionService.checkWriteAccess(publicSpaceId, adminId));
            assertDoesNotThrow(() -> permissionService.checkWriteAccess(privateSpaceId, adminId));
        }

        @Test
        @DisplayName("MANAGER 可写 PUBLIC 空间")
        void managerCanWritePublicSpace() {
            assertDoesNotThrow(() -> permissionService.checkWriteAccess(publicSpaceId, managerId));
        }

        @Test
        @DisplayName("MANAGER 可写本部门 TEAM 空间")
        void managerCanWriteTeamSpace() {
            assertDoesNotThrow(() -> permissionService.checkWriteAccess(teamSpaceId, managerId));
        }

        @Test
        @DisplayName("普通 USER 无权写空间")
        void userCannotWrite() {
            assertThrows(BizException.class,
                    () -> permissionService.checkWriteAccess(publicSpaceId, userAId));
        }
    }

    // ==================== 检索过滤 ====================

    @Test
    @DisplayName("getAccessibleSpaceIds: ADMIN 返回所有空间")
    void adminSeesAllSpaces() {
        List<Long> ids = permissionService.getAccessibleSpaceIds(adminId);
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of(publicSpaceId, teamSpaceId, privateSpaceId)));
    }

    @Test
    @DisplayName("getAccessibleSpaceIds: userA (部门1) 可看到 PUBLIC + TEAM(部门1) + 自己的 PRIVATE")
    void userASeesOwnAndPublicAndTeam() {
        List<Long> ids = permissionService.getAccessibleSpaceIds(userAId);
        assertTrue(ids.contains(publicSpaceId));
        assertTrue(ids.contains(teamSpaceId));
        assertTrue(ids.contains(privateSpaceId));
        assertEquals(3, ids.size());  // userA is owner of privateSpace
    }

    @Test
    @DisplayName("getAccessibleSpaceIds: userB (部门2) 只能看到 PUBLIC 空间")
    void userBSeesOnlyPublic() {
        List<Long> ids = permissionService.getAccessibleSpaceIds(userBId);
        assertTrue(ids.contains(publicSpaceId));
        assertFalse(ids.contains(teamSpaceId));
        assertFalse(ids.contains(privateSpaceId));
        assertEquals(1, ids.size());
    }

    // ==================== 辅助方法 ====================

    private Long createUser(String username, String role, Long deptId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setName(username);
        user.setRole(role);
        user.setDepartmentId(deptId);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user.getId();
    }

    private Long createSpace(String name, String visibility, Long ownerId, String deptScope) {
        Space space = new Space();
        space.setName(name);
        space.setVisibility(visibility);
        space.setOwnerId(ownerId);
        space.setDepartmentScope(deptScope);
        spaceMapper.insert(space);
        return space.getId();
    }
}
