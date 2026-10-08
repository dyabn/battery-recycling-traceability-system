package com.batteryrecycling.traceability.battery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class BatteryDtos {
    private BatteryDtos() {
    }

    public record BatteryCreateRequest(
            @Size(max = 100)
            String originalCode,
            @NotBlank String batteryType,
            @Size(max = 100)
            String batteryModel,
            @Size(max = 100)
            String manufacturer,
            @Size(max = 40)
            @NotBlank String batteryChemistry,
            @Digits(integer = 8, fraction = 2)
            BigDecimal nominalCapacity,
            LocalDate productionDate
    ) {
    }

    public record DuplicateCheckRequest(@NotBlank @Size(max = 100) String originalCode) {
    }

    public record DuplicateResolutionRequest(
            @NotBlank String reviewResult,
            Long existingBatteryId,
            @Size(max = 255)
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
            String result,
            Map<String, Object> details
    ) {
    }
}
