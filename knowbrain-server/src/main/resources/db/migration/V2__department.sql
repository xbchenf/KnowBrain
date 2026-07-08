-- KnowBrain 部门体系 (Flyway V2)
-- 依赖: V1__init_schema.sql

-- 部门表
CREATE TABLE IF NOT EXISTS `kb_department` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '部门名称',
    `parent_id`   BIGINT       DEFAULT 0 COMMENT '上级部门 ID（0=顶级部门）',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 种子数据：预置部门
INSERT IGNORE INTO `kb_department` (`id`, `name`, `parent_id`, `sort_order`) VALUES
    (1, '技术部',   0, 1),
    (2, '产品部',   0, 2),
    (3, '人力资源部', 0, 3),
    (4, '财务部',   0, 4),
    (5, '市场部',   0, 5),
    (6, '运营部',   0, 6),
    (7, '行政部',   0, 7);

-- ADMIN 账号归属技术部
UPDATE `kb_sys_user` SET `department_id` = 1 WHERE `username` = 'admin' AND `department_id` IS NULL;
