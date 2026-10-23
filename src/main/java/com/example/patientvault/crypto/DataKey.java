package com.example.patientvault.crypto;

import java.util.UUID;

/** Opaque handle to a patient's unwrapped data-encryption key. Never serialise or log its contents. */
public interface DataKey {

    /** Id of the patient (or other owner) this key belongs to; bound into all ciphertext as AAD. */
    UUID ownerId();
}
