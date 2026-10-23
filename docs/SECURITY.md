# Security notes

Living document; sections are added as the corresponding steps of the spec are implemented.
Authoritative requirements: `PatientVault_Spec_v1.md` sections 6 and 7.

## 1. Encryption model (implemented: step 1)

PostgreSQL and CouchDB are treated as **untrusted storage**. Everything personal or medical is
encrypted inside the JVM before it is stored.

```
APP_MASTER_KEYS  (env secret, versioned "v1:<base64>,v2:<base64>", each >= 256 bit)
   | HKDF-SHA256, distinct info labels, per master key
   +-- KEK              AES-256-GCM, wraps each patient's DEK
   +-- USER_SECRET_KEY  AES-256-GCM, doctor-level secrets (TOTP secret, notification email)
   +-- BLIND_INDEX_KEY  HMAC-SHA256 search tokens (derived from ONE configured master key id)

Per patient: random 256-bit DEK, stored only wrapped by the KEK
   +-- fields           AES-256-GCM, AAD = patientId || fieldName
   +-- file content     Tink streaming AEAD AES256_GCM_HKDF_1MB, key = HKDF(DEK, salt = fileId, info = purpose),
                        AAD = patientId || fileId || purpose
```

- Code: package `com.example.patientvault.crypto`; the only public entry points are `CryptoService`,
  `BlindIndexer`, `KeyProvider` and the DTO/exception types in the package root. Implementations are in
  `crypto.internal`. All primitives come from Google Tink; there is no hand-rolled cryptography.
- **Ciphertext layout:** `version(1) | keyIdLength(1) | keyId | nonce | ciphertext+tag`. The header is
  part of the AAD, so changing version or key id makes decryption fail.
- **Tamper / transplant protection:** a modified byte, or a ciphertext moved to another patient, field,
  file or purpose, raises `IntegrityFailureException` (callers must audit this, see spec 6.4).
- **Crypto-shredding:** deleting a patient's wrapped DEK makes every ciphertext of that patient
  unreadable, including copies in backups and replicas. `CryptoService.forgetDataKey` clears the
  in-memory copy (cache TTL 5 min, max 1024 entries).
- **Fail-fast startup:** the application does not start if `APP_MASTER_KEYS` or `APP_ACTIVE_KEY_ID` is
  missing, malformed, refers to an unknown key id, or any key is shorter than 256 bits. Error messages
  never contain key material. Readiness (`/actuator/health/readiness`) includes a key-ring self-check.
- **Key rotation:** add `v2` to `APP_MASTER_KEYS` and switch `APP_ACTIVE_KEY_ID`; old keys stay available
  for decryption. `CryptoService.rewrapDataKey` re-wraps a DEK under the active KEK (the admin-triggered
  job that iterates patients arrives with the patient module).
- **Blind index key:** derived from `APP_BLIND_INDEX_KEY_ID` (default: the first key in
  `APP_MASTER_KEYS`), so rotating the active KEK does not invalidate search tokens. Changing the blind
  index key id requires re-indexing all search tokens.
- **Losing every copy of the master key is unrecoverable data loss.** Keep an offline copy.

## 2. Secrets handling

- Secrets only via environment variables; `.env`, `.dev-secrets.env` are git-ignored.
- `./dev run` generates local development keys into `.dev-secrets.env`. Never reuse them elsewhere.
- Configuration classes holding secrets have no `toString()` and no bean-validation constraints so
  values cannot leak through logs or binding errors.
