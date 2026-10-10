-- Dev-only demo warehouse and location data. This location is loaded only by the dev profile.

INSERT INTO warehouse (id, enterprise_id, warehouse_code, warehouse_name, enabled_status, created_by, created_at, updated_by, updated_at, version)
VALUES
(20101, 1, 'WH-101', 'A企业启用仓库', 'ENABLED', 4, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0),
(20102, 1, 'WH-102', 'A企业停用仓库', 'DISABLED', 4, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0),
(20201, 2, 'WH-201', 'B企业启用仓库', 'ENABLED', 6, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO warehouse_location (id, enterprise_id, warehouse_id, location_code, enabled_status, created_by, created_at, updated_by, updated_at, version)
VALUES
(21101, 1, 20101, 'WH-101-A01-R01-L01', 'ENABLED', 4, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0),
(21102, 1, 20101, 'WH-101-A01-R01-L02', 'DISABLED', 4, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0),
(21103, 1, 20102, 'WH-102-A01-R01-L01', 'ENABLED', 4, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0),
(21201, 2, 20201, 'WH-201-A01-R01-L01', 'ENABLED', 6, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3), 0)
ON DUPLICATE KEY UPDATE
  id = id;
