package com.batteryrecycling.traceability.inbound;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.battery.BatteryDtos.BatteryDto;
import com.batteryrecycling.traceability.battery.BatteryService;
import com.batteryrecycling.traceability.common.api.IdGenerator;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import com.batteryrecycling.traceability.inbound.InboundDtos.InboundCreateRequest;
import com.batteryrecycling.traceability.inbound.InboundDtos.InboundResult;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InboundService {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final BatteryService batteryService;

    public InboundService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, IdempotencyService idempotencyService, AuditService auditService, BatteryService batteryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.batteryService = batteryService;
    }

    public List<BatteryDto> listPending(CurrentUser currentUser) {
        return jdbcTemplate.query("""
                SELECT id, enterprise_id, system_trace_code, original_code, battery_type, battery_model,
                       manufacturer, battery_chemistry, nominal_capacity, production_date,
                       current_responsible_enterprise_id, lifecycle_status, duplicate_status, version
                FROM battery
                WHERE enterprise_id = ? AND lifecycle_status = 'ACCEPTED_PENDING_INBOUND'
                ORDER BY updated_at, id
                """, this::mapBattery, currentUser.enterpriseId());
    }

    public InboundResult createInbound(CurrentUser currentUser, Long batteryId, InboundCreateRequest request, String idempotencyKey, HttpServletRequest servletRequest) {
        String canonical = "batteryId=" + batteryId + ";warehouseId=" + (request == null ? null : request.warehouseId()) + ";locationId=" + (request == null ? null : request.locationId());
        return idempotencyService.execute("CREATE_INBOUND", idempotencyKey, canonical, InboundResult.class,
                () -> createInboundInTransaction(currentUser, batteryId, request, servletRequest));
    }

    public InboundResult createInboundInTransaction(CurrentUser currentUser, Long batteryId, InboundCreateRequest request, HttpServletRequest servletRequest) {
        validateCreateRequest(currentUser, batteryId, request, servletRequest);
        BatteryDto battery = batteryService.requireBatteryForUpdate(currentUser, batteryId, servletRequest);
        if (!"ACCEPTED_PENDING_INBOUND".equals(battery.lifecycleStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_INVALID_BATTERY_STATE", "BATTERY", batteryId, "FAILED", "电池不是待入库状态", servletRequest);
            throw ApiException.conflict("INVALID_BATTERY_STATE", "只有验收通过待入库电池可以办理入库");
        }
        Warehouse warehouse = warehouse(currentUser, request.warehouseId(), servletRequest);
        Location location = location(currentUser, request.locationId(), servletRequest);
        if (!"ENABLED".equals(warehouse.enabledStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_WAREHOUSE_DISABLED", "WAREHOUSE", warehouse.id(), "FAILED", "仓库已停用", servletRequest);
            throw ApiException.badRequest("WAREHOUSE_DISABLED", "仓库已停用");
        }
        if (!"ENABLED".equals(location.enabledStatus())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_LOCATION_DISABLED", "WAREHOUSE_LOCATION", location.id(), "FAILED", "库位已停用", servletRequest);
            throw ApiException.badRequest("LOCATION_DISABLED", "库位已停用");
        }
        if (!warehouse.id().equals(location.warehouseId())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_LOCATION_WAREHOUSE_MISMATCH", "WAREHOUSE_LOCATION", location.id(), "FAILED", "库位不属于所选仓库", servletRequest);
            throw ApiException.badRequest("LOCATION_WAREHOUSE_MISMATCH", "库位不属于所选仓库");
        }
        LocalDateTime acceptedAt = latestPassAcceptedAt(currentUser, batteryId);
        if (acceptedAt == null) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_ACCEPTANCE_MISSING", "BATTERY", batteryId, "FAILED", "缺少验收通过记录", servletRequest);
            throw ApiException.conflict("INVALID_BATTERY_STATE", "缺少验收通过记录");
        }

        long inboundRecordId = idGenerator.nextId();
        long inventoryId = idGenerator.nextId();
        Long batchId = activeBatchId(currentUser.enterpriseId(), batteryId);
        try {
            jdbcTemplate.update("""
                    INSERT INTO inbound_record (
                      id, enterprise_id, inbound_no, battery_id, warehouse_id, location_id,
                      inbound_status, inbound_by, inbound_at, created_at, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 'EFFECTIVE', ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    inboundRecordId,
                    currentUser.enterpriseId(),
                    "IB-" + inboundRecordId,
                    batteryId,
                    warehouse.id(),
                    location.id(),
                    currentUser.id());
            jdbcTemplate.update("""
                    INSERT INTO inventory (
                      id, enterprise_id, battery_id, warehouse_id, location_id, inbound_record_id,
                      is_current, created_at, updated_at, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
                    """,
                    inventoryId,
                    currentUser.enterpriseId(),
                    batteryId,
                    warehouse.id(),
                    location.id(),
                    inboundRecordId);
            int updated = jdbcTemplate.update("""
                    UPDATE battery
                    SET lifecycle_status = 'IN_STOCK', updated_by = ?, updated_at = CURRENT_TIMESTAMP(3), version = version + 1
                    WHERE id = ? AND enterprise_id = ? AND lifecycle_status = 'ACCEPTED_PENDING_INBOUND'
                    """, currentUser.id(), batteryId, currentUser.enterpriseId());
            if (updated != 1) {
                throw ApiException.conflict("DUPLICATE_SUBMISSION", "电池已被其他请求入库");
            }
        } catch (DataIntegrityViolationException exception) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_DUPLICATE_SUBMISSION", "BATTERY", batteryId, "FAILED", "电池已经存在当前库存", servletRequest);
            throw ApiException.conflict("DUPLICATE_SUBMISSION", "电池已经存在当前库存");
        }

        LocalDateTime inboundAt = inboundAt(inboundRecordId);
        if (inboundAt.isBefore(acceptedAt)) {
            throw ApiException.badRequest("VALIDATION_FAILED", "入库时间不能早于验收通过时间");
        }
        batteryService.insertLifecycle(currentUser, batteryId, batchId, "INBOUND_COMPLETED", "入库完成", "ACCEPTED_PENDING_INBOUND", "IN_STOCK", servletRequest);
        auditService.recordSuccess(currentUser.enterpriseId(), currentUser.id(), "INBOUND_COMPLETED", "INBOUND_RECORD", inboundRecordId, "inventoryId=" + inventoryId, servletRequest);
        return new InboundResult(inboundRecordId, inventoryId, "IN_STOCK");
    }

    public void rejectDelete(CurrentUser currentUser, Long inboundRecordId, HttpServletRequest servletRequest) {
        if (inboundRecordId == null) {
            throw ApiException.notFound("入库记录不存在");
        }
        List<Long> enterprises = jdbcTemplate.queryForList("SELECT enterprise_id FROM inbound_record WHERE id = ?", Long.class, inboundRecordId);
        if (enterprises.isEmpty()) {
            throw ApiException.notFound("入库记录不存在");
        }
        if (!enterprises.get(0).equals(currentUser.enterpriseId())) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_RECORD_CROSS_ENTERPRISE_DENIED", "INBOUND_RECORD", inboundRecordId, "FORBIDDEN", "拒绝跨企业入库记录访问", servletRequest);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的入库记录");
        }
        auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_RECORD_DELETE_FORBIDDEN", "INBOUND_RECORD", inboundRecordId, "FAILED", "生效入库记录不允许删除", servletRequest);
        throw ApiException.conflict("EFFECTIVE_RECORD_DELETE_FORBIDDEN", "生效入库记录不允许删除");
    }

    private void validateCreateRequest(CurrentUser currentUser, Long batteryId, InboundCreateRequest request, HttpServletRequest servletRequest) {
        if (request == null || request.warehouseId() == null || request.locationId() == null) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "INBOUND_VALIDATION_FAILED", "BATTERY", batteryId, "FAILED", "仓库和库位不能为空", servletRequest);
            throw ApiException.badRequest("VALIDATION_FAILED", "仓库和库位不能为空");
        }
    }

    private Warehouse warehouse(CurrentUser currentUser, Long warehouseId, HttpServletRequest request) {
        List<Warehouse> rows = jdbcTemplate.query("""
                SELECT id, enterprise_id, warehouse_code, warehouse_name, enabled_status
                FROM warehouse
                WHERE id = ? AND enterprise_id = ?
                """, this::mapWarehouse, warehouseId, currentUser.enterpriseId());
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        if (exists("warehouse", warehouseId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "WAREHOUSE_CROSS_ENTERPRISE_DENIED", "WAREHOUSE", warehouseId, "FORBIDDEN", "拒绝跨企业仓库访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的仓库");
        }
        throw new ApiException("WAREHOUSE_NOT_FOUND", "仓库不存在", HttpStatus.NOT_FOUND);
    }

    private Location location(CurrentUser currentUser, Long locationId, HttpServletRequest request) {
        List<Location> rows = jdbcTemplate.query("""
                SELECT id, enterprise_id, warehouse_id, location_code, enabled_status
                FROM warehouse_location
                WHERE id = ? AND enterprise_id = ?
                """, this::mapLocation, locationId, currentUser.enterpriseId());
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        if (exists("warehouse_location", locationId)) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "LOCATION_CROSS_ENTERPRISE_DENIED", "WAREHOUSE_LOCATION", locationId, "FORBIDDEN", "拒绝跨企业库位访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的库位");
        }
        throw new ApiException("LOCATION_NOT_FOUND", "库位不存在", HttpStatus.NOT_FOUND);
    }

    private LocalDateTime latestPassAcceptedAt(CurrentUser currentUser, Long batteryId) {
        return jdbcTemplate.queryForObject("""
                SELECT MAX(accepted_at)
                FROM acceptance_record
                WHERE enterprise_id = ? AND battery_id = ? AND acceptance_result = 'PASS'
                """, LocalDateTime.class, currentUser.enterpriseId(), batteryId);
    }

    private LocalDateTime inboundAt(Long inboundRecordId) {
        return jdbcTemplate.queryForObject("SELECT inbound_at FROM inbound_record WHERE id = ?", LocalDateTime.class, inboundRecordId);
    }

    private Long activeBatchId(Long enterpriseId, Long batteryId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT batch_id
                FROM recycle_batch_battery
                WHERE enterprise_id = ? AND battery_id = ? AND relation_status = 'ACTIVE'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, Long.class, enterpriseId, batteryId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean exists(String tableName, Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private BatteryDto mapBattery(ResultSet rs, int rowNum) throws SQLException {
        return new BatteryDto(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("system_trace_code"),
                rs.getString("original_code"),
                rs.getString("battery_type"),
                rs.getString("battery_model"),
                rs.getString("manufacturer"),
                rs.getString("battery_chemistry"),
                rs.getBigDecimal("nominal_capacity"),
                rs.getObject("production_date", LocalDate.class),
                rs.getLong("current_responsible_enterprise_id"),
                rs.getString("lifecycle_status"),
                rs.getString("duplicate_status"),
                rs.getInt("version"));
    }

    private Warehouse mapWarehouse(ResultSet rs, int rowNum) throws SQLException {
        return new Warehouse(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getString("warehouse_code"),
                rs.getString("warehouse_name"),
                rs.getString("enabled_status"));
    }

    private Location mapLocation(ResultSet rs, int rowNum) throws SQLException {
        return new Location(
                rs.getLong("id"),
                rs.getLong("enterprise_id"),
                rs.getLong("warehouse_id"),
                rs.getString("location_code"),
                rs.getString("enabled_status"));
    }

    private record Warehouse(Long id, Long enterpriseId, String warehouseCode, String warehouseName, String enabledStatus) {
    }

    private record Location(Long id, Long enterpriseId, Long warehouseId, String locationCode, String enabledStatus) {
    }
}
