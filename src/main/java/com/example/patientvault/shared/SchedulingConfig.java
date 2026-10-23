package com.example.patientvault.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs (reminders, erasure retry, reconciliation, housekeeping). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
