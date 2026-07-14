-- KnowBrain IM 部门映射表 (Flyway V4)
-- 用途: 将企微/钉钉/飞书的部门ID 映射到 KB 部门ID
-- 依赖: V2__department.sql (kb_department 表)

CREATE TABLE IF NOT EXISTS `kb_im_dept_mapping` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `platform`         VARCHAR(20)  NOT NULL COMMENT 'IM平台: wecom / dingtalk / feishu',
    `external_dept_id` VARCHAR(100) NOT NULL COMMENT 'IM平台侧部门ID',
    `kb_dept_id`       BIGINT       NOT NULL COMMENT '对应 KB 部门 ID (kb_department.id)',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_dept` (`platform`, `external_dept_id`),
    KEY `idx_kb_dept_id` (`kb_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM平台部门→KB部门映射表';
