# Decisions log

Deviations from, and clarifications of, `PatientVault_Spec_v1.md`. Newest last.

| # | Date | Decision | Reason |
|---|---|---|---|
| D-001 | 2026-10-23 | Base package and Maven groupId: `com.example.patientvault`. | Confirmed by the project owner. |
| D-002 | 2026-10-23 | Java 25 (LTS) with Spring Boot 4.1.2 (latest stable 4.x at the time of writing). | JDK 25 is installed; Boot 4 supports it. |
| D-003 | 2026-10-23 | Automated tests (unit, integration, Testcontainers, Playwright, module-boundary verification, CI test stages) are **deferred** and will be added after the feature steps have been reviewed. | Requested by the project owner. Acceptance criteria stay in the spec and will be covered later. |
| D-004 | 2026-10-23 | Implementation proceeds in the order of spec section 14 with small commits and a review checkpoint after each step. | Requested by the project owner. |
