package com.example.patientvault.crypto.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Raw crypto configuration. Deliberately has no {@code toString()} and no bean-validation
 * constraints so that key material can never end up in logs or binding error messages;
 * validation is done in {@link EnvKeyProvider}.
 */
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    /** Key ring in the form {@code v1:<base64>[,v2:<base64>]}. */
    private String masterKeys;

    private String activeKeyId;

    /** Optional; defaults to the first key id in {@link #masterKeys}. */
    private String blindIndexKeyId;

    public String getMasterKeys() {
        return masterKeys;
    }

    public void setMasterKeys(String masterKeys) {
        this.masterKeys = masterKeys;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public String getBlindIndexKeyId() {
        return blindIndexKeyId;
    }

    public void setBlindIndexKeyId(String blindIndexKeyId) {
        this.blindIndexKeyId = blindIndexKeyId;
    }
}
