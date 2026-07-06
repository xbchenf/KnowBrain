-- ============================================================
-- H2 测试数据库表结构（与生产 MySQL 对齐）
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS kb_sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(128),
    phone VARCHAR(32),
    role VARCHAR(32) DEFAULT 'USER',
    department_id BIGINT,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 空间表
CREATE TABLE IF NOT EXISTS kb_space (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    visibility VARCHAR(32) DEFAULT 'PRIVATE',
    owner_id BIGINT NOT NULL,
    department_scope VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 空间成员表
CREATE TABLE IF NOT EXISTS kb_space_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) DEFAULT 'VIEWER',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(512),
    file_name VARCHAR(512),
    file_type VARCHAR(32),
    file_size BIGINT,
    minio_path VARCHAR(512),
    space_id BIGINT,
    uploader_id BIGINT,
    parsed_content CLOB,
    category VARCHAR(128),
    tags VARCHAR(512),
    status VARCHAR(32) DEFAULT 'PARSING',
    chunk_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 文档切片表
CREATE TABLE IF NOT EXISTS kb_document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content CLOB,
    char_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 场景分类表
CREATE TABLE IF NOT EXISTS kb_scenario_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    `key` VARCHAR(64) NOT NULL,
    parent_key VARCHAR(64),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 场景术语词典表
CREATE TABLE IF NOT EXISTS kb_scenario_glossary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    term VARCHAR(128) NOT NULL,
    formal VARCHAR(128) NOT NULL,
    synonyms VARCHAR(512),
    category VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 场景 FAQ 表
CREATE TABLE IF NOT EXISTS kb_scenario_faq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(500) NOT NULL,
    answer CLOB NOT NULL,
    keywords VARCHAR(500),
    category VARCHAR(64),
    enabled INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 反馈表
CREATE TABLE IF NOT EXISTS kb_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(500),
    answer CLOB,
    rating VARCHAR(16),
    comment VARCHAR(500),
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 搜索日志表
CREATE TABLE IF NOT EXISTS kb_search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(500),
    answer VARCHAR(500),
    sources_count INT DEFAULT 0,
    source_titles VARCHAR(1000),
    confidence VARCHAR(8),
    faq_matched INT DEFAULT 0,
    user_id BIGINT,
    space_ids VARCHAR(200),
    category VARCHAR(64),
    cost_ms INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 部门表
CREATE TABLE IF NOT EXISTS kb_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);
