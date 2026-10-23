package com.example.patientvault.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

    /** UTC clock; display and scheduling zones are applied from {@code app.timezone} where needed. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
