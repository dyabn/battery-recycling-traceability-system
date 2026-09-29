-- I2 implementation guard.
-- Ensures one battery can belong to only one active recycle batch relation at a time.

ALTER TABLE recycle_batch_battery
  ADD COLUMN active_battery_id BIGINT
    GENERATED ALWAYS AS (CASE WHEN relation_status = 'ACTIVE' THEN battery_id ELSE NULL END) STORED,
  ADD CONSTRAINT uk_rbb_active_battery UNIQUE (active_battery_id);
