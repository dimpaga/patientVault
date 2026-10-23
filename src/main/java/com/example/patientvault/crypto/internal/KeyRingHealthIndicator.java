package com.example.patientvault.crypto.internal;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness contributor ("keyRing"): verifies every derived key can still encrypt and decrypt. Exposes no details. */
@Component("keyRingHealthIndicator")
class KeyRingHealthIndicator implements HealthIndicator {

    private final KeyRing keyRing;

    KeyRingHealthIndicator(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    @Override
    public Health health() {
        return keyRing.isHealthy() ? Health.up().build() : Health.down().build();
    }
}
