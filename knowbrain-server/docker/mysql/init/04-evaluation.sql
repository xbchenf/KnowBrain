-- KnowBrain RAG 评测体系 (Docker Init 04)
-- 用途: Docker 首次部署时建表，与 Flyway V6__evaluation.sql 保持一致
-- 注意: 如果启用 Flyway，此文件不会执行（Flyway 优先于 Docker init）

-- 1. 评测数据集
CREATE TABLE IF NOT EXISTS `kb_evaluation_dataset` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(100) NOT NULL COMMENT '数据集名称',
    `description`    VARCHAR(500) COMMENT '描述说明',
    `scenario`       VARCHAR(50)  COMMENT '关联场景: it-helpdesk / hr-policy / general',
    `question_count` INT          NOT NULL DEFAULT 0 COMMENT '问题数量（冗余计数）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenario` (`scenario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG评测数据集';

-- 2. 评测问题
CREATE TABLE IF NOT EXISTS `kb_evaluation_question` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dataset_id`       BIGINT       NOT NULL COMMENT '所属数据集 ID',
    `question`         TEXT         NOT NULL COMMENT '测试问题',
    `expected_answer`  TEXT         COMMENT '预期答案（可选，人工标注的参考答案）',
    `expected_doc_ids` VARCHAR(500) COMMENT '预期命中文档 ID（可选，逗号分隔）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG评测问题';

-- 3. 评测运行记录
CREATE TABLE IF NOT EXISTS `kb_evaluation_run` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dataset_id`          BIGINT       NOT NULL COMMENT '所用数据集 ID',
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING / COMPLETED / FAILED',
    `total_questions`     INT          NOT NULL DEFAULT 0 COMMENT '总问题数',
    `completed_questions` INT          NOT NULL DEFAULT 0 COMMENT '已完成数',
    `avg_faithfulness`    DECIMAL(4,3) COMMENT '平均忠实度 0.000-1.000',
    `avg_relevance`       DECIMAL(4,3) COMMENT '平均答案相关性 0.000-1.000',
    `avg_context_recall`  DECIMAL(4,3) COMMENT '平均上下文召回率 0.000-1.000',
    `avg_latency_ms`      INT          COMMENT '平均延迟（毫秒）',
    `started_at`          DATETIME     COMMENT '开始时间',
    `completed_at`        DATETIME     COMMENT '完成时间',
    `error_message`       TEXT         COMMENT '失败原因',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_dataset_id` (`dataset_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG评测运行记录';

-- 4. 单题评测结果
CREATE TABLE IF NOT EXISTS `kb_evaluation_result` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`           BIGINT       NOT NULL COMMENT '所属运行 ID',
    `question_id`      BIGINT       NOT NULL COMMENT '对应问题 ID',
    `question_text`    TEXT         COMMENT '问题文本（冗余存储，避免 JOIN）',
    `actual_answer`    TEXT         COMMENT 'RAG 实际返回的答案',
    `retrieved_chunks` TEXT         COMMENT '检索到的文档片段（JSON 数组）',
    `faithfulness`     DECIMAL(4,3) COMMENT '忠实度 0.000-1.000',
    `answer_relevance` DECIMAL(4,3) COMMENT '答案相关性 0.000-1.000',
    `context_recall`   DECIMAL(4,3) COMMENT '上下文召回率 0.000-1.000',
    `latency_ms`       INT          COMMENT '本次查询延迟（毫秒）',
    `llm_eval_raw`     TEXT         COMMENT 'LLM 评测原始输出（JSON，调试用）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_run_id` (`run_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG单题评测结果';
