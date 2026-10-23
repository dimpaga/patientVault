package com.example.patientvault.records.blob;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the CouchDB client(s), the {@link BlobStore} adapter and the initializer. Fails fast on missing secrets. */
@Configuration(proxyBeanMethods = false)
class CouchDbConfig {

    @Bean
    CouchDbBlobStore blobStore(CouchDbProperties properties) {
        require(properties.appPassword(), "COUCHDB_APP_PASSWORD");
        require(properties.adminPassword(), "COUCHDB_ADMIN_PASSWORD");
        CouchDbClient app = new CouchDbClient(properties.url(), properties.appUser(), properties.appPassword());
        CouchDbClient admin = new CouchDbClient(properties.url(), properties.adminUser(), properties.adminPassword());
        return new CouchDbBlobStore(app, admin, properties.database());
    }

    @Bean
    CouchDbInitializer couchDbInitializer(CouchDbProperties properties) {
        require(properties.adminPassword(), "COUCHDB_ADMIN_PASSWORD");
        return new CouchDbInitializer(properties,
                new CouchDbClient(properties.url(), properties.adminUser(), properties.adminPassword()));
    }

    private static void require(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is not set");
        }
    }
}
