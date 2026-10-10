package com.batteryrecycling.traceability.warehouse;

import com.batteryrecycling.traceability.audit.AuditService;
import com.batteryrecycling.traceability.common.exception.ApiException;
import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.warehouse.WarehouseDtos.WarehouseDto;
import com.batteryrecycling.traceability.warehouse.WarehouseDtos.WarehouseLocationDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WarehouseService {
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public WarehouseService(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<WarehouseDto> listWarehouses(CurrentUser currentUser) {
        return jdbcTemplate.query("""
                SELECT id, enterprise_id, warehouse_code, warehouse_name, enabled_status
                FROM warehouse
                WHERE enterprise_id = ? AND enabled_status = 'ENABLED'
                ORDER BY warehouse_code, id
                """,
                (rs, rowNum) -> new WarehouseDto(
                        rs.getLong("id"),
                        rs.getLong("enterprise_id"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"),
                        rs.getString("enabled_status")),
                currentUser.enterpriseId());
    }

    public List<WarehouseLocationDto> listLocations(CurrentUser currentUser, Long warehouseId, HttpServletRequest request) {
        WarehouseDto warehouse = warehouse(currentUser, warehouseId, request);
        if (!"ENABLED".equals(warehouse.enabledStatus())) {
            throw ApiException.badRequest("WAREHOUSE_DISABLED", "仓库已停用");
        }
        return jdbcTemplate.query("""
                SELECT id, enterprise_id, warehouse_id, location_code, enabled_status
                FROM warehouse_location
                WHERE enterprise_id = ? AND warehouse_id = ? AND enabled_status = 'ENABLED'
                ORDER BY location_code, id
                """,
                (rs, rowNum) -> new WarehouseLocationDto(
                        rs.getLong("id"),
                        rs.getLong("enterprise_id"),
                        rs.getLong("warehouse_id"),
                        rs.getString("location_code"),
                        rs.getString("enabled_status")),
                currentUser.enterpriseId(), warehouse.id());
    }

    private WarehouseDto warehouse(CurrentUser currentUser, Long warehouseId, HttpServletRequest request) {
        List<WarehouseDto> warehouses = jdbcTemplate.query("""
                SELECT id, enterprise_id, warehouse_code, warehouse_name, enabled_status
                FROM warehouse
                WHERE id = ? AND enterprise_id = ?
                """,
                (rs, rowNum) -> new WarehouseDto(
                        rs.getLong("id"),
                        rs.getLong("enterprise_id"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"),
                        rs.getString("enabled_status")),
                warehouseId, currentUser.enterpriseId());
        if (!warehouses.isEmpty()) {
            return warehouses.get(0);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse WHERE id = ?", Integer.class, warehouseId);
        if (count != null && count > 0) {
            auditService.recordRejected(currentUser.enterpriseId(), currentUser.id(), "WAREHOUSE_CROSS_ENTERPRISE_DENIED", "WAREHOUSE", warehouseId, "FORBIDDEN", "拒绝跨企业仓库访问", request);
            throw ApiException.forbidden("CROSS_ENTERPRISE_ACCESS_DENIED", "不能访问其他企业的仓库");
        }
        throw ApiException.notFound("仓库不存在");
    }
}
