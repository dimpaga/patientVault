package com.example.patientvault.identity.internal;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

/** Feeds failed password attempts into the account lockout counter. */
@Component
class LoginFailureListener {

    private final UserAccountService accounts;

    LoginFailureListener(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener
    void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        accounts.recordFailedLogin(AppUserDetailsService.normalizeUsername(event.getAuthentication().getName()));
    }
}
