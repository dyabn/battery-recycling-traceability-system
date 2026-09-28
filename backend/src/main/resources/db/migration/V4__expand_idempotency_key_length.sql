-- I1 复核整改：与 OpenAPI 契约统一，将 Idempotency-Key 支持长度扩展到 128。

ALTER TABLE idempotency_record
  MODIFY idempotency_key VARCHAR(128) NOT NULL;
