package com.example.patientvault.records.blob;

import java.util.UUID;

/** Opaque object keys (spec 5.3.2): UUIDs and fixed words only, never a name. */
public final class StorageKeys {

    private StorageKeys() {
    }

    public static String content(UUID patientId, UUID fileId) {
        return "p/" + patientId + "/f/" + fileId + "/content";
    }

    public static String thumbnail(UUID patientId, UUID fileId) {
        return "p/" + patientId + "/f/" + fileId + "/thumb";
    }
}
