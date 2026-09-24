-- Data Governance V1.1 incremental schema design for MySQL 8.4.
-- Design artifact only. Do not execute as a production migration before review approval.

CREATE TABLE IF NOT EXISTS dq_rule_definition (
  id BIGINT NOT NULL AUTO_INCREMENT,
  rule_code VARCHAR(32) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  dimension VARCHAR(32) NOT NULL,
  object_type VARCHAR(64) NOT NULL,
  handler_code VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  default_owner_role_code VARCHAR(64) NOT NULL,
  data_standard_metadata JSON NOT NULL,
  involved_fields JSON NOT NULL,
  check_condition TEXT NOT NULL,
  remediation_guidance TEXT NOT NULL,
  allow_exemption TINYINT NOT NULL DEFAULT 0,
  rule_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dq_rule_code (rule_code),
  CONSTRAINT ck_dq_rule_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT ck_dq_rule_owner_role CHECK (default_owner_role_code IN ('BUSINESS_SUPERVISOR', 'RECYCLE_OPERATOR', 'WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')),
  CONSTRAINT ck_dq_rule_exemption CHECK (allow_exemption = 0),
  CONSTRAINT ck_dq_rule_status CHECK (rule_status IN ('ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_enterprise_rule_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  enabled_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  toggled_reason VARCHAR(512) NULL,
  toggled_by BIGINT NULL,
  toggled_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dq_enterprise_rule (enterprise_id, rule_code),
  KEY idx_dq_rule_config_status (enterprise_id, enabled_status),
  CONSTRAINT fk_dq_rule_config_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_rule_config_rule FOREIGN KEY (rule_code) REFERENCES dq_rule_definition(rule_code),
  CONSTRAINT fk_dq_rule_config_user FOREIGN KEY (toggled_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dq_rule_config_status CHECK (enabled_status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_check_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  run_no VARCHAR(40) NOT NULL,
  run_scope VARCHAR(32) NOT NULL,
  run_status VARCHAR(16) NOT NULL,
  requested_rule_codes JSON NULL,
  executed_rule_codes JSON NOT NULL,
  checked_object_count INT NOT NULL DEFAULT 0,
  checked_rule_count INT NOT NULL DEFAULT 0,
  issue_count INT NOT NULL DEFAULT 0,
  result_summary JSON NULL,
  failure_reason VARCHAR(512) NULL,
  started_by BIGINT NOT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  trace_id VARCHAR(64) NULL,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dq_check_run_no (run_no),
  KEY idx_dq_check_run_enterprise_time (enterprise_id, started_at),
  KEY idx_dq_check_run_status (enterprise_id, run_status),
  CONSTRAINT fk_dq_check_run_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_check_run_started_by FOREIGN KEY (started_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dq_check_run_scope CHECK (run_scope IN ('ALL', 'BATTERY', 'INBOUND', 'INVENTORY', 'TRACE')),
  CONSTRAINT ck_dq_check_run_status CHECK (run_status IN ('RUNNING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_check_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  check_status VARCHAR(16) NOT NULL,
  checked_object_count INT NOT NULL DEFAULT 0,
  violation_count INT NOT NULL DEFAULT 0,
  rule_snapshot JSON NOT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  error_message VARCHAR(512) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dq_result_run_rule (run_id, rule_code),
  KEY idx_dq_result_enterprise_run (enterprise_id, run_id),
  KEY idx_dq_result_rule_status (enterprise_id, rule_code, check_status),
  CONSTRAINT fk_dq_result_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_result_run FOREIGN KEY (run_id) REFERENCES dq_check_run(id),
  CONSTRAINT fk_dq_result_rule FOREIGN KEY (rule_code) REFERENCES dq_rule_definition(rule_code),
  CONSTRAINT ck_dq_result_status CHECK (check_status IN ('PASS', 'VIOLATION', 'SKIPPED', 'ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_issue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  issue_no VARCHAR(40) NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  object_type VARCHAR(64) NOT NULL,
  object_id BIGINT NULL,
  object_identity VARCHAR(128) NOT NULL,
  object_key VARCHAR(128) NOT NULL,
  title VARCHAR(128) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  owner_role_code VARCHAR(64) NOT NULL,
  issue_status VARCHAR(16) NOT NULL,
  assigned_to_user_id BIGINT NULL,
  assigned_by_user_id BIGINT NULL,
  assigned_at DATETIME(3) NULL,
  discovered_run_id BIGINT NOT NULL,
  discovered_result_id BIGINT NOT NULL,
  first_detected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_detected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  closed_at DATETIME(3) NULL,
  open_issue_key VARCHAR(255) GENERATED ALWAYS AS (
    CASE
      WHEN issue_status <> 'CLOSED' THEN CONCAT(rule_code, '#', object_type, '#', LOWER(TRIM(object_identity)))
      ELSE NULL
    END
  ) STORED,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dq_issue_no (issue_no),
  UNIQUE KEY uk_dq_issue_open (enterprise_id, open_issue_key),
  KEY idx_dq_issue_list (enterprise_id, issue_status, severity, last_detected_at),
  KEY idx_dq_issue_rule_object (enterprise_id, rule_code, object_type, object_identity),
  KEY idx_dq_issue_assignee (enterprise_id, assigned_to_user_id, issue_status),
  CONSTRAINT fk_dq_issue_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_issue_rule FOREIGN KEY (rule_code) REFERENCES dq_rule_definition(rule_code),
  CONSTRAINT fk_dq_issue_assigned_to FOREIGN KEY (assigned_to_user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_dq_issue_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_dq_issue_run FOREIGN KEY (discovered_run_id) REFERENCES dq_check_run(id),
  CONSTRAINT fk_dq_issue_result FOREIGN KEY (discovered_result_id) REFERENCES dq_check_result(id),
  CONSTRAINT ck_dq_issue_severity CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
  CONSTRAINT ck_dq_issue_owner_role CHECK (owner_role_code IN ('BUSINESS_SUPERVISOR', 'RECYCLE_OPERATOR', 'WAREHOUSE_ADMIN', 'SYSTEM_ADMIN')),
  CONSTRAINT ck_dq_issue_status CHECK (issue_status IN ('OPEN', 'ASSIGNED', 'PROCESSING', 'SUBMITTED', 'RECHECKING', 'REJECTED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_remediation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  issue_id BIGINT NOT NULL,
  remediation_note VARCHAR(1000) NOT NULL,
  evidence_attachment_id BIGINT NULL,
  correction_object_type VARCHAR(64) NULL,
  correction_object_id BIGINT NULL,
  remediation_status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  submitted_by BIGINT NOT NULL,
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dq_remediation_issue (enterprise_id, issue_id, submitted_at),
  CONSTRAINT fk_dq_remediation_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_remediation_issue FOREIGN KEY (issue_id) REFERENCES dq_issue(id),
  CONSTRAINT fk_dq_remediation_attachment FOREIGN KEY (evidence_attachment_id) REFERENCES business_attachment(id),
  CONSTRAINT fk_dq_remediation_user FOREIGN KEY (submitted_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dq_remediation_status CHECK (remediation_status IN ('SUBMITTED')),
  CONSTRAINT ck_dq_remediation_evidence_required CHECK (
    evidence_attachment_id IS NOT NULL
    OR (correction_object_type IS NOT NULL AND correction_object_id IS NOT NULL)
  ),
  CONSTRAINT ck_dq_remediation_correction_pair CHECK (
    (correction_object_type IS NULL AND correction_object_id IS NULL)
    OR (correction_object_type IS NOT NULL AND correction_object_id IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_recheck (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  issue_id BIGINT NOT NULL,
  recheck_status VARCHAR(16) NOT NULL,
  linked_check_run_id BIGINT NOT NULL,
  linked_check_result_id BIGINT NOT NULL,
  started_by BIGINT NOT NULL,
  completed_by BIGINT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  result VARCHAR(16) NULL,
  result_reason VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_dq_recheck_issue (enterprise_id, issue_id, started_at),
  CONSTRAINT fk_dq_recheck_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_recheck_issue FOREIGN KEY (issue_id) REFERENCES dq_issue(id),
  CONSTRAINT fk_dq_recheck_run FOREIGN KEY (linked_check_run_id) REFERENCES dq_check_run(id),
  CONSTRAINT fk_dq_recheck_result FOREIGN KEY (linked_check_result_id) REFERENCES dq_check_result(id),
  CONSTRAINT fk_dq_recheck_started_by FOREIGN KEY (started_by) REFERENCES sys_user(id),
  CONSTRAINT fk_dq_recheck_completed_by FOREIGN KEY (completed_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dq_recheck_status CHECK (recheck_status IN ('RUNNING', 'PASSED', 'FAILED')),
  CONSTRAINT ck_dq_recheck_result CHECK (result IS NULL OR result IN ('PASSED', 'FAILED')),
  CONSTRAINT ck_dq_recheck_completion CHECK (
    (
      recheck_status = 'RUNNING'
      AND result IS NULL
      AND completed_by IS NULL
      AND completed_at IS NULL
    )
    OR
    (
      recheck_status = 'PASSED'
      AND result = 'PASSED'
      AND completed_by IS NOT NULL
      AND completed_at IS NOT NULL
    )
    OR
    (
      recheck_status = 'FAILED'
      AND result = 'FAILED'
      AND completed_by IS NOT NULL
      AND completed_at IS NOT NULL
      AND result_reason IS NOT NULL
    )
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dq_operation_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id BIGINT NOT NULL,
  action_code VARCHAR(80) NOT NULL,
  object_type VARCHAR(64) NOT NULL,
  object_id BIGINT NULL,
  before_state VARCHAR(64) NULL,
  after_state VARCHAR(64) NULL,
  result VARCHAR(20) NOT NULL,
  reason VARCHAR(512) NULL,
  operated_by BIGINT NULL,
  operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  trace_id VARCHAR(64) NULL,
  idempotency_key_hash VARCHAR(128) NULL,
  PRIMARY KEY (id),
  KEY idx_dq_audit_enterprise_time (enterprise_id, operated_at),
  KEY idx_dq_audit_object (enterprise_id, object_type, object_id),
  KEY idx_dq_audit_action_result (enterprise_id, action_code, result),
  CONSTRAINT fk_dq_audit_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dq_audit_user FOREIGN KEY (operated_by) REFERENCES sys_user(id),
  CONSTRAINT ck_dq_audit_result CHECK (result IN ('SUCCESS', 'FAILED', 'FORBIDDEN', 'CONFLICT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO dq_rule_definition (
  rule_code, rule_name, dimension, object_type, handler_code, severity,
  default_owner_role_code, data_standard_metadata, involved_fields, check_condition,
  remediation_guidance, allow_exemption, rule_status
) VALUES
('DQ-001', '系统追溯编码必须全系统唯一', '唯一性', 'battery', 'TraceCodeUniqueRule', 'HIGH', 'BUSINESS_SUPERVISOR',
 JSON_OBJECT('standard', 'system_trace_code global unique'), JSON_ARRAY('battery.system_trace_code'),
 'battery.system_trace_code must be globally unique; cross-enterprise conflicts hide unauthorized object details.',
 '核对追溯编码来源，关联业务更正记录后重新检查。', 0, 'ACTIVE'),
('DQ-002', '电池核心字段必须完整', '完整性', 'battery', 'BatteryRequiredFieldRule', 'MEDIUM', 'RECYCLE_OPERATOR',
 JSON_OBJECT('standard', 'required battery fields'), JSON_ARRAY('battery.current_responsible_enterprise_id', 'battery.battery_type', 'battery.battery_chemistry', 'battery.lifecycle_status'),
 'Required battery fields must not be null; battery_chemistry may be UNKNOWN when selected by standard.',
 '通过业务更正补齐字段；允许按数据标准填写未知，不得仅凭说明关闭。', 0, 'ACTIVE'),
('DQ-003', '原始编码重复必须完成核实', '一致性', 'battery_registration_candidate', 'DuplicateReviewRule', 'HIGH', 'RECYCLE_OPERATOR',
 JSON_OBJECT('standard', 'duplicate original code review'), JSON_ARRAY('battery_registration_candidate.candidate_status', 'duplicate_code_review.review_result'),
 'Duplicate original code candidates must reach a terminal review result.',
 '完成重复编码人工核实并关联核实记录。', 0, 'ACTIVE'),
('DQ-004', '未验收通过的电池不能入库', '业务规则', 'inbound_record', 'AcceptanceBeforeInboundRule', 'HIGH', 'BUSINESS_SUPERVISOR',
 JSON_OBJECT('standard', 'accepted before inbound'), JSON_ARRAY('acceptance_record.acceptance_result', 'battery.lifecycle_status', 'inbound_record.inbound_status', 'inbound_record.inbound_at'),
 'Inbound records require previous passed acceptance evidence and valid lifecycle order.',
 '核对验收记录、入库记录和生命周期事件，关联更正证据。', 0, 'ACTIVE'),
('DQ-005', '库位必须属于所选仓库', '一致性', 'inbound_record', 'WarehouseLocationRule', 'HIGH', 'WAREHOUSE_ADMIN',
 JSON_OBJECT('standard', 'warehouse location relation'), JSON_ARRAY('inbound_record.warehouse_id', 'warehouse_location.warehouse_id'),
 'inbound_record.warehouse_id must equal warehouse_location.warehouse_id for the selected location.',
 '通过库存或入库更正记录修正仓库库位关系。', 0, 'ACTIVE'),
('DQ-006', '生命周期事件时间不能倒序', '时序性', 'lifecycle_event', 'LifecycleSequenceRule', 'MEDIUM', 'BUSINESS_SUPERVISOR',
 JSON_OBJECT('standard', 'lifecycle event order'), JSON_ARRAY('lifecycle_event.occurred_at'),
 'Key lifecycle events for the same battery must not have reversed occurred_at order.',
 '关联生命周期事件更正说明和证据后重新检查。', 0, 'ACTIVE'),
('DQ-007', '当前库存责任企业必须一致', '一致性', 'inventory', 'InventoryEnterpriseRule', 'HIGH', 'WAREHOUSE_ADMIN',
 JSON_OBJECT('standard', 'current inventory enterprise'), JSON_ARRAY('battery.current_responsible_enterprise_id', 'inventory.enterprise_id', 'inventory.is_current'),
 'Current inventory enterprise must match battery.current_responsible_enterprise_id when inventory.is_current = 1.',
 '通过库存调整或责任企业更正记录处理后重新检查。', 0, 'ACTIVE')
ON DUPLICATE KEY UPDATE
  rule_name = VALUES(rule_name),
  handler_code = VALUES(handler_code),
  severity = VALUES(severity),
  default_owner_role_code = VALUES(default_owner_role_code),
  data_standard_metadata = VALUES(data_standard_metadata),
  involved_fields = VALUES(involved_fields),
  check_condition = VALUES(check_condition),
  remediation_guidance = VALUES(remediation_guidance),
  allow_exemption = VALUES(allow_exemption),
  rule_status = VALUES(rule_status);

INSERT INTO dq_enterprise_rule_config (enterprise_id, rule_code, enabled_status, created_at, updated_at)
SELECT e.id, r.rule_code, 'ENABLED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM enterprise e
CROSS JOIN dq_rule_definition r
WHERE r.rule_code IN ('DQ-001', 'DQ-002', 'DQ-003', 'DQ-004', 'DQ-005', 'DQ-006', 'DQ-007')
ON DUPLICATE KEY UPDATE
  enabled_status = enabled_status,
  updated_at = updated_at;

INSERT INTO sys_permission (id, permission_code, permission_name, created_at, updated_at)
VALUES
(9201, 'dq:rule:read', '数据治理规则查看', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9202, 'dq:rule:toggle', '数据治理规则启停', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9203, 'dq:check:execute', '数据质量检查执行', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9204, 'dq:check:read', '数据质量检查查看', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9205, 'dq:issue:read', '数据质量问题查看', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9206, 'dq:issue:assign', '数据质量问题分配', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9207, 'dq:issue:process', '数据质量问题处理', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9208, 'dq:issue:recheck', '数据质量问题复核', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9209, 'dq:dashboard:read', '数据质量看板查看', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9210, 'dq:audit:read', '数据治理审计查看', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  updated_at = VALUES(updated_at);

INSERT INTO sys_role (id, role_code, role_name, created_at, updated_at)
VALUES
(9101, 'SYSTEM_ADMIN', '系统管理员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9102, 'BUSINESS_SUPERVISOR', '业务主管', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9103, 'RECYCLE_OPERATOR', '回收操作员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(9104, 'WAREHOUSE_ADMIN', '仓库管理员', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  updated_at = VALUES(updated_at);

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at)
VALUES
(9301, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:rule:read'), CURRENT_TIMESTAMP(3)),
(9302, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:read'), CURRENT_TIMESTAMP(3)),
(9303, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:dashboard:read'), CURRENT_TIMESTAMP(3)),
(9304, (SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:audit:read'), CURRENT_TIMESTAMP(3)),
(9311, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:rule:read'), CURRENT_TIMESTAMP(3)),
(9312, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:check:execute'), CURRENT_TIMESTAMP(3)),
(9313, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:check:read'), CURRENT_TIMESTAMP(3)),
(9314, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:read'), CURRENT_TIMESTAMP(3)),
(9315, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:assign'), CURRENT_TIMESTAMP(3)),
(9316, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:recheck'), CURRENT_TIMESTAMP(3)),
(9317, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:dashboard:read'), CURRENT_TIMESTAMP(3)),
(9318, (SELECT id FROM sys_role WHERE role_code = 'BUSINESS_SUPERVISOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:audit:read'), CURRENT_TIMESTAMP(3)),
(9321, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:read'), CURRENT_TIMESTAMP(3)),
(9322, (SELECT id FROM sys_role WHERE role_code = 'RECYCLE_OPERATOR'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:process'), CURRENT_TIMESTAMP(3)),
(9331, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:read'), CURRENT_TIMESTAMP(3)),
(9332, (SELECT id FROM sys_role WHERE role_code = 'WAREHOUSE_ADMIN'), (SELECT id FROM sys_permission WHERE permission_code = 'dq:issue:process'), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  created_at = created_at;
