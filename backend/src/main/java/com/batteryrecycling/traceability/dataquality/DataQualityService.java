package com.batteryrecycling.traceability.dataquality;

import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.CheckResultDto;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.CheckRunDto;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.CreateCheckRunRequest;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.IssueDto;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.RuleDto;
import com.batteryrecycling.traceability.dataquality.DataQualityDtos.ToggleRuleStatusRequest;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataQualityService {
    private static final List<String> ALL_RULES = List.of("DQ-001", "DQ-002", "DQ-003", "DQ-004", "DQ-005", "DQ-006", "DQ-007");
    private static final Map<String, List<String>> SCOPE_RULES = Map.of(
            "ALL", ALL_RULES,
            "BATTERY", List.of("DQ-001", "DQ-002", "DQ-003"),
            "INBOUND", List.of("DQ-004", "DQ-005"),
            "INVENTORY", List.of("DQ-007"),
            "TRACE", List.of("DQ-006")
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final DataQualityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public DataQualityService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, DataQualityAuditService auditService, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<RuleDto> listRules(CurrentUser currentUser) {
        return jdbcTemplate.query("""
                SELECT r.id, r.rule_code, r.rule_name, r.dimension, r.object_type, r.handler_code,
                       r.severity, r.default_owner_role_code, r.data_standard_metadata,
                       r.involved_fields, r.check_condition, r.remediation_guidance, r.allow_exemption,
                       r.rule_status, COALESCE(c.enabled_status, 'DISABLED') AS enabled_status
                FROM dq_rule_definition r
                LEFT JOIN dq_enterprise_rule_config c
                  ON c.rule_code = r.rule_code AND c.enterprise_id = ?
                WHERE r.rule_code IN ('DQ-001','DQ-002','DQ-003','DQ-004','DQ-005','DQ-006','DQ-007')
                ORDER BY r.rule_code
                """, this::mapRule, currentUser.enterpriseId());
    }

    public void rejectRuleToggle(CurrentUser currentUser, String ruleCode, ToggleRuleStatusRequest request, HttpServletRequest servletRequest) {
        auditService.record(currentUser, "DQ_RULE_TOGGLE_DENIED", "DQ_RULE", null, "FORBIDDEN", "I5 不允许内置角色启停规则: " + ruleCode, servletRequest);
        throw ApiException.forbidden("RULE_TOGGLE_FORBIDDEN", "本阶段不允许启停内置数据质量规则");
    }

    public CheckRunDto createCheckRun(CurrentUser currentUser, CreateCheckRunRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = canonical(request);
        return idempotencyService.execute("CREATE_DQ_CHECK_RUN", idempotencyKey, canonical, CheckRunDto.class,
                () -> createCheckRunOnce(currentUser, request, servletRequest, idempotencyService.sha256Hex(idempotencyKey)));
    }

    private CheckRunDto createCheckRunOnce(CurrentUser currentUser, CreateCheckRunRequest request, HttpServletRequest servletRequest, String keyHash) {
        String scope = normalizeScope(request.runScope());
        List<String> scopeRules = SCOPE_RULES.get(scope);
        if (scopeRules == null) {
            auditService.record(currentUser, "DQ_VALIDATION_FAILED", "DQ_CHECK_RUN", null, "FAILED", "未知检查范围", servletRequest);
            throw ApiException.badRequest("VALIDATION_FAILED", "未知检查范围");
        }

        List<RuleDto> rules = listRules(currentUser);
        Map<String, RuleDto> ruleByCode = rules.stream().collect(Collectors.toMap(RuleDto::ruleCode, rule -> rule, (left, right) -> left, LinkedHashMap::new));
        int configCount = count("""
                SELECT COUNT(*)
                FROM dq_enterprise_rule_config
                WHERE enterprise_id = ?
                  AND rule_code IN ('DQ-001','DQ-002','DQ-003','DQ-004','DQ-005','DQ-006','DQ-007')
                """, currentUser.enterpriseId());
        if (!ruleByCode.keySet().containsAll(ALL_RULES) || configCount < ALL_RULES.size()) {
            CheckRunDto failed = createRun(currentUser, scope, request.requestedRuleCodes(), List.of(), "FAILED", "当前企业缺少数据质量规则配置");
            auditService.record(currentUser, "DQ_CHECK_FAILED", "DQ_CHECK_RUN", failed.id(), "FAILED", failed.failureReason(), servletRequest, keyHash);
            return failed;
        }

        List<String> selectedRules = selectedRules(currentUser, request.requestedRuleCodes(), scope, scopeRules, ruleByCode, servletRequest);
        CheckRunDto running = createRun(currentUser, scope, request.requestedRuleCodes(), selectedRules, "RUNNING", null);
        auditService.record(currentUser, "DQ_CHECK_CREATED", "DQ_CHECK_RUN", running.id(), "SUCCESS", "创建数据质量检查", servletRequest, keyHash);
        try {
            ExecutionSummary summary = requiresNew.execute(status -> executeRules(currentUser, running.id(), selectedRules, ruleByCode, servletRequest));
            updateRunCompleted(running.id(), summary);
            auditService.record(currentUser, "DQ_CHECK_COMPLETED", "DQ_CHECK_RUN", running.id(), "SUCCESS", "数据质量检查完成", servletRequest, keyHash);
            return getCheckRun(currentUser, running.id(), servletRequest);
        } catch (RuntimeException exception) {
            String reason = "数据质量检查执行失败";
            updateRunFailed(running.id(), reason);
            auditService.record(currentUser, "DQ_CHECK_FAILED", "DQ_CHECK_RUN", running.id(), "FAILED", reason, servletRequest, keyHash);
            return getCheckRun(currentUser, running.id(), servletRequest);
        }
    }

    public CheckRunDto getCheckRun(CurrentUser currentUser, Long runId, HttpServletRequest servletRequest) {
        List<CheckRunDto> rows = jdbcTemplate.query("""
                SELECT id, enterprise_id, run_no, run_scope, run_status, requested_rule_codes,
                       executed_rule_codes, checked_object_count, checked_rule_count, issue_count,
                       result_summary, failure_reason, started_by, started_at, completed_at
                FROM dq_check_run
                WHERE id = ? AND enterprise_id = ?
                """, this::mapCheckRun, runId, currentUser.enterpriseId());
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        if (exists("dq_check_run", runId)) {
            auditService.record(currentUser, "DQ_CHECK_CROSS_ENTERPRISE_DENIED", "DQ_CHECK_RUN", runId, "FORBIDDEN", "拒绝跨企业检查任务访问", servletRequest);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的检查任务");
        }
        throw ApiException.notFound("检查任务不存在");
    }

    public List<CheckResultDto> listCheckResults(CurrentUser currentUser, Long runId, HttpServletRequest servletRequest) {
        getCheckRun(currentUser, runId, servletRequest);
        return jdbcTemplate.query("""
                SELECT id, enterprise_id, run_id, rule_code, check_status, checked_object_count,
                       violation_count, rule_snapshot, started_at, completed_at, error_message
                FROM dq_check_result
                WHERE run_id = ? AND enterprise_id = ?
                ORDER BY rule_code
                """, this::mapCheckResult, runId, currentUser.enterpriseId());
    }

    public List<IssueDto> listIssues(CurrentUser currentUser, String status, HttpServletRequest servletRequest) {
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, enterprise_id, issue_no, rule_code, object_type, object_id, object_identity,
                       object_key, title, description, severity, owner_role_code, issue_status,
                       assigned_to_user_id, discovered_run_id, discovered_result_id, first_detected_at,
                       last_detected_at
                FROM dq_issue
                WHERE enterprise_id = ?
                """);
        args.add(currentUser.enterpriseId());
        if (normalizedStatus != null) {
            sql.append(" AND issue_status = ?");
            args.add(normalizedStatus);
        }
        if (!canReadAllIssues(currentUser)) {
            sql.append(" AND assigned_to_user_id = ?");
            args.add(currentUser.id());
        }
        sql.append(" ORDER BY last_detected_at DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapIssue, args.toArray());
    }

    public IssueDto getIssue(CurrentUser currentUser, Long issueId, HttpServletRequest servletRequest) {
        List<IssueDto> rows = jdbcTemplate.query("""
                SELECT id, enterprise_id, issue_no, rule_code, object_type, object_id, object_identity,
                       object_key, title, description, severity, owner_role_code, issue_status,
                       assigned_to_user_id, discovered_run_id, discovered_result_id, first_detected_at,
                       last_detected_at
                FROM dq_issue
                WHERE id = ? AND enterprise_id = ?
                """, this::mapIssue, issueId, currentUser.enterpriseId());
        if (rows.isEmpty()) {
            if (exists("dq_issue", issueId)) {
                auditService.record(currentUser, "DQ_ISSUE_CROSS_ENTERPRISE_DENIED", "DQ_ISSUE", issueId, "FORBIDDEN", "拒绝跨企业质量问题访问", servletRequest);
                throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的质量问题");
            }
            throw ApiException.notFound("质量问题不存在");
        }
        IssueDto issue = rows.get(0);
        if (!canReadAllIssues(currentUser) && !currentUser.id().equals(issue.assignedToUserId())) {
            auditService.record(currentUser, "DQ_ISSUE_ACCESS_DENIED", "DQ_ISSUE", issueId, "FORBIDDEN", "只能查看分配给本人的质量问题", servletRequest);
            throw ApiException.forbidden("FORBIDDEN", "只能查看分配给本人的质量问题");
        }
        return issue;
    }

    private ExecutionSummary executeRules(CurrentUser currentUser, Long runId, List<String> selectedRules, Map<String, RuleDto> ruleByCode, HttpServletRequest request) {
        int checkedObjectCount = 0;
        int violationCount = 0;
        int passRuleCount = 0;
        int violationRuleCount = 0;
        int newIssueCount = 0;
        int dedupHitCount = 0;
        for (String ruleCode : selectedRules) {
            RuleDto rule = ruleByCode.get(ruleCode);
            RuleOutcome outcome = runRule(currentUser, ruleCode);
            checkedObjectCount += outcome.checkedObjectCount();
            violationCount += outcome.violations().size();
            if (outcome.violations().isEmpty()) {
                passRuleCount++;
            } else {
                violationRuleCount++;
            }
            long resultId = idGenerator.nextId();
            jdbcTemplate.update("""
                    INSERT INTO dq_check_result (
                      id, enterprise_id, run_id, rule_code, check_status, checked_object_count,
                      violation_count, rule_snapshot, started_at, completed_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """,
                    resultId,
                    currentUser.enterpriseId(),
                    runId,
                    ruleCode,
                    outcome.violations().isEmpty() ? "PASS" : "VIOLATION",
                    outcome.checkedObjectCount(),
                    outcome.violations().size(),
                    json(ruleSnapshot(rule)));
            for (RuleViolation violation : outcome.violations()) {
                boolean created = createOrRediscoverIssue(currentUser, runId, resultId, rule, violation, request);
                if (created) {
                    newIssueCount++;
                } else {
                    dedupHitCount++;
                }
            }
        }
        return new ExecutionSummary(checkedObjectCount, selectedRules.size(), violationCount,
                Map.of(
                        "passRuleCount", passRuleCount,
                        "violationRuleCount", violationRuleCount,
                        "newIssueCount", newIssueCount,
                        "dedupHitCount", dedupHitCount
                ));
    }

    private boolean createOrRediscoverIssue(CurrentUser currentUser, Long runId, Long resultId, RuleDto rule, RuleViolation violation, HttpServletRequest request) {
        String openIssueKey = openIssueKey(rule.ruleCode(), rule.objectType(), violation.objectIdentity());
        List<Long> existing = jdbcTemplate.queryForList("""
                SELECT id
                FROM dq_issue
                WHERE enterprise_id = ? AND open_issue_key = ?
                """, Long.class, currentUser.enterpriseId(), openIssueKey);
        if (!existing.isEmpty()) {
            jdbcTemplate.update("UPDATE dq_issue SET last_detected_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ?", existing.get(0));
            auditService.record(currentUser, "DQ_ISSUE_REDISCOVERED", "DQ_ISSUE", existing.get(0), "SUCCESS", "再次发现未关闭质量问题", request);
            return false;
        }
        long issueId = idGenerator.nextId();
        try {
            jdbcTemplate.update("""
                    INSERT INTO dq_issue (
                      id, enterprise_id, issue_no, rule_code, object_type, object_id, object_identity,
                      object_key, title, description, severity, owner_role_code, issue_status,
                      assigned_to_user_id, discovered_run_id, discovered_result_id, first_detected_at,
                      last_detected_at, created_at, updated_at, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', NULL, ?, ?, CURRENT_TIMESTAMP(3),
                            CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    issueId,
                    currentUser.enterpriseId(),
                    "DQI-" + issueId,
                    rule.ruleCode(),
                    rule.objectType(),
                    violation.objectId(),
                    violation.objectIdentity(),
                    violation.objectKey(),
                    rule.ruleCode() + " " + rule.ruleName(),
                    violation.description(),
                    rule.severity(),
                    rule.defaultOwnerRoleCode(),
                    runId,
                    resultId);
            auditService.record(currentUser, "DQ_ISSUE_CREATED", "DQ_ISSUE", issueId, "SUCCESS", rule.ruleCode(), request);
            return true;
        } catch (DataIntegrityViolationException exception) {
            List<Long> concurrentExisting = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM dq_issue
                    WHERE enterprise_id = ? AND open_issue_key = ?
                    """, Long.class, currentUser.enterpriseId(), openIssueKey);
            if (!concurrentExisting.isEmpty()) {
                jdbcTemplate.update("UPDATE dq_issue SET last_detected_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = ?", concurrentExisting.get(0));
                auditService.record(currentUser, "DQ_ISSUE_REDISCOVERED", "DQ_ISSUE", concurrentExisting.get(0), "SUCCESS", "并发检查命中既有质量问题", request);
                return false;
            }
            throw exception;
        }
    }

    private RuleOutcome runRule(CurrentUser currentUser, String ruleCode) {
        return switch (ruleCode) {
            case "DQ-001" -> dq001(currentUser);
            case "DQ-002" -> dq002(currentUser);
            case "DQ-003" -> dq003(currentUser);
            case "DQ-004" -> dq004(currentUser);
            case "DQ-005" -> dq005(currentUser);
            case "DQ-006" -> dq006(currentUser);
            case "DQ-007" -> dq007(currentUser);
            default -> throw ApiException.badRequest("VALIDATION_FAILED", "未知规则");
        };
    }

    private RuleOutcome dq001(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM battery WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT b.id, b.system_trace_code
                FROM battery b
                JOIN (
                  SELECT system_trace_code
                  FROM battery
                  GROUP BY system_trace_code
                  HAVING COUNT(*) > 1
                ) duplicated ON duplicated.system_trace_code = b.system_trace_code
                WHERE b.enterprise_id = ?
                ORDER BY b.system_trace_code, b.id
                """, (rs, rowNum) -> violation(rs.getLong("id"), rs.getString("system_trace_code"), "系统追溯编码重复"), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq002(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM battery WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT id, system_trace_code
                FROM battery
                WHERE enterprise_id = ?
                  AND (
                    system_trace_code IS NULL OR TRIM(system_trace_code) = ''
                    OR battery_type IS NULL OR TRIM(battery_type) = ''
                    OR battery_chemistry IS NULL OR TRIM(battery_chemistry) = ''
                    OR lifecycle_status IS NULL OR TRIM(lifecycle_status) = ''
                    OR current_responsible_enterprise_id IS NULL
                  )
                ORDER BY id
                """, (rs, rowNum) -> violation(rs.getLong("id"), String.valueOf(rs.getLong("id")), "电池核心字段为空"), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq003(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM battery_registration_candidate WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT id, original_code
                FROM battery_registration_candidate
                WHERE enterprise_id = ? AND candidate_status = 'PENDING_REVIEW'
                ORDER BY id
                """, (rs, rowNum) -> violation(rs.getLong("id"), String.valueOf(rs.getLong("id")), "存在未完成核实的重复编码候选: " + rs.getString("original_code")), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq004(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM inbound_record WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT ir.id, ir.inbound_no
                FROM inbound_record ir
                WHERE ir.enterprise_id = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM acceptance_record ar
                    WHERE ar.enterprise_id = ir.enterprise_id
                      AND ar.battery_id = ir.battery_id
                      AND ar.acceptance_result = 'PASS'
                      AND ar.accepted_at <= ir.inbound_at
                  )
                ORDER BY ir.id
                """, (rs, rowNum) -> violation(rs.getLong("id"), String.valueOf(rs.getLong("id")), "入库前缺少验收通过事实: " + rs.getString("inbound_no")), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq005(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM inbound_record WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT ir.id, ir.inbound_no
                FROM inbound_record ir
                LEFT JOIN warehouse_location wl ON wl.id = ir.location_id
                WHERE ir.enterprise_id = ?
                  AND (wl.id IS NULL OR wl.warehouse_id <> ir.warehouse_id)
                ORDER BY ir.id
                """, (rs, rowNum) -> violation(rs.getLong("id"), String.valueOf(rs.getLong("id")), "入库库位不存在或不属于所选仓库: " + rs.getString("inbound_no")), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq006(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(DISTINCT battery_id) FROM lifecycle_event WHERE enterprise_id = ?", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT DISTINCT later.battery_id
                FROM lifecycle_event earlier
                JOIN lifecycle_event later
                  ON later.enterprise_id = earlier.enterprise_id
                 AND later.battery_id = earlier.battery_id
                WHERE earlier.enterprise_id = ?
                  AND CASE earlier.event_type
                        WHEN 'BATTERY_REGISTERED' THEN 10
                        WHEN 'DUPLICATE_REVIEW_SAME' THEN 15
                        WHEN 'DUPLICATE_REVIEW_DIFFERENT' THEN 15
                        WHEN 'BATCH_SUBMITTED' THEN 20
                        WHEN 'ACCEPTANCE_NEED_SUPPLEMENT' THEN 30
                        WHEN 'ACCEPTANCE_SUPPLEMENTED' THEN 35
                        WHEN 'ACCEPTANCE_PASSED' THEN 40
                        WHEN 'ACCEPTANCE_REJECTED' THEN 40
                        WHEN 'INBOUND_COMPLETED' THEN 50
                        ELSE 0
                      END
                    < CASE later.event_type
                        WHEN 'BATTERY_REGISTERED' THEN 10
                        WHEN 'DUPLICATE_REVIEW_SAME' THEN 15
                        WHEN 'DUPLICATE_REVIEW_DIFFERENT' THEN 15
                        WHEN 'BATCH_SUBMITTED' THEN 20
                        WHEN 'ACCEPTANCE_NEED_SUPPLEMENT' THEN 30
                        WHEN 'ACCEPTANCE_SUPPLEMENTED' THEN 35
                        WHEN 'ACCEPTANCE_PASSED' THEN 40
                        WHEN 'ACCEPTANCE_REJECTED' THEN 40
                        WHEN 'INBOUND_COMPLETED' THEN 50
                        ELSE 0
                      END
                  AND later.occurred_at < earlier.occurred_at
                ORDER BY later.battery_id
                """, (rs, rowNum) -> violation(rs.getLong("battery_id"), String.valueOf(rs.getLong("battery_id")), "生命周期后置事件早于前置事件"), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private RuleOutcome dq007(CurrentUser currentUser) {
        int checked = count("SELECT COUNT(*) FROM inventory WHERE enterprise_id = ? AND is_current = 1", currentUser.enterpriseId());
        List<RuleViolation> violations = jdbcTemplate.query("""
                SELECT i.id, b.system_trace_code
                FROM inventory i
                JOIN battery b ON b.id = i.battery_id
                WHERE i.enterprise_id = ?
                  AND i.is_current = 1
                  AND i.enterprise_id <> b.current_responsible_enterprise_id
                ORDER BY i.id
                """, (rs, rowNum) -> violation(rs.getLong("id"), String.valueOf(rs.getLong("id")), "当前库存企业与电池责任企业不一致: " + rs.getString("system_trace_code")), currentUser.enterpriseId());
        return new RuleOutcome(checked, violations);
    }

    private List<String> selectedRules(CurrentUser currentUser, List<String> requestedRuleCodes, String scope, List<String> scopeRules, Map<String, RuleDto> ruleByCode, HttpServletRequest request) {
        List<String> requested = requestedRuleCodes == null ? List.of() : requestedRuleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> selected = requested.isEmpty() ? scopeRules : requested;
        for (String ruleCode : selected) {
            RuleDto rule = ruleByCode.get(ruleCode);
            if (rule == null || !ALL_RULES.contains(ruleCode)) {
                auditService.record(currentUser, "DQ_VALIDATION_FAILED", "DQ_CHECK_RUN", null, "FAILED", "未知规则: " + ruleCode, request);
                throw ApiException.badRequest("VALIDATION_FAILED", "未知规则: " + ruleCode);
            }
            if (!scopeRules.contains(ruleCode)) {
                auditService.record(currentUser, "DQ_VALIDATION_FAILED", "DQ_CHECK_RUN", null, "FAILED", "规则不属于检查范围: " + ruleCode, request);
                throw ApiException.badRequest("VALIDATION_FAILED", "规则不属于检查范围: " + ruleCode);
            }
            if (!"ENABLED".equals(rule.enabledStatus())) {
                auditService.record(currentUser, "DQ_VALIDATION_FAILED", "DQ_CHECK_RUN", null, "FAILED", "规则未启用: " + ruleCode, request);
                throw ApiException.badRequest("VALIDATION_FAILED", "规则未启用: " + ruleCode);
            }
        }
        return selected;
    }

    private CheckRunDto createRun(CurrentUser currentUser, String scope, List<String> requestedRuleCodes, List<String> selectedRules, String status, String failureReason) {
        long runId = idGenerator.nextId();
        requiresNew.executeWithoutResult(tx -> jdbcTemplate.update("""
                INSERT INTO dq_check_run (
                  id, enterprise_id, run_no, run_scope, run_status, requested_rule_codes,
                  executed_rule_codes, checked_object_count, checked_rule_count, issue_count,
                  result_summary, failure_reason, started_by, started_at, completed_at, trace_id, version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?, ?, CURRENT_TIMESTAMP(3),
                        CASE WHEN ? <> 'RUNNING' THEN CURRENT_TIMESTAMP(3) ELSE NULL END, NULL, 0)
                """,
                runId,
                currentUser.enterpriseId(),
                "DQR-" + runId,
                scope,
                status,
                requestedRuleCodes == null ? null : json(normalizedRuleCodes(requestedRuleCodes)),
                json(selectedRules),
                selectedRules.size(),
                json(Map.of("passRuleCount", 0, "violationRuleCount", 0, "newIssueCount", 0, "dedupHitCount", 0)),
                failureReason,
                currentUser.id(),
                status));
        return getCheckRun(currentUser, runId, null);
    }

    private void updateRunCompleted(Long runId, ExecutionSummary summary) {
        requiresNew.executeWithoutResult(tx -> jdbcTemplate.update("""
                UPDATE dq_check_run
                SET run_status = 'COMPLETED',
                    checked_object_count = ?,
                    checked_rule_count = ?,
                    issue_count = ?,
                    result_summary = ?,
                    completed_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ?
                """, summary.checkedObjectCount(), summary.checkedRuleCount(), summary.issueCount(), json(summary.resultSummary()), runId));
    }

    private void updateRunFailed(Long runId, String reason) {
        requiresNew.executeWithoutResult(tx -> jdbcTemplate.update("""
                UPDATE dq_check_run
                SET run_status = 'FAILED',
                    failure_reason = ?,
                    completed_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = ?
                """, reason, runId));
    }

    private RuleDto mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new RuleDto(
                rs.getLong("id"),
                rs.getString("rule_code"),
                rs.getString("rule_name"),
                rs.getString("dimension"),
                rs.getString("object_type"),
                rs.getString("handler_code"),
                rs.getString("severity"),
                rs.getString("default_owner_role_code"),
                readMap(rs.getString("data_standard_metadata")),
                readList(rs.getString("involved_fields")),
                rs.getString("check_condition"),
                rs.getString("remediation_guidance"),
                rs.getBoolean("allow_exemption"),
                rs.getString("rule_status"),
                rs.getString("enabled_status"));
    }

    private CheckRunDto mapCheckRun(ResultSet rs, int rowNum) throws SQLException {
        return new CheckRunDto(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("run_no"),
                rs.getString("run_scope"),
                rs.getString("run_status"),
                readList(rs.getString("requested_rule_codes")),
                readList(rs.getString("executed_rule_codes")),
                rs.getInt("checked_object_count"),
                rs.getInt("checked_rule_count"),
                rs.getInt("issue_count"),
                readMap(rs.getString("result_summary")),
                rs.getString("failure_reason"),
                rs.getLong("started_by"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class));
    }

    private CheckResultDto mapCheckResult(ResultSet rs, int rowNum) throws SQLException {
        return new CheckResultDto(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getLong("run_id"),
                rs.getString("rule_code"),
                rs.getString("check_status"),
                rs.getInt("checked_object_count"),
                rs.getInt("violation_count"),
                readMap(rs.getString("rule_snapshot")),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class),
                rs.getString("error_message"));
    }

    private IssueDto mapIssue(ResultSet rs, int rowNum) throws SQLException {
        Long assigned = rs.getObject("assigned_to_user_id") == null ? null : rs.getLong("assigned_to_user_id");
        Long objectId = rs.getObject("object_id") == null ? null : rs.getLong("object_id");
        return new IssueDto(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("issue_no"),
                rs.getString("rule_code"),
                rs.getString("object_type"),
                objectId,
                rs.getString("object_identity"),
                rs.getString("object_key"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("severity"),
                rs.getString("owner_role_code"),
                rs.getString("issue_status"),
                assigned,
                rs.getLong("discovered_run_id"),
                rs.getLong("discovered_result_id"),
                rs.getObject("first_detected_at", LocalDateTime.class),
                rs.getObject("last_detected_at", LocalDateTime.class));
    }

    private Map<String, Object> ruleSnapshot(RuleDto rule) {
        return Map.of(
                "ruleCode", rule.ruleCode(),
                "ruleName", rule.ruleName(),
                "severity", rule.severity(),
                "ownerRoleCode", rule.defaultOwnerRoleCode(),
                "checkCondition", rule.checkCondition()
        );
    }

    private boolean canReadAllIssues(CurrentUser currentUser) {
        return currentUser.roles().contains("SYSTEM_ADMIN") || currentUser.roles().contains("BUSINESS_SUPERVISOR");
    }

    private boolean exists(String tableName, Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private String canonical(CreateCheckRunRequest request) {
        return "runScope=" + normalizeScope(request.runScope()) + ";requestedRuleCodes=" + normalizedRuleCodes(request.requestedRuleCodes());
    }

    private List<String> normalizedRuleCodes(List<String> requestedRuleCodes) {
        if (requestedRuleCodes == null) {
            return List.of();
        }
        return requestedRuleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .sorted()
                .toList();
    }

    private String normalizeScope(String scope) {
        return scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
    }

    private RuleViolation violation(Long objectId, String identity, String description) {
        String normalizedIdentity = identity == null ? String.valueOf(objectId) : identity.trim();
        return new RuleViolation(objectId, normalizedIdentity, normalizedIdentity, description);
    }

    private String openIssueKey(String ruleCode, String objectType, String objectIdentity) {
        return (ruleCode + "#" + objectType + "#" + objectIdentity.trim().toLowerCase(Locale.ROOT));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException("VALIDATION_FAILED", "JSON 序列化失败", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private record RuleOutcome(int checkedObjectCount, List<RuleViolation> violations) {
    }

    private record RuleViolation(Long objectId, String objectIdentity, String objectKey, String description) {
    }

    private record ExecutionSummary(int checkedObjectCount, int checkedRuleCount, int issueCount, Map<String, Object> resultSummary) {
    }
}
