-- KnowBrain IM 用户身份关联表 (Flyway V5)
-- 用途: 将企微/钉钉/飞书的用户身份关联到 KB 用户，支持跨平台身份统一
-- 依赖: V1__init_schema.sql (kb_sys_user 表)

CREATE TABLE IF NOT EXISTS `kb_user_identity` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `kb_user_id`    BIGINT       NOT NULL COMMENT 'KB用户ID (kb_sys_user.id)',
    `platform`      VARCHAR(20)  NOT NULL COMMENT 'IM平台: wecom / dingtalk / feishu',
    `platform_uid`  VARCHAR(128) NOT NULL COMMENT '平台侧用户唯一标识',
    `platform_name` VARCHAR(64)  COMMENT '平台侧显示名',
    `mobile`        VARCHAR(20)  COMMENT '手机号（首次匹配后可更新，用于跨平台身份匹配）',
    `linked_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_uid` (`platform`, `platform_uid`),
    KEY `idx_kb_user_id` (`kb_user_id`),
    KEY `idx_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM平台用户身份→KB用户关联表';

-- 回填存量: 将现有 im_{platform}_{userId} 命名的用户迁入关联表
-- 解析规则: username 格式为 "im_{platform}_{userId}"
--   platform   = 第2段（im 和 userId 之间的部分）
--   platform_uid = 剩余部分
INSERT INTO kb_user_identity (kb_user_id, platform, platform_uid, platform_name)
SELECT
    su.id,
    SUBSTRING_INDEX(SUBSTRING_INDEX(su.username, '_', 2), '_', -1) AS platform,
    SUBSTRING(su.username,
              CHAR_LENGTH('im_')
                  + CHAR_LENGTH(SUBSTRING_INDEX(SUBSTRING_INDEX(su.username, '_', 2), '_', -1))
                  + 2) AS platform_uid,
    su.name
FROM kb_sys_user su
WHERE su.username LIKE 'im\_%'
  AND su.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM kb_user_identity ui WHERE ui.kb_user_id = su.id
  );
