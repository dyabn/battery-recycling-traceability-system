package com.batteryrecycling.traceability.common.api;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {
    private final IdentifierGenerator identifierGenerator = new DefaultIdentifierGenerator();

    public long nextId() {
        return identifierGenerator.nextId(null).longValue();
    }
}
