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

## 2. Authentication and sessions (implemented: step 2)

- **Account:** one doctor account (`ROLE_DOCTOR`), created at first start from `APP_BOOTSTRAP_USERNAME` /
  `APP_BOOTSTRAP_PASSWORD` only when no user exists; there is no public registration. The password must
  be changed before anything else works. Bootstrap credentials are never logged.
- **Passwords:** Argon2id (Spring Security recommended parameters) behind a `DelegatingPasswordEncoder`;
  policy: at least 12 characters, not in the common-password list, must not contain the username.
- **Two-step login:** password, then TOTP (RFC 6238, +/-1 step, replay-protected) or a single-use recovery
  code. Until the second step passes the session is anonymous (only a 5-minute `PendingMfa` marker
  exists). The TOTP secret is stored encrypted with the user-secret key; the 10 recovery codes only as
  Argon2id hashes and are shown once.
- **Brute-force protection:** progressive account lockout (15 min, doubling, max 8 h) counted under a row
  lock; per-IP token-bucket rate limit (10 requests/min) on `POST /login` and `POST /login/mfa`; generic
  error messages that do not reveal whether the user exists.
- **Sessions:** server-side, `pv_session` cookie `HttpOnly`, `Secure`, `SameSite=Strict`; new session id
  after the password step and again after the second factor; 15 min idle timeout, 8 h absolute timeout;
  logout invalidates the session. Expired sessions land on the login page with a message (htmx requests
  get 401 + `HX-Redirect`).
- **Sensitive actions:** `@RequiresRecentAuth` sends the user through `/reauth` (password + TOTP) unless
  they authenticated within `app.security.reauth-window` (5 min). Applied to password change and
  recovery-code regeneration; patient deletion and audit export will use it too.
- **Authorisation:** method security with permission authorities (`PATIENT_READ` ... `SETTINGS_MANAGE`);
  `RolePermissions` is the only place that maps roles to permissions.
- **Web protections:** CSRF on every state-changing request (htmx sends `X-CSRF-TOKEN` from a wrapper that
  is refreshed on every boosted navigation); CSP `default-src 'self'` without inline script/style, HSTS,
  `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP/CORP `same-origin`, no framing;
  `Cache-Control: no-store` on dynamic responses; post-login redirects accept same-site relative paths only.
- **Privacy in logs:** audit events carry ids and non-personal context only; failed logins are recorded as
  `ANONYMOUS` without the attempted username.

## 3. Secrets handling

- Secrets only via environment variables; `.env`, `.dev-secrets.env` are git-ignored.
- `./dev run` generates local development keys and a local doctor account into `.dev-secrets.env`
  (it prints the initial password once). Never reuse them elsewhere.
- Configuration classes holding secrets have no `toString()` and no bean-validation constraints so
  values cannot leak through logs or binding errors.
