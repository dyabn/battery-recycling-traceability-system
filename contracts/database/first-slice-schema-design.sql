-- 第一切片数据库设计版 SQL V0.1
-- 目标数据库：MySQL 8
-- 用途：设计评审，不等同于正式 Flyway 迁移脚本。

CREATE TABLE IF NOT EXISTS enterprise (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  unified_social_credit_code VARCHAR(40) NULL,
  enabled_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_enterprise_name UNIQUE (name),
  CONSTRAINT ck_enterprise_enabled_status CHECK (enabled_status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  enabled_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_sys_user_username UNIQUE (username),
  CONSTRAINT fk_sys_user_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT ck_sys_user_enabled_status CHECK (enabled_status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_sys_role_code UNIQUE (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT PRIMARY KEY,
  permission_code VARCHAR(100) NOT NULL,
  permission_name VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id),
  CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id),
  CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
  CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recycle_batch (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  batch_no VARCHAR(40) NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_subject_name VARCHAR(100) NOT NULL,
  handover_date DATE NOT NULL,
  handover_location VARCHAR(200) NULL,
  related_document_no VARCHAR(80) NULL,
  handover_person VARCHAR(64) NULL,
  remark VARCHAR(500) NULL,
  batch_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
  submitted_at DATETIME(3) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_recycle_batch_no UNIQUE (batch_no),
  CONSTRAINT fk_recycle_batch_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_recycle_batch_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_recycle_batch_status CHECK (batch_status IN ('DRAFT', 'PENDING_ACCEPTANCE', 'ACCEPTANCE_PROCESSING', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS battery (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  system_trace_code VARCHAR(64) NOT NULL,
  original_code VARCHAR(100) NULL,
  battery_type VARCHAR(20) NOT NULL DEFAULT 'PACK',
  battery_model VARCHAR(100) NULL,
  manufacturer VARCHAR(100) NULL,
  battery_chemistry VARCHAR(40) NOT NULL,
  nominal_capacity DECIMAL(10, 2) NULL,
  production_date DATE NULL,
  current_responsible_enterprise_id BIGINT NOT NULL,
  lifecycle_status VARCHAR(40) NOT NULL DEFAULT 'REGISTERED',
  duplicate_status VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_battery_trace_code UNIQUE (system_trace_code),
  CONSTRAINT fk_battery_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_battery_responsible_enterprise FOREIGN KEY (current_responsible_enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_battery_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_battery_type CHECK (battery_type = 'PACK'),
  CONSTRAINT ck_battery_lifecycle_status CHECK (lifecycle_status IN ('REGISTERED', 'PENDING_ACCEPTANCE', 'ACCEPTED_PENDING_INBOUND', 'PENDING_SUPPLEMENT', 'ACCEPTANCE_REJECTED', 'IN_STOCK')),
  CONSTRAINT ck_battery_duplicate_status CHECK (duplicate_status IN ('NORMAL', 'SUSPECTED_DUPLICATE', 'RESOLVED_SAME', 'RESOLVED_DIFFERENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recycle_batch_battery (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  relation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  CONSTRAINT uk_batch_battery UNIQUE (batch_id, battery_id),
  CONSTRAINT fk_rbb_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_rbb_batch FOREIGN KEY (batch_id) REFERENCES recycle_batch(id),
  CONSTRAINT fk_rbb_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_rbb_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_rbb_status CHECK (relation_status IN ('ACTIVE', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS duplicate_code_review (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  original_code VARCHAR(100) NOT NULL,
  matched_battery_id BIGINT NULL,
  review_result VARCHAR(40) NOT NULL,
  duplicate_reason VARCHAR(255) NULL,
  reviewer_id BIGINT NOT NULL,
  reviewed_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_dcr_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_dcr_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_dcr_matched_battery FOREIGN KEY (matched_battery_id) REFERENCES battery(id),
  CONSTRAINT fk_dcr_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user(id),
  CONSTRAINT ck_dcr_result CHECK (review_result IN ('SAME_BATTERY', 'DIFFERENT_BATTERY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS acceptance_record (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  acceptance_result VARCHAR(30) NOT NULL,
  identity_check_result VARCHAR(40) NOT NULL,
  appearance_check_result VARCHAR(40) NOT NULL,
  document_check_result VARCHAR(40) NOT NULL,
  acceptance_note VARCHAR(500) NULL,
  accepted_by BIGINT NOT NULL,
  accepted_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_acceptance_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_acceptance_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_acceptance_batch FOREIGN KEY (batch_id) REFERENCES recycle_batch(id),
  CONSTRAINT fk_acceptance_user FOREIGN KEY (accepted_by) REFERENCES sys_user(id),
  CONSTRAINT ck_acceptance_result CHECK (acceptance_result IN ('PASS', 'NEED_SUPPLEMENT', 'REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS acceptance_supplement (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  acceptance_record_id BIGINT NULL,
  supplement_note VARCHAR(500) NOT NULL,
  supplemented_by BIGINT NOT NULL,
  supplemented_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_supplement_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_supplement_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_supplement_acceptance FOREIGN KEY (acceptance_record_id) REFERENCES acceptance_record(id),
  CONSTRAINT fk_supplement_user FOREIGN KEY (supplemented_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  warehouse_code VARCHAR(20) NOT NULL,
  warehouse_name VARCHAR(100) NOT NULL,
  enabled_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_warehouse_code UNIQUE (warehouse_code),
  CONSTRAINT fk_warehouse_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_warehouse_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_warehouse_code CHECK (warehouse_code REGEXP '^WH-[0-9]{3}$'),
  CONSTRAINT ck_warehouse_enabled_status CHECK (enabled_status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warehouse_location (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  location_code VARCHAR(40) NOT NULL,
  enabled_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_location_code UNIQUE (location_code),
  CONSTRAINT fk_location_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_location_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT ck_location_code CHECK (location_code REGEXP '^WH-[0-9]{3}-A[0-9]{2}-R[0-9]{2}-L[0-9]{2}$'),
  CONSTRAINT ck_location_enabled_status CHECK (enabled_status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inbound_record (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  inbound_no VARCHAR(40) NOT NULL,
  battery_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,
  inbound_status VARCHAR(20) NOT NULL DEFAULT 'EFFECTIVE',
  inbound_by BIGINT NOT NULL,
  inbound_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_inbound_no UNIQUE (inbound_no),
  CONSTRAINT uk_inbound_battery UNIQUE (battery_id),
  CONSTRAINT fk_inbound_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_inbound_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_inbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_inbound_location FOREIGN KEY (location_id) REFERENCES warehouse_location(id),
  CONSTRAINT fk_inbound_user FOREIGN KEY (inbound_by) REFERENCES sys_user(id),
  CONSTRAINT ck_inbound_status CHECK (inbound_status IN ('EFFECTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,
  inbound_record_id BIGINT NOT NULL,
  is_current TINYINT NOT NULL DEFAULT 1,
  current_battery_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_current = 1 THEN battery_id ELSE NULL END) STORED,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_inventory_current_battery UNIQUE (current_battery_id),
  CONSTRAINT fk_inventory_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_inventory_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_inventory_location FOREIGN KEY (location_id) REFERENCES warehouse_location(id),
  CONSTRAINT fk_inventory_inbound FOREIGN KEY (inbound_record_id) REFERENCES inbound_record(id),
  CONSTRAINT ck_inventory_current CHECK (is_current IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lifecycle_event (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  battery_id BIGINT NOT NULL,
  batch_id BIGINT NULL,
  event_type VARCHAR(50) NOT NULL,
  event_name VARCHAR(100) NOT NULL,
  from_status VARCHAR(40) NULL,
  to_status VARCHAR(40) NULL,
  event_result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  operator_user_id BIGINT NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  remark VARCHAR(500) NULL,
  trace_id VARCHAR(64) NULL,
  CONSTRAINT fk_event_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_event_battery FOREIGN KEY (battery_id) REFERENCES battery(id),
  CONSTRAINT fk_event_batch FOREIGN KEY (batch_id) REFERENCES recycle_batch(id),
  CONSTRAINT fk_event_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_event_result CHECK (event_result IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NULL,
  operator_user_id BIGINT NULL,
  action_code VARCHAR(80) NOT NULL,
  object_type VARCHAR(40) NULL,
  object_id BIGINT NULL,
  result VARCHAR(20) NOT NULL,
  reject_reason VARCHAR(255) NULL,
  operated_at DATETIME(3) NOT NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  trace_id VARCHAR(64) NULL,
  CONSTRAINT fk_audit_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS', 'FAILED', 'FORBIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS business_attachment (
  id BIGINT PRIMARY KEY,
  enterprise_id BIGINT NOT NULL,
  object_type VARCHAR(40) NOT NULL,
  object_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_ext VARCHAR(20) NOT NULL,
  file_size_bytes BIGINT NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  uploaded_by BIGINT NOT NULL,
  uploaded_at DATETIME(3) NOT NULL,
  CONSTRAINT fk_attachment_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprise(id),
  CONSTRAINT fk_attachment_user FOREIGN KEY (uploaded_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sys_user_enterprise ON sys_user (enterprise_id, enabled_status);
CREATE INDEX idx_batch_enterprise_status ON recycle_batch (enterprise_id, batch_status);
CREATE INDEX idx_battery_original_code ON battery (original_code);
CREATE INDEX idx_battery_enterprise_status ON battery (enterprise_id, lifecycle_status);
CREATE INDEX idx_batch_battery_battery ON recycle_batch_battery (battery_id);
CREATE INDEX idx_duplicate_original_code ON duplicate_code_review (original_code);
CREATE INDEX idx_acceptance_battery ON acceptance_record (battery_id, created_at);
CREATE INDEX idx_warehouse_enterprise_status ON warehouse (enterprise_id, enabled_status);
CREATE INDEX idx_location_warehouse_status ON warehouse_location (warehouse_id, enabled_status);
CREATE INDEX idx_inventory_enterprise_location ON inventory (enterprise_id, warehouse_id, location_id, is_current);
CREATE INDEX idx_event_battery_time ON lifecycle_event (battery_id, occurred_at);
CREATE INDEX idx_audit_operator_time ON audit_log (operator_user_id, operated_at);
CREATE INDEX idx_attachment_object ON business_attachment (object_type, object_id);

