package com.batteryrecycling.traceability.battery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BatteryDtos {
    private BatteryDtos() {
    }

    public record BatteryCreateRequest(
            String originalCode,
            @NotBlank String batteryType,
            String batteryModel,
            String manufacturer,
            @NotBlank String batteryChemistry,
            BigDecimal nominalCapacity,
            LocalDate productionDate
    ) {
    }

    public record DuplicateCheckRequest(@NotBlank String originalCode) {
    }

    public record DuplicateResolutionRequest(
            @NotBlank String reviewResult,
            Long existingBatteryId,
            String duplicateReason
    ) {
    }

    public record DuplicateCheckResult(boolean duplicated, List<Long> matchedBatteryIds) {
    }

    public record BatteryRegistrationResult(
            String resultType,
            BatteryDto battery,
            Long candidateId,
            String candidateStatus,
            List<Long> matchedBatteryIds
    ) {
        public static BatteryRegistrationResult created(BatteryDto battery) {
            return new BatteryRegistrationResult("BATTERY_CREATED", battery, null, null, List.of());
        }

        public static BatteryRegistrationResult duplicate(Long candidateId, List<Long> matchedBatteryIds) {
            return new BatteryRegistrationResult("DUPLICATE_REVIEW_REQUIRED", null, candidateId, "PENDING_REVIEW", matchedBatteryIds);
        }
    }

    public record BatteryDto(
            Long id,
            Long enterpriseId,
            String systemTraceCode,
            String originalCode,
            String batteryType,
            String batteryModel,
            String manufacturer,
            String batteryChemistry,
            BigDecimal nominalCapacity,
            LocalDate productionDate,
            Long currentResponsibleEnterpriseId,
            String lifecycleStatus,
            String duplicateStatus,
            Integer version
    ) {
    }

    public record TraceEventDto(
            String eventName,
            String objectCode,
            String operator,
            LocalDateTime occurredAt,
            String statusChange,
            String result
    ) {
    }
}
