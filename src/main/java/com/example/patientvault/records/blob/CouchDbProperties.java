package com.example.patientvault.records.blob;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CouchDB connection. The application works with the dedicated non-admin user; the admin credentials are
 * used only by the initializer to create the database and that user. No {@code toString()} so passwords
 * can never be printed.
 *
 * @param url         base URL (use {@code https://...} when CouchDB offers TLS)
 * @param database    database that holds one document per file
 * @param adminUser   server admin, used only at start-up
 * @param appUser     the account the application uses at run time (member of the database only)
 */
@ConfigurationProperties(prefix = "app.couchdb")
public record CouchDbProperties(
        @DefaultValue("http://localhost:5984") String url,
        @DefaultValue("patient_files") String database,
        @DefaultValue("admin") String adminUser,
        String adminPassword,
        @DefaultValue("patientvault_app") String appUser,
        String appPassword) {

    @Override
    public String toString() {
        return "CouchDbProperties[url=" + url + ", database=" + database + "]";
    }
}
