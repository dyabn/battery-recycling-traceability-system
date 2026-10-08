package com.batteryrecycling.traceability.attachment;

import java.time.LocalDateTime;

public final class AttachmentDtos {
    private AttachmentDtos() {
    }

    public record AttachmentDto(
            Long id,
            String fileName,
            String fileExt,
            Long fileSizeBytes,
            String contentSha256,
            String bindingStatus,
            LocalDateTime expiresAt
    ) {
    }
}
