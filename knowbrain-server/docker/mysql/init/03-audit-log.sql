-- KnowBrain 审计日志表
-- 记录管理员所有写操作：谁 + 什么时间 + 做了什么 + 目标资源 + 结果 + IP
-- 依赖: 01-init-schema.sql 已执行

CREATE TABLE IF NOT EXISTS `kb_audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_id`     BIGINT       NOT NULL COMMENT '操作人用户ID',
    `operator_name`   VARCHAR(50)  DEFAULT NULL COMMENT '操作人用户名',
    `operation_type`  VARCHAR(20)  NOT NULL COMMENT '操作类型: CREATE / UPDATE / DELETE / OTHER',
    `resource_type`   VARCHAR(50)  NOT NULL COMMENT '目标资源类型: USER / DEPARTMENT / SPACE / DOCUMENT / SCENARIO_CATEGORY / SCENARIO_GLOSSARY / SCENARIO_FAQ / AUTH',
    `resource_id`     BIGINT       DEFAULT NULL COMMENT '目标资源ID',
    `resource_name`   VARCHAR(255) DEFAULT NULL COMMENT '目标资源名称/摘要',
    `description`     VARCHAR(500) DEFAULT NULL COMMENT '操作详情描述',
    `ip_address`      VARCHAR(45)  DEFAULT NULL COMMENT '客户端IP地址',
    `success`         TINYINT      DEFAULT 1 COMMENT '操作结果: 1=成功, 0=失败',
    `error_message`   VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_resource_type` (`resource_type`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_resource_type_id` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
