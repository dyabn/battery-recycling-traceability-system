package com.batteryrecycling.traceability.common.api;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {
    private final AtomicInteger sequence = new AtomicInteger();

    public long nextId() {
        int value = sequence.updateAndGet(current -> current >= 999 ? 1 : current + 1);
        return Instant.now().toEpochMilli() * 1000 + value;
    }
}

