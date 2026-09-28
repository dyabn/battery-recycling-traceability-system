package com.batteryrecycling.traceability.tenant;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_ENTERPRISE_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setEnterpriseId(Long enterpriseId) {
        CURRENT_ENTERPRISE_ID.set(enterpriseId);
    }

    public static Long requireEnterpriseId() {
        Long enterpriseId = CURRENT_ENTERPRISE_ID.get();
        if (enterpriseId == null) {
            throw new IllegalStateException("Tenant context is not initialized.");
        }
        return enterpriseId;
    }

    public static void clear() {
        CURRENT_ENTERPRISE_ID.remove();
    }
}

