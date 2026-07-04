package com.knowbrain.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 空间管理服务
 */
public interface SpaceService {

    /** 创建空间 */
    Space create(String name, String description, Long ownerId, String visibility, String departmentScope);

    /** 查询空间详情 */
    Space getById(Long id);

    /** 分页查询用户可访问的空间 */
    Page<Space> listByUser(Long userId, int page, int size);

    /** 更新空间信息 */
    Space update(Long id, String name, String description, String visibility, String departmentScope);

    /** 删除空间（逻辑删除） */
    void delete(Long id);
}
