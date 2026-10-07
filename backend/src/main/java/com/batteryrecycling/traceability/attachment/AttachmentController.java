package com.batteryrecycling.traceability.attachment;

import com.batteryrecycling.traceability.attachment.AttachmentService.AttachmentDownload;
import com.batteryrecycling.traceability.common.api.ApiResponse;
import com.batteryrecycling.traceability.common.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController {
    private final AttachmentService attachmentService;
    private final CurrentUserService currentUserService;

    public AttachmentController(AttachmentService attachmentService, CurrentUserService currentUserService) {
        this.attachmentService = attachmentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(value = "/api/v1/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('attachment:upload')")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file, @RequestHeader("Idempotency-Key") String idempotencyKey, HttpServletRequest servletRequest) {
        return ApiResponse.ok(attachmentService.upload(currentUserService.requireCurrentUser(), file, idempotencyKey, servletRequest));
    }

    @GetMapping("/api/v1/attachments/{id}/download")
    @PreAuthorize("hasAuthority('attachment:read')")
    public ResponseEntity<byte[]> download(@PathVariable Long id, HttpServletRequest servletRequest) {
        AttachmentDownload download = attachmentService.download(currentUserService.requireCurrentUser(), id, servletRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(download.bytes());
    }
}
