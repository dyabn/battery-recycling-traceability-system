package com.batteryrecycling.traceability.inventory;

import com.batteryrecycling.traceability.common.security.CurrentUser;
import com.batteryrecycling.traceability.inventory.InventoryDtos.InventoryItemDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InventoryItemDto> listCurrent(CurrentUser currentUser, String systemTraceCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.enterprise_id, i.battery_id, b.system_trace_code,
                       i.warehouse_id, w.warehouse_code, w.warehouse_name,
                       i.location_id, l.location_code, i.inbound_record_id, r.inbound_at
                FROM inventory i
                JOIN battery b ON b.id = i.battery_id
                JOIN warehouse w ON w.id = i.warehouse_id
                JOIN warehouse_location l ON l.id = i.location_id
                JOIN inbound_record r ON r.id = i.inbound_record_id
                WHERE i.enterprise_id = ? AND i.is_current = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(currentUser.enterpriseId());
        if (systemTraceCode != null && !systemTraceCode.isBlank()) {
            sql.append(" AND b.system_trace_code LIKE ?");
            args.add("%" + systemTraceCode.trim() + "%");
        }
        sql.append(" ORDER BY r.inbound_at DESC, i.id DESC");
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new InventoryItemDto(
                        rs.getLong("id"),
                        rs.getLong("enterprise_id"),
                        rs.getLong("battery_id"),
                        rs.getString("system_trace_code"),
                        rs.getLong("warehouse_id"),
                        rs.getString("warehouse_code"),
                        rs.getString("warehouse_name"),
                        rs.getLong("location_id"),
                        rs.getString("location_code"),
                        rs.getLong("inbound_record_id"),
                        rs.getObject("inbound_at", java.time.LocalDateTime.class)),
                args.toArray());
    }
}
