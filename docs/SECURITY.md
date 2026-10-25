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

## 3. Logging (implemented: step 3, light version)

- **Request id:** `RequestLoggingFilter` runs before Spring Security, assigns a UUID (or accepts a
  client-supplied `X-Request-Id` that matches `[A-Za-z0-9._-]{8,64}`, so it cannot be used for log
  injection), returns it in the `X-Request-Id` response header and shows it on the error pages as the
  reference code.
- **Context on every log line (MDC):** `requestId`, `remoteIp`, `userId` (the user's UUID, never the
  name); audit lines add `action` and `module`.
- **Access log** (logger `access`): one line per request with method, **route template** (for example
  `/patients/{id}`), status, duration and response bytes (counted while streaming, nothing is buffered).
  No query string, no bodies, no headers. Requests that never reached a handler are logged with their
  path where UUID/number segments are masked as `{id}`; odd paths become `UNMAPPED`. Health probes are
  logged at DEBUG only.
- **Streams:** `access`, `security` (login, MFA, lockout, rate limit, CSRF, access denied, session
  timeout, re-authentication) and `audit` (business actions). They are currently written to the log;
  the append-only audit table and audit page are still to come.
- **Format:** plain text locally (`level [requestId,userId] ...`), structured JSON (Elastic Common Schema)
  with the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`). Hibernate SQL and bind logging are off.
- **Not yet done:** sanitising exception messages in error logs and the automated log-hygiene test
  (hardening step); both matter once patient data exists.

## 4. Patient data (implemented: step 4)

- **At rest:** `patient` and `visit` rows hold only ciphertext (`*_enc`) plus the wrapped data key; names,
  e-mail, phone, AMKA, referral source, notes, visit reasons, diagnoses and treatments are encrypted per
  field by `PatientCodec` (the only class that sees both forms). Verified by dumping the tables and
  searching for sentinel values (plain and hex): none found.
  Not encrypted, by design: the visit date and the visit type (doctor visit or surgery).
- **Search tokens:** `patient_search_token` holds HMAC-SHA256 tokens only (AMKA, e-mail, mobile, name
  prefixes 3..15). A unique index on AMKA tokens enforces one patient per AMKA without the database
  knowing the number. Tokens are rebuilt in the same transaction when a searchable field changes.
- **Validation:** server-side and authoritative (e-mail syntax, libphonenumber E.164 for mobiles, 11-digit
  Luhn-valid AMKA, length limits, no future visit dates). Messages never echo the typed value.
- **Authorisation and IDOR:** every service method requires `PATIENT_READ` / `PATIENT_WRITE`; patients must
  be `ACTIVE`; a visit is only reachable through its own patient (404 otherwise).
- **Concurrency:** optimistic locking (`version`) on patients and visits; a stale save shows a conflict
  message and keeps the user's input.
- **Audit/logging:** `PATIENT_CREATED/VIEWED/UPDATED` and `VISIT_*` record ids and the *names* of changed
  fields only. Entities and records that hold personal data override `toString()`. Checked: none of the
  sentinel values appear in any log.

## 5. Search (implemented: step 5)

- One search box; the query is classified (11 digits -> AMKA, "@" -> e-mail, mostly digits -> mobile via
  libphonenumber, otherwise name words) and matched against HMAC blind-index tokens only. The database
  never sees the typed text.
- **Never logged:** the query text. The access log records the route template without the query string and
  `PATIENT_SEARCHED` records only the query type and the number of results. Verified by searching for
  distinctive values and grepping the logs.
- Known limits (also shown in the UI help text): exact AMKA/e-mail/mobile only, name words need at least
  3 letters, blind indexes reveal equality and prefix patterns to someone with database access but not the
  values.
- Only ACTIVE patients are returned; results are decrypted in memory, one page at a time.

## 6. Medical files (implemented: step 6)

- **At rest:** file content is stream-encrypted (AES256_GCM_HKDF_1MB, key derived from the patient data key,
  file id and purpose) before it leaves the JVM; names are encrypted fields. CouchDB documents hold only
  technical fields (type, patientId, keyId, cipher, size, time). Verified by searching raw PostgreSQL and CouchDB
  for sentinel names/content (plain and base64): nothing found.
- **Upload:** one file per request; content sniffed with Tika against an allow-list; HTML, SVG, EXE, scripts and
  extension/content mismatches are rejected before anything is stored; size limit 100 MB (413); names and relative
  paths sanitised; failed uploads leave no blob or row.
- **Download:** streamed through the app after authentication, authorisation and the patient/file ownership
  check; no public or pre-signed URLs; `Content-Disposition: attachment` except images/PDF (inline), PDFs with
  `Content-Security-Policy: sandbox`, `nosniff` and `no-store` on every response.
- **Integrity:** a modified ciphertext byte makes the download fail (500, no content sent) and is audited as
  `FILE_INTEGRITY_FAILURE`.
- **Availability:** if CouchDB is down, uploads and downloads answer 503, nothing half-written remains, the
  listing still works (it comes from PostgreSQL) and readiness reports DOWN until CouchDB returns.

## 7. Appointments and e-mail reminders (implemented: step 8)

- **What an e-mail contains:** the date and time, the patient name (or initials with `privacy-mode: INITIALS`)
  and a link that needs a login. Never AMKA, phone, e-mail, diagnosis, notes, the appointment reason or file
  names: the composer only receives the name, the time and the id, so nothing else can leak into a mail.
  The `.ics` attachment holds the same data. Verified against Mailpit.
- **Reason at rest:** the appointment reason is an encrypted field bound to the patient and the appointment.
  Audit entries and logs contain ids only.
- **Transport:** SMTP with STARTTLS required (`mail.smtp.starttls.required=true`); only the local profile turns it
  off, for the Mailpit sink. Credentials come from `SMTP_*` environment variables. Connection and read timeouts
  are 5 and 10 seconds. Failures keep only the exception class (the message could contain the address).
- **Recipient:** the doctor's notification address is stored encrypted in `app_user.notification_email_enc`.
  Changing it needs a recent login (it redirects patient names to a new mailbox) and is audited; the address is
  validated against header injection.
- **Scheduler:** one transaction per reminder with `FOR UPDATE SKIP LOCKED`, so no reminder is sent twice by
  concurrent runs or instances. Jobs run as actor `SYSTEM` with only `PATIENT_READ`. Delivery is at-least-once:
  a crash between the SMTP hand-over and the commit sends that one reminder again after the restart.
- **Deep link:** `/appointments/{id}` is a normal authenticated route; the e-mail itself carries no session or token.

## 8. Secrets handling

- Secrets only via environment variables; `.env`, `.dev-secrets.env` are git-ignored.
- `./dev run` generates local development keys and a local doctor account into `.dev-secrets.env`
  (it prints the initial password once). Never reuse them elsewhere.
- Configuration classes holding secrets have no `toString()` and no bean-validation constraints so
  values cannot leak through logs or binding errors.
