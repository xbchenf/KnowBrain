package com.knowbrain.document.service;

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
     * 删除文档（逻辑删除）
     */
    void delete(Long id);

    /**
     * 对已解析的文档重新切片 + 向量化
     */
    void chunkAndVectorize(Long documentId);
}
