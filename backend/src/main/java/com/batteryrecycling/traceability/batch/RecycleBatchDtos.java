package com.batteryrecycling.traceability.batch;

import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecycleBatchDtos {
    private RecycleBatchDtos() {
    }

    public record RecycleBatchCreateRequest(
            @Size(max = 30)
            @NotBlank String sourceType,
            @Size(max = 100)
            @NotBlank String sourceSubjectName,
            @NotNull LocalDate handoverDate,
            @Size(max = 200)
            String handoverLocation,
            @Size(max = 80)
            String relatedDocumentNo,
            @Size(max = 64)
            String handoverPerson,
            @Size(max = 500)
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
