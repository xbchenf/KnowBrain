-- KnowBrain 数据库初始化脚本
-- 数据库: knowbrain (需先手动创建)

-- 文档主表
CREATE TABLE IF NOT EXISTS `kb_document` (
    `id`          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(255)    DEFAULT NULL COMMENT '文档标题',
    `file_name`   VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    `file_type`   VARCHAR(32)     DEFAULT NULL COMMENT '文件类型: pdf / docx / txt / md',
    `file_size`   BIGINT          DEFAULT NULL COMMENT '文件大小(字节)',
    `minio_path`  VARCHAR(512)    DEFAULT NULL COMMENT 'MinIO 存储路径',
    `space_id`    BIGINT          DEFAULT NULL COMMENT '所属空间 ID',
    `uploader_id` BIGINT          DEFAULT NULL COMMENT '上传者用户 ID',
    `parsed_content` MEDIUMTEXT   DEFAULT NULL COMMENT 'Tika 解析后的完整文本',
    `category`    VARCHAR(64)     DEFAULT NULL COMMENT '文档分类',
    `tags`        VARCHAR(255)    DEFAULT NULL COMMENT '标签(逗号分隔)',
    `status`      VARCHAR(16)     DEFAULT 'PARSING' COMMENT '状态: PARSING / READY / FAILED',
    `chunk_count` INT             DEFAULT 0 COMMENT '切片总数',
    `create_time` DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT         DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_space_id` (`space_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档主表';

-- 文档切片表
CREATE TABLE IF NOT EXISTS `kb_document_chunk` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `document_id`  BIGINT       NOT NULL COMMENT '所属文档 ID',
    `chunk_index` INT          NOT NULL DEFAULT 0 COMMENT '切片序号(从0开始)',
    `content`     TEXT         NOT NULL COMMENT '切片文本内容',
    `char_count`  INT          DEFAULT 0 COMMENT '切片字符数',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片表';

-- 知识分类表
CREATE TABLE IF NOT EXISTS `kb_scenario_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '分类名称',
    `key`         VARCHAR(50)  NOT NULL COMMENT '分类唯一标识',
    `parent_key`  VARCHAR(50)  DEFAULT NULL COMMENT '父分类 key',
    `sort_order`  INT          DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分类表';

-- 术语词典表
CREATE TABLE IF NOT EXISTS `kb_scenario_glossary` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `term`        VARCHAR(50)  NOT NULL COMMENT '口语化术语',
    `formal`      VARCHAR(100) NOT NULL COMMENT '正式技术术语',
    `synonyms`    VARCHAR(500) DEFAULT NULL COMMENT '同义词列表(逗号分隔)',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_term` (`term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='术语词典表';

-- 高频问答表
CREATE TABLE IF NOT EXISTS `kb_scenario_faq` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `keywords`    VARCHAR(500) NOT NULL COMMENT '匹配关键词(逗号分隔)',
    `question`    VARCHAR(500) NOT NULL COMMENT '标准问题',
    `answer`      TEXT         NOT NULL COMMENT '预设答案',
    `category`    VARCHAR(50)  DEFAULT NULL COMMENT '所属分类 key',
    `enabled`     TINYINT      DEFAULT 1 COMMENT '启用状态: 0=禁用, 1=启用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高频问答预设表';

-- 系统用户表
CREATE TABLE IF NOT EXISTS `kb_sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)  NOT NULL COMMENT '用户名(登录账号)',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    `name`          VARCHAR(50)  DEFAULT NULL COMMENT '显示名称',
    `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `role`          VARCHAR(16)  DEFAULT 'USER' COMMENT '角色: USER / ADMIN',
    `status`        VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE / DISABLED',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 文档空间表
CREATE TABLE IF NOT EXISTS `kb_space` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`             VARCHAR(100) NOT NULL COMMENT '空间名称',
    `description`      VARCHAR(500) DEFAULT NULL COMMENT '空间描述',
    `owner_id`         BIGINT       NOT NULL COMMENT '创建者用户 ID',
    `visibility`       VARCHAR(16)  DEFAULT 'PRIVATE' COMMENT '可见性: PRIVATE / TEAM / PUBLIC',
    `department_scope` VARCHAR(500) DEFAULT NULL COMMENT '部门可见范围(逗号分隔)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=删除',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档空间表';

-- 空间成员权限表
CREATE TABLE IF NOT EXISTS `kb_space_member` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `space_id`    BIGINT      NOT NULL COMMENT '空间 ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户 ID',
    `role`        VARCHAR(16) NOT NULL DEFAULT 'VIEWER' COMMENT '角色: OWNER / EDITOR / VIEWER',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_user` (`space_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员权限表';

-- 搜索日志表（统计用）
CREATE TABLE IF NOT EXISTS `kb_search_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question`      VARCHAR(500) NOT NULL COMMENT '用户问题原文',
    `answer`        VARCHAR(500) DEFAULT NULL COMMENT 'AI 回答（截取前 500 字）',
    `sources_count` INT          DEFAULT 0 COMMENT '引用来源数',
    `source_titles` VARCHAR(1000) DEFAULT NULL COMMENT '引用文档标题(逗号分隔)',
    `confidence`    VARCHAR(8)   DEFAULT NULL COMMENT '置信度: high/medium/low',
    `faq_matched`   TINYINT      DEFAULT 0 COMMENT '是否 FAQ 命中: 0=否, 1=是',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '提问用户 ID',
    `space_ids`     VARCHAR(200) DEFAULT NULL COMMENT '检索空间范围(逗号分隔)',
    `category`      VARCHAR(64)  DEFAULT NULL COMMENT '分类过滤',
    `cost_ms`       INT          DEFAULT 0 COMMENT '问答总耗时(毫秒)',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索日志表';

-- 答案反馈表
CREATE TABLE IF NOT EXISTS `kb_feedback` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question`    VARCHAR(500) NOT NULL COMMENT '用户问题',
    `answer`      TEXT         NOT NULL COMMENT 'AI 回答',
    `rating`      VARCHAR(8)   NOT NULL COMMENT '评价: useful / useless',
    `comment`     VARCHAR(500) DEFAULT NULL COMMENT '补充意见',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '用户 ID（未登录为空）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_rating` (`rating`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答案反馈表';

-- ==================== 默认管理员 ====================
INSERT IGNORE INTO `kb_sys_user` (`id`, `username`, `password_hash`, `name`, `role`, `status`)
VALUES (1, 'admin', '$2a$10$kLccD0teqN.yXQRw0zdkvufHZaxOaIR2lEP3bG4aQH/AzVsZvyk.6', UNHEX('E7AEA1E79086E59198'), 'ADMIN', 'ACTIVE');
-- 默认密码: KnowBrain@2026
