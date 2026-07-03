package com.knowbrain.document.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片向量表 — kb_document_chunk
 */
@Data
@TableName("kb_document_chunk")
public class EkDocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID */
    private Long documentId;

    /** 切片序号（从 0 开始） */
    private Integer chunkIndex;

    /** 切片文本内容 */
    private String content;

    /** 切片字符数 */
    private Integer charCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
