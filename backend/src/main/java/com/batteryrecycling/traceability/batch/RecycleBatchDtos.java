package com.batteryrecycling.traceability.batch;

import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecycleBatchDtos {
    private RecycleBatchDtos() {
    }

    public record RecycleBatchCreateRequest(
            @NotBlank String sourceType,
            @NotBlank String sourceSubjectName,
            @NotNull LocalDate handoverDate,
            String handoverLocation,
            String relatedDocumentNo,
            String handoverPerson,
            String remark,
            List<Long> attachmentIds
    ) {
    }

    public record AddBatteryRequest(@NotNull Long batteryId) {
    }

    public record RecycleBatchDto(
            Long id,
            Long enterpriseId,
            String batchNo,
            String sourceType,
            String sourceSubjectName,
            LocalDate handoverDate,
            String handoverLocation,
            String relatedDocumentNo,
            String handoverPerson,
            String remark,
            String batchStatus,
            LocalDateTime submittedAt,
            Long createdBy,
            LocalDateTime createdAt,
            Long updatedBy,
            LocalDateTime updatedAt,
            Integer version,
            List<BatteryDto> batteries
    ) {
    }
}
