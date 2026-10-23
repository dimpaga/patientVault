package com.example.patientvault.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** First-start account. No {@code toString()} and no validation annotations so the password never leaks. */
@ConfigurationProperties(prefix = "app.bootstrap")
public class BootstrapProperties {

    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
