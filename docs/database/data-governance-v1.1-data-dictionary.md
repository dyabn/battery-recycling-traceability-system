# 数据治理 V1.1 数据字典增量

文档状态：待评审
关联变更：CR-DG-001

## dq_rule_definition

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| rule_code | VARCHAR(32) | NOT NULL, UNIQUE | 规则编号，DQ-001..DQ-007。 |
| rule_name | VARCHAR(128) | NOT NULL | 规则名称。 |
| dimension | VARCHAR(32) | NOT NULL | 数据质量维度。 |
| object_type | VARCHAR(64) | NOT NULL | 适用对象。 |
| handler_code | VARCHAR(64) | NOT NULL | 固定规则处理器。 |
| severity | VARCHAR(16) | NOT NULL | `HIGH`、`MEDIUM`、`LOW`。 |
| default_owner_role | VARCHAR(64) | NOT NULL | 默认责任角色。 |
| data_standard_metadata | JSON | NOT NULL | 数据标准元数据。 |
| involved_fields | JSON | NOT NULL | 参与字段。 |
| check_condition | TEXT | NOT NULL | 判断条件说明。 |
| remediation_guidance | TEXT | NOT NULL | 整改说明。 |
| allow_exemption | TINYINT | NOT NULL | V1.1 固定为 0。 |
| rule_status | VARCHAR(16) | NOT NULL | 全局规则状态。 |
| created_at | DATETIME(3) | NOT NULL | 创建时间。 |
| updated_at | DATETIME(3) | NOT NULL | 更新时间。 |
| version | INT | NOT NULL | 乐观锁版本。 |

## dq_enterprise_rule_config

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| rule_code | VARCHAR(32) | NOT NULL, FK | 规则编号。 |
| enabled_status | VARCHAR(16) | NOT NULL | `ENABLED`、`DISABLED`。 |
| toggled_reason | VARCHAR(512) | NULL | 最近启停原因。 |
| toggled_by | BIGINT | NULL, FK | 最近启停人。 |
| toggled_at | DATETIME(3) | NULL | 最近启停时间。 |
| created_at | DATETIME(3) | NOT NULL | 创建时间。 |
| updated_at | DATETIME(3) | NOT NULL | 更新时间。 |
| version | INT | NOT NULL | 乐观锁版本。 |

唯一键：`uk_dq_enterprise_rule(enterprise_id, rule_code)`。

## dq_check_run

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| run_no | VARCHAR(40) | NOT NULL, UNIQUE | 检查任务编号。 |
| run_scope | VARCHAR(32) | NOT NULL | `ALL`、`BATTERY`、`INBOUND`、`INVENTORY`、`TRACE`。 |
| run_status | VARCHAR(16) | NOT NULL | `RUNNING`、`COMPLETED`、`FAILED`。 |
| requested_rule_codes | JSON | NULL | 请求检查规则。 |
| executed_rule_codes | JSON | NOT NULL | 实际执行规则。 |
| checked_object_count | INT | NOT NULL | 检查对象数。 |
| checked_rule_count | INT | NOT NULL | 执行规则数。 |
| issue_count | INT | NOT NULL | 本次发现或命中问题数。 |
| result_summary | JSON | NULL | 结果摘要。 |
| failure_reason | VARCHAR(512) | NULL | 失败原因。 |
| started_by | BIGINT | NOT NULL, FK | 发起人。 |
| started_at | DATETIME(3) | NOT NULL | 发起时间。 |
| completed_at | DATETIME(3) | NULL | 完成时间。 |
| trace_id | VARCHAR(64) | NULL | 链路 ID。 |
| version | INT | NOT NULL | 乐观锁版本。 |

## dq_check_result

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| run_id | BIGINT | NOT NULL, FK | 检查任务。 |
| rule_code | VARCHAR(32) | NOT NULL, FK | 规则编号。 |
| check_status | VARCHAR(16) | NOT NULL | `PASS`、`VIOLATION`、`SKIPPED`、`ERROR`。 |
| checked_object_count | INT | NOT NULL | 检查对象数。 |
| violation_count | INT | NOT NULL | 违规数。 |
| rule_snapshot | JSON | NOT NULL | 规则快照。 |
| started_at | DATETIME(3) | NOT NULL | 开始时间。 |
| completed_at | DATETIME(3) | NULL | 完成时间。 |
| error_message | VARCHAR(512) | NULL | 错误信息。 |

