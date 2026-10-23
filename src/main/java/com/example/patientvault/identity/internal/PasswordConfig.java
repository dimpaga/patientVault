package com.example.patientvault.identity.internal;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class PasswordConfig {

    static final String ENCODING_ID = "argon2";

    /**
     * Argon2id with Spring Security's recommended parameters, behind a {@link DelegatingPasswordEncoder}
     * so the algorithm or its parameters can be upgraded later without invalidating stored hashes.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(ENCODING_ID,
                Map.of(ENCODING_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
