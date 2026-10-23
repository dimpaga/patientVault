package com.example.patientvault.identity.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Clock;

/** Bookkeeping on the doctor account around authentication. */
@Service
class UserAccountService {

    private final DoctorUserRepository users;
    private final Clock clock;

    UserAccountService(DoctorUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    void recordLoginSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> user.setLastLoginAt(clock.instant()));
    }
}