唯一键：`uk_dq_result_run_rule(run_id, rule_code)`。

## dq_issue

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| issue_no | VARCHAR(40) | NOT NULL, UNIQUE | 问题编号。 |
| rule_code | VARCHAR(32) | NOT NULL, FK | 规则编号。 |
| object_type | VARCHAR(64) | NOT NULL | 业务对象类型。 |
| object_id | BIGINT | NULL | 业务对象 ID。 |
| object_key | VARCHAR(128) | NOT NULL | 业务对象展示键或隔离键。 |
| title | VARCHAR(128) | NOT NULL | 问题标题。 |
| description | VARCHAR(1000) | NOT NULL | 问题描述。 |
| severity | VARCHAR(16) | NOT NULL | 严重程度。 |
| owner_role | VARCHAR(64) | NOT NULL | 默认责任角色。 |
| issue_status | VARCHAR(16) | NOT NULL | `OPEN`、`ASSIGNED`、`PROCESSING`、`SUBMITTED`、`RECHECKING`、`REJECTED`、`CLOSED`。 |
| assigned_to_user_id | BIGINT | NULL, FK | 责任人。 |
| assigned_by_user_id | BIGINT | NULL, FK | 分配人。 |
| assigned_at | DATETIME(3) | NULL | 分配时间。 |
| discovered_run_id | BIGINT | NOT NULL, FK | 首次发现任务。 |
| discovered_result_id | BIGINT | NOT NULL, FK | 首次发现结果。 |
| first_detected_at | DATETIME(3) | NOT NULL | 首次发现时间。 |
| last_detected_at | DATETIME(3) | NOT NULL | 最近命中时间。 |
| closed_at | DATETIME(3) | NULL | 关闭时间。 |
| open_issue_key | VARCHAR(255) | GENERATED | 未关闭问题去重键。 |
| created_at | DATETIME(3) | NOT NULL | 创建时间。 |
| updated_at | DATETIME(3) | NOT NULL | 更新时间。 |
| version | INT | NOT NULL | 乐观锁版本。 |

## dq_remediation

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| issue_id | BIGINT | NOT NULL, FK | 质量问题。 |
| remediation_note | VARCHAR(1000) | NOT NULL | 处理说明。 |
| evidence_attachment_id | BIGINT | NULL, FK | 处理证据附件。 |
| correction_object_type | VARCHAR(64) | NULL | 更正记录对象类型。 |
| correction_object_id | BIGINT | NULL | 更正记录对象 ID。 |
| remediation_status | VARCHAR(16) | NOT NULL | `SUBMITTED`。 |
| submitted_by | BIGINT | NOT NULL, FK | 提交人。 |
| submitted_at | DATETIME(3) | NOT NULL | 提交时间。 |
| created_at | DATETIME(3) | NOT NULL | 创建时间。 |
| version | INT | NOT NULL | 乐观锁版本。 |

## dq_recheck

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 主键。 |
| enterprise_id | BIGINT | NOT NULL, FK | 企业。 |
| issue_id | BIGINT | NOT NULL, FK | 质量问题。 |
| recheck_status | VARCHAR(16) | NOT NULL | `RUNNING`、`PASSED`、`FAILED`。 |
| linked_check_run_id | BIGINT | NULL, FK | 关联检查任务。 |
| result_note | VARCHAR(1000) | NULL | 复核说明。 |
| started_by | BIGINT | NOT NULL, FK | 复核人。 |
| started_at | DATETIME(3) | NOT NULL | 开始时间。 |
| completed_at | DATETIME(3) | NULL | 完成时间。 |
| created_at | DATETIME(3) | NOT NULL | 创建时间。 |
| version | INT | NOT NULL | 乐观锁版本。 |

## 当前结论

数据字典增量待评审。字段以 `contracts/database/data-governance-v1.1-migration.sql` 为准。
