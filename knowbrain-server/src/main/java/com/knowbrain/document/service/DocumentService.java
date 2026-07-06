package com.knowbrain.document.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.document.entity.EkDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理服务 — 上传、解析、切片、向量化
 */
public interface DocumentService {

    /**
     * 上传文档（MinIO 存储 + Tika 解析 + 切片 + 向量化）
     */
    EkDocument upload(MultipartFile file, Long spaceId, Long uploaderId, String category);

    /**
     * 根据 ID 查询文档
     */
    EkDocument getById(Long id);

    /**
     * 分页查询文档列表（按空间过滤，按创建时间倒序）
     */
    Page<EkDocument> listBySpace(Long spaceId, int page, int size);

    /**
     * 删除文档（逻辑删除）
     */
    void delete(Long id);

    /**
     * 更新文档元数据（标题、分类）
     */
    void updateMeta(Long id, String title, String category);

    /**
     * 对已解析的文档重新切片 + 向量化
     */
    void chunkAndVectorize(Long documentId);
}
