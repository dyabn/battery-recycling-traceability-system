-- I5 data-quality discovery increment.
-- Backfill enterprise rule configs for existing enterprises without changing V1-V6.
-- Existing configs are preserved, including DISABLED configs.

INSERT INTO dq_enterprise_rule_config (
  enterprise_id, rule_code, enabled_status, toggled_reason, toggled_by, toggled_at,
  created_at, updated_at, version
)
SELECT e.id, r.rule_code, 'ENABLED', NULL, NULL, NULL,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM enterprise e
CROSS JOIN dq_rule_definition r
WHERE r.rule_code IN ('DQ-001','DQ-002','DQ-003','DQ-004','DQ-005','DQ-006','DQ-007')
  AND NOT EXISTS (
    SELECT 1
    FROM dq_enterprise_rule_config c
    WHERE c.enterprise_id = e.id
      AND c.rule_code = r.rule_code
  );
