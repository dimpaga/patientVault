# Decisions log

Deviations from, and clarifications of, `PatientVault_Spec_v1.md`. Newest last.

| # | Date | Decision | Reason |
|---|---|---|---|
| D-001 | 2026-10-23 | Base package and Maven groupId: `com.example.patientvault`. | Confirmed by the project owner. |
| D-002 | 2026-10-23 | Java 25 (LTS) with Spring Boot 4.1.2 (latest stable 4.x at the time of writing). | JDK 25 is installed; Boot 4 supports it. |
| D-003 | 2026-10-23 | Automated tests (unit, integration, Testcontainers, Playwright, module-boundary verification, CI test stages) are **deferred** and will be added after the feature steps have been reviewed. | Requested by the project owner. Acceptance criteria stay in the spec and will be covered later. |
| D-004 | 2026-10-23 | Implementation proceeds in the order of spec section 14 with small commits and a review checkpoint after each step. | Requested by the project owner. |
| D-005 | 2026-10-23 | `TextNormalizer` lives in the `search` module (spec 4.1 assigns normalisation to `search`), although the step-1 table lists it with the crypto kernel. | Keeps `crypto` free of domain logic; `BlindIndexer` takes already-normalised values. |
| D-006 | 2026-10-23 | The blind-index key is derived from `APP_BLIND_INDEX_KEY_ID` (default: first key in `APP_MASTER_KEYS`), not from the active key. | Rotating the active KEK (spec 6.2) must not force re-indexing; only an explicit blind-key change does, as the spec requires. |
| D-007 | 2026-10-23 | Values encrypted directly with a DEK carry the header key label `dek`; wrapped DEKs and user secrets carry the master key id. | The DEK has no version, and re-wrapping must not leave stale key ids inside field ciphertext. Header is authenticated via AAD. |
| D-008 | 2026-10-23 | Tink's `subtle` primitives (`AesGcmJce`, `AesGcmHkdfStreaming`, `Hkdf`, `PrfMac`) are used with raw derived keys instead of keyset handles. | Keys are derived deterministically from the env key ring (HKDF) and are never stored as Tink keysets; subtle classes are Tink's supported low-level API. |
| D-009 | 2026-10-23 | Stream ciphers take a `purpose` (`content`, `thumb`) that is mixed into the HKDF info and AAD. | Prevents a thumbnail ciphertext from being accepted as file content and vice versa. |
| D-010 | 2026-10-23 | `./dev run` generates local keys into the git-ignored `.dev-secrets.env`; no key is committed in `application-local.yml`. | Spec 7.4: no secret in the repository. |
