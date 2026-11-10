-- Flyway V4: temporal_task_execution.failure_reason 改为 TEXT，不再截断失败原因

ALTER TABLE temporal_task_execution
    MODIFY COLUMN failure_reason TEXT DEFAULT NULL COMMENT '失败原因全文(不截断)';
