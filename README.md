# PatientVault

Web application for a single doctor to store, search and view patient personal and medical data
(photos, PDFs, DICOM files and folders). Personal and medical data is encrypted at application level
before it reaches PostgreSQL or the Amazon S3 file store. The full design and requirements are in
[PatientVault_Spec_v2.md](PatientVault_Spec_v2.md); deviations and decisions are in
[docs/DECISIONS.md](docs/DECISIONS.md).

**Stack:** Java 25, Spring Boot 4.1, Thymeleaf + HTMX, PostgreSQL 17, Amazon S3 (file content), Flyway.

## Status

Implementation follows spec section 14. Done so far: step 0 (foundation), step 1 (crypto kernel),
step 2 (identity and UI shell), a light step 3 (request ids, structured logs, access log; the audit
table and page are still open) step 4 (patients and visits: create, edit, view, visits tab), step 5 (search),
step 6 (medical files and folders; content in Amazon S3, metadata in PostgreSQL) and step 8 (appointments, e-mail reminders, daily agenda).
Step 7 (erasure) is postponed. Automated tests are deferred.

Reminder e-mails go through SMTP: set `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`,
`APP_PUBLIC_BASE_URL` and optionally `APP_DOCTOR_NOTIFICATION_EMAIL` in production. Locally, mails go to Mailpit
(http://localhost:8025).

## Prerequisites

- JDK 25 (`JAVA_HOME` must point to it; the Maven wrapper is used, no local Maven needed)
- Docker (for PostgreSQL, an S3-compatible emulator and Mailpit)

## Run locally

```bash
./dev up      # start PostgreSQL, the S3 emulator (versioned bucket) and Mailpit (SMTP sink UI: http://localhost:8025)
./dev run     # start the app with the "local" profile on http://localhost:8080
./dev down    # stop the services (./dev reset also removes their data volumes)
```

Health: `http://localhost:8080/actuator/health/readiness`.

### File storage (Amazon S3)

Encrypted file content and thumbnails live in a private S3 bucket under UUID-only keys; all file metadata
(encrypted names, folders, type, size, status, keys, integrity hash) stays in PostgreSQL. `./dev run` writes
settings for the local emulator into `.dev-secrets.env`. To use a real bucket (for example the test bucket)
replace the `S3_*` / `APP_STORAGE_S3_*` lines in that file with:

```bash
export S3_BUCKET=<bucket>
export S3_REGION=<region of the bucket>
export S3_ACCESS_KEY_ID=<key of the app's IAM user>      # optional: omit both keys to use the AWS default credential chain
export S3_SECRET_ACCESS_KEY=<secret>
```

Production uses the same variables (`S3_ENDPOINT` stays unset). The IAM policy and bucket hardening are in
spec section 5.3.3 / 5.3.4. Never point a local or test profile at the production bucket.

## Build

```bash
./mvnw -B -DskipTests package
```

## Configuration

See spec section 15. Local defaults live in `application.yml` / `application-local.yml`;
`.env.example` lists the variables used by Docker Compose. Never commit real secrets.

The application **will not start** without a key ring: set `APP_MASTER_KEYS` (`v1:<base64 256-bit key>`)
and `APP_ACTIVE_KEY_ID`. `./dev genkey` prints a new key; `./dev run` creates local development keys
and a local doctor account automatically (the initial password is printed once and kept in the
git-ignored `.dev-secrets.env`). See [docs/SECURITY.md](docs/SECURITY.md).

First login: sign in with the initial password, scan the QR code with an authenticator app, save the
recovery codes, then choose a new password. In production set `APP_BOOTSTRAP_USERNAME` and
`APP_BOOTSTRAP_PASSWORD` (used only when no user exists).

## Module layout

Modular monolith under `com.example.patientvault`: `shared`, `crypto`, `audit`, `identity`,
`patient`, `search`, `records`, `appointment`. Allowed dependencies between modules are documented in
each module's `package-info.java` and in spec section 4.1.
