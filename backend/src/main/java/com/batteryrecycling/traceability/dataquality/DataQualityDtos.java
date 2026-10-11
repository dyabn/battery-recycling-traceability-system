package com.batteryrecycling.traceability.dataquality;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DataQualityDtos {
    private DataQualityDtos() {
    }

    public record RuleDto(
            Long id,
            String ruleCode,
            String ruleName,
            String dimension,
            String objectType,
            String handlerCode,
            String severity,
            String defaultOwnerRoleCode,
            Map<String, Object> dataStandardMetadata,
            List<String> involvedFields,
            String checkCondition,
            String remediationGuidance,
            Boolean allowExemption,
            String ruleStatus,
            String enabledStatus
    ) {
    }

    public record ToggleRuleStatusRequest(
            @NotBlank String enabledStatus,
            @NotBlank String reason
    ) {
    }

    public record CreateCheckRunRequest(
            @NotBlank String runScope,
            List<String> requestedRuleCodes
    ) {
    }

    public record CheckRunDto(
            Long id,
            Long enterpriseId,
            String runNo,
            String runScope,
            String runStatus,
            List<String> requestedRuleCodes,
            List<String> executedRuleCodes,
            Integer checkedObjectCount,
            Integer checkedRuleCount,
            Integer issueCount,
            Map<String, Object> resultSummary,
            String failureReason,
            Long startedBy,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {
    }

    public record CheckResultDto(
            Long id,
            Long enterpriseId,
            Long runId,
            String ruleCode,
            String checkStatus,
            Integer checkedObjectCount,
            Integer violationCount,
            Map<String, Object> ruleSnapshot,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String errorMessage
    ) {
    }

    public record IssueDto(
            Long id,
            Long enterpriseId,
            String issueNo,
            String ruleCode,
            String objectType,
            Long objectId,
            String objectIdentity,
            String objectKey,
            String title,
            String description,
            String severity,
            String ownerRoleCode,
            String issueStatus,
            Long assignedToUserId,
            Long discoveredRunId,
            Long discoveredResultId,
            LocalDateTime firstDetectedAt,
            LocalDateTime lastDetectedAt
    ) {
    }
}
