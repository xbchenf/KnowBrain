package com.knowbrain.space;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.auth.RoleEnum;
import com.knowbrain.auth.SysUser;
import com.knowbrain.auth.SysUserMapper;
import com.knowbrain.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 空间管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;
    private final PermissionService permissionService;
    private final SysUserMapper userMapper;

    @Override
    public Space create(String name, String description, Long ownerId, String visibility, String departmentScope) {
        Space space = new Space();
        space.setName(name);
        space.setDescription(description);
        space.setOwnerId(ownerId);
        space.setVisibility(visibility != null ? visibility : "PRIVATE");
        space.setDepartmentScope(departmentScope);
        spaceMapper.insert(space);
        log.info("空间创建: id={}, name={}, ownerId={}, visibility={}", space.getId(), name, ownerId, visibility);
        return space;
    }

    @Override
    public Space getById(Long id) {
        Space space = spaceMapper.selectById(id);
        if (space != null) {
            fillOwnerNames(List.of(space));
        }
        return space;
    }

    @Override
    public Page<Space> listByUser(Long userId, int page, int size) {
        // 通过权限服务获取用户可读的空间 ID 列表
        List<Long> accessibleIds = permissionService.getAccessibleSpaceIds(userId);
        if (accessibleIds.isEmpty()) {
            return new Page<>(page, size, 0);
        }

        LambdaQueryWrapper<Space> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Space::getId, accessibleIds)
               .orderByDesc(Space::getCreateTime);
        Page<Space> result = spaceMapper.selectPage(new Page<>(page, size), wrapper);
        fillOwnerNames(result.getRecords());

        // 填充 isOwner：ADMIN 对所有空间为 owner，普通用户仅对自己的空间
        SysUser currentUser = userMapper.selectById(userId);
        boolean isAdmin = currentUser != null && RoleEnum.ADMIN.matches(currentUser.getRole());
        for (Space s : result.getRecords()) {
            s.setIsOwner(isAdmin || s.getOwnerId().equals(userId));
        }

        return result;
    }

    /** 批量填充空间的创建者姓名 */
    private void fillOwnerNames(List<Space> spaces) {
        if (spaces.isEmpty()) return;
        List<Long> ownerIds = spaces.stream()
                .map(Space::getOwnerId)
                .distinct()
                .toList();
        Map<Long, String> nameMap = userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getName, (a, b) -> a));
        for (Space s : spaces) {
            s.setOwnerName(nameMap.getOrDefault(s.getOwnerId(), "未知用户"));
        }
    }

    @Override
    public Space update(Long id, String name, String description, String visibility, String departmentScope) {
        Space space = spaceMapper.selectById(id);
        if (space == null) return null;
        if (name != null) space.setName(name);
        if (description != null) space.setDescription(description);
        if (visibility != null) space.setVisibility(visibility);
        if (departmentScope != null) space.setDepartmentScope(departmentScope);
        spaceMapper.updateById(space);
        log.info("空间更新: id={}", id);
        return space;
    }

    @Override
    public void delete(Long id) {
        spaceMapper.deleteById(id);
        log.info("空间删除: id={}", id);
    }
}
