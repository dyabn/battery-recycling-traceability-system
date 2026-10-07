package com.batteryrecycling.traceability.battery;

import com.batteryrecycling.traceability.idempotency.IdempotencyService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class BatteryServiceTest {

    @Test
    void originalCodeLockNameIsStableBoundedAndTenantIsolated() {
        BatteryService service = new BatteryService(
                null,
                null,
                new IdempotencyService(null, null, null, null),
                null);

        String enterpriseMax = service.originalCodeLockName(Long.MAX_VALUE, "ORI-I2-LOCK");
        String same = service.originalCodeLockName(Long.MAX_VALUE, "ORI-I2-LOCK");
        String differentEnterprise = service.originalCodeLockName(1L, "ORI-I2-LOCK");

        Assertions.assertThat(enterpriseMax).hasSizeLessThanOrEqualTo(64);
        Assertions.assertThat(enterpriseMax).hasSize(64);
        Assertions.assertThat(enterpriseMax).isEqualTo(same);
        Assertions.assertThat(differentEnterprise).isNotEqualTo(enterpriseMax);
    }
}
