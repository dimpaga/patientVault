package com.example.patientvault.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the doctor account on first start from {@code APP_BOOTSTRAP_USERNAME} /
 * {@code APP_BOOTSTRAP_PASSWORD}, only when no user exists. The account must change the password at
 * first login. Credentials are never logged.
 */
@Component
class BootstrapUserInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapUserInitializer.class);

    private final DoctorUserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final BootstrapProperties properties;

    BootstrapUserInitializer(DoctorUserRepository users, PasswordEncoder encoder, PasswordPolicy policy,
                             BootstrapProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String username = AppUserDetailsService.normalizeUsername(properties.getUsername());
        String password = properties.getPassword();
        if (username.isEmpty() || password == null || password.isBlank()) {
            LOG.warn("No user exists and APP_BOOTSTRAP_USERNAME / APP_BOOTSTRAP_PASSWORD are not set: nobody can log in");
            return;
        }
        if (!policy.violations(password, username).isEmpty()) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_PASSWORD does not satisfy the password policy (at least "
                            + PasswordPolicy.MIN_LENGTH + " characters, not a common password, not containing the username)");
        }
        DoctorUser user = users.save(new DoctorUser(username, encoder.encode(password)));
        LOG.info("Bootstrap doctor account created (id={}); a password change is required at first login", user.getId());
    }
}
