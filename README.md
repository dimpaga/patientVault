# PatientVault

Web application for a single doctor to store, search and view patient personal and medical data
(photos, PDFs, DICOM files and folders). Personal and medical data is encrypted at application level
before it reaches PostgreSQL or CouchDB. The full design and requirements are in
[PatientVault_Spec_v1.md](PatientVault_Spec_v1.md); deviations and decisions are in
[docs/DECISIONS.md](docs/DECISIONS.md).

**Stack:** Java 25, Spring Boot 4.1, Thymeleaf + HTMX, PostgreSQL 17, CouchDB 3, Flyway.

## Status

Implementation follows spec section 14. Done so far: step 0 (foundation). Automated tests are deferred.

## Prerequisites

- JDK 25 (`JAVA_HOME` must point to it; the Maven wrapper is used, no local Maven needed)
- Docker (for PostgreSQL, CouchDB and Mailpit)

## Run locally

```bash
./dev up      # start PostgreSQL, CouchDB and Mailpit (SMTP sink UI: http://localhost:8025)
./dev run     # start the app with the "local" profile on http://localhost:8080
./dev down    # stop the services (./dev reset also removes their data volumes)
```

Health: `http://localhost:8080/actuator/health/readiness`.

## Build

```bash
./mvnw -B -DskipTests package
```

## Configuration

See spec section 15. Local defaults live in `application.yml` / `application-local.yml`;
`.env.example` lists the variables used by Docker Compose. Never commit real secrets.

## Module layout

Modular monolith under `com.example.patientvault`: `shared`, `crypto`, `audit`, `identity`,
`patient`, `search`, `records`, `appointment`. Allowed dependencies between modules are documented in
each module's `package-info.java` and in spec section 4.1.
