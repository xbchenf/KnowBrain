package com.knowbrain.space;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 空间管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;

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
        return spaceMapper.selectById(id);
    }

    @Override
    public Page<Space> listByUser(Long userId, int page, int size) {
        // 用户可访问的空间：自己创建的 + PUBLIC 空间
        // 注：TEAM 权限过滤在阶段 2-3 完善
        LambdaQueryWrapper<Space> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Space::getOwnerId, userId)
               .or()
               .eq(Space::getVisibility, "PUBLIC")
               .orderByDesc(Space::getCreateTime);
        return spaceMapper.selectPage(new Page<>(page, size), wrapper);
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
