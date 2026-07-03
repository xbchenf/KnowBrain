package com.knowbrain.document.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档主表 — kb_document
 */
@Data
@TableName("kb_document")
public class EkDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    private String title;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：pdf / docx / txt / md */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MinIO 存储路径（对象 Key） */
    private String minioPath;

    /** 所属空间 ID */
    private Long spaceId;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** Tika 解析后的完整文本 */
    private String parsedContent;

    /** 文档分类 */
    private String category;

    /** 标签（逗号分隔） */
    private String tags;

    /** 文档状态：PARSING / READY / FAILED */
    private String status;

    /** 切片总数（阶段 3 填充） */
    private Integer chunkCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
