package com.example.patientvault.identity.internal;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.crypto.CryptoService;
import com.example.patientvault.shared.DoctorContact;

/**
 * The single doctor's notification address. Stored encrypted with the application's secret key (it is personal data
 * of the doctor); until one is saved, the deployment default {@code APP_DOCTOR_NOTIFICATION_EMAIL} is used.
 * Background jobs call this without a logged-in user, so the account is the only (single-doctor) user row.
 */
@Service
class DoctorContactService implements DoctorContact {

    private final DoctorUserRepository users;
    private final CryptoService crypto;
    private final String defaultAddress;

    DoctorContactService(DoctorUserRepository users, CryptoService crypto,
                         @Value("${app.doctor.notification-email:}") String defaultAddress) {
        this.users = users;
        this.crypto = crypto;
        this.defaultAddress = defaultAddress == null ? "" : defaultAddress.strip();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> notificationEmail() {
        Optional<String> stored = users.findAll().stream().findFirst()
                .filter(u -> u.getNotificationEmailEnc() != null)
                .map(u -> crypto.decryptSecretText(context(u), u.getNotificationEmailEnc()));
        if (stored.isPresent()) {
            return stored;
        }
        return defaultAddress.isEmpty() ? Optional.empty() : Optional.of(defaultAddress);
    }

    @Override
    @Transactional
    public void updateNotificationEmail(String email) {
        DoctorUser user = users.findAll().stream().findFirst().orElseThrow();
        user.setNotificationEmailEnc(crypto.encryptSecretText(context(user), email));
    }

    private static String context(DoctorUser user) {
        return "notification-email:" + user.getId();
    }
}
