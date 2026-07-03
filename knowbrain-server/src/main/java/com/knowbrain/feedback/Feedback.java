package com.knowbrain.feedback;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答案反馈 — kb_feedback
 */
@Data
@TableName("kb_feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户问题 */
    private String question;

    /** AI 回答 */
    private String answer;

    /** 评价: useful / useless */
    private String rating;

    /** 补充意见 */
    private String comment;

    /** 用户 ID（未登录为空） */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
