package com.batteryrecycling.traceability.attachment;

import com.batteryrecycling.traceability.attachment.AttachmentDtos.AttachmentDto;
import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    public AttachmentService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    public AttachmentDto upload(CurrentUser currentUser, MultipartFile file, String idempotencyKey, HttpServletRequest servletRequest) {
        validateUpload(file);
        String originalName = safeFileName(file.getOriginalFilename());
        String canonical = "fileName=" + originalName + ";size=" + file.getSize() + ";contentType=" + file.getContentType();
        return idempotencyService.execute("UPLOAD_ATTACHMENT", idempotencyKey, canonical, AttachmentDto.class,
                () -> uploadInTransaction(currentUser, file, originalName, servletRequest));
    }

    public AttachmentDto uploadInTransaction(CurrentUser currentUser, MultipartFile file, String originalName, HttpServletRequest servletRequest) {
        long attachmentId = idGenerator.nextId();
        String ext = extension(originalName);
        jdbcTemplate.update("""
                INSERT INTO business_attachment (
                  id, enterprise_id, object_type, object_id, binding_status, file_name,
                  file_ext, file_size_bytes, storage_path, uploaded_by, uploaded_at, expires_at
                )
                VALUES (?, ?, NULL, NULL, 'TEMP', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3), ?)
                """,
                attachmentId,
                currentUser.enterpriseId(),
                originalName,
                ext,
                file.getSize(),
                "temp://" + attachmentId + "/" + originalName,
                currentUser.id(),
                LocalDateTime.now().plusDays(1));
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "ATTACHMENT_UPLOADED", "BUSINESS_ATTACHMENT", attachmentId, "fileName=" + originalName, servletRequest);
        return requireAttachment(currentUser, attachmentId, servletRequest);
    }

    public AttachmentDownload download(CurrentUser currentUser, Long attachmentId, HttpServletRequest servletRequest) {
        List<AttachmentRecord> attachments = jdbcTemplate.query("""
                SELECT id, enterprise_id, file_name, file_ext, file_size_bytes, binding_status, uploaded_by, expires_at
                FROM business_attachment
                WHERE id = ? AND enterprise_id = ?
                """, this::mapRecord, attachmentId, currentUser.enterpriseId());
        if (!attachments.isEmpty()) {
            AttachmentRecord record = attachments.get(0);
            if ("TEMP".equals(record.bindingStatus()) && !record.uploadedBy().equals(currentUser.id())) {
                auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ATTACHMENT_DOWNLOAD_FORBIDDEN", "BUSINESS_ATTACHMENT", attachmentId, "FORBIDDEN", "拒绝读取他人临时附件", servletRequest);
                throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问该附件");
            }
            String content = "Attachment placeholder: " + record.fileName();
            return new AttachmentDownload(record.fileName(), content.getBytes(StandardCharsets.UTF_8));
        }
        if (exists(attachmentId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "ATTACHMENT_CROSS_ENTERPRISE_DENIED", "BUSINESS_ATTACHMENT", attachmentId, "FORBIDDEN", "拒绝跨企业附件访问", servletRequest);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的附件");
        }
        throw ApiException.notFound("附件不存在");
    }

    private AttachmentDto requireAttachment(CurrentUser currentUser, Long attachmentId, HttpServletRequest servletRequest) {
        List<AttachmentDto> attachments = jdbcTemplate.query("""
                SELECT id, file_name, file_ext, file_size_bytes, binding_status, expires_at
                FROM business_attachment
                WHERE id = ? AND enterprise_id = ?
                """, this::mapDto, attachmentId, currentUser.enterpriseId());
        if (attachments.isEmpty()) {
            throw ApiException.notFound("附件不存在");
        }
        return attachments.get(0);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "附件不能为空");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        if (originalName.length() > 255) {
            throw ApiException.badRequest("VALIDATION_FAILED", "附件文件名最长 255 字符");
        }
        if (extension(originalName).length() > 20) {
            throw ApiException.badRequest("VALIDATION_FAILED", "附件扩展名最长 20 字符");
        }
    }

    private AttachmentDto mapDto(ResultSet rs, int rowNum) throws SQLException {
        return new AttachmentDto(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("file_ext"),
                rs.getLong("file_size_bytes"),
                rs.getString("binding_status"),
                rs.getObject("expires_at", LocalDateTime.class));
    }

    private AttachmentRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new AttachmentRecord(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("file_name"),
                rs.getString("file_ext"),
                rs.getLong("file_size_bytes"),
                rs.getString("binding_status"),
                rs.getLong("uploaded_by"),
                rs.getObject("expires_at", LocalDateTime.class));
    }

    private boolean exists(Long attachmentId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_attachment WHERE id = ?", Integer.class, attachmentId);
        return count != null && count > 0;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment.bin";
        }
        return fileName.replace("\\", "_").replace("/", "_").trim();
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    public record AttachmentDownload(String fileName, byte[] bytes) {
    }

    private record AttachmentRecord(Long id, Long enterpriseId, String fileName, String fileExt, Long fileSizeBytes, String bindingStatus, Long uploadedBy, LocalDateTime expiresAt) {
    }
}
