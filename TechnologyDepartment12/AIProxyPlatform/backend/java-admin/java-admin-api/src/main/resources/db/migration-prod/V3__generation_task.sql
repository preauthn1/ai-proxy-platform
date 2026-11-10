-- Flyway V3: generation_task 表（图片生成任务；对齐 spec §3.1 / Python GenerationTask 关键字段）

CREATE TABLE generation_task (
    id             VARCHAR(64)   NOT NULL COMMENT 'job_id (uuid)',
    task_type      VARCHAR(64)   NOT NULL DEFAULT 'external_image_generate',
    business_type  VARCHAR(64)   NOT NULL DEFAULT 'external_image',
    business_id    VARCHAR(64)   NULL,
    queue_name     VARCHAR(64)   NULL,
    status         VARCHAR(32)   NOT NULL DEFAULT 'processing', -- processing|succeeded|failed
    payload_json   JSON          NULL,
    result_json    JSON          NULL,
    metadata_json  JSON          NULL,
    error_code     VARCHAR(64)   NULL,
    error_message  VARCHAR(512)  NULL,
    max_attempts   INT           NULL,
    attempt_count  INT           NOT NULL DEFAULT 0,
    deleted_at     BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NULL,
    created_by     BIGINT        NOT NULL DEFAULT 0,
    updated_by     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_gt_business (business_type, business_id),
    KEY idx_gt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
