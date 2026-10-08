# EcoLoop Backend Audit Report

## 1. Resolved VERIFY Items

| # | Item | Answer & Evidence | Risk |
|---|------|-------------------|------|
| 1 | **Household complete reachability** | **No**. It is unreachable dead code. `PickupService.java#complete(UUID, UUID)` exists and calls `completeAcceptedPickup(..., "household")`, but **no controller routes to it**. `PickupController.java#complete` explicitly restricts access with `@PreAuthorize("hasRole('PARTNER')")` and calls `completeForPartnerUser`. | Low (Dead Code) |
| 2 | **Partner ownership on jobs** | **Yes**. Both `PickupService.java#completeForPartner` and `verifyForPartner` contain explicit checks: `if (!partnerId.equals(pickup.getPartnerId())) throw ResponseStatusException(FORBIDDEN)`. | None |
| 3 | **cancelPickupByDevice** | The query `PickupRepository.java#findActiveByUserIdAndDeviceId` filters by `status NOT IN ('completed', 'cancelled')`. Thus, **`pending`, `accepted`, and `verified`** count as active. | None |
| 4 | **Offer reject preconditions** | Ownership is enforced via `offers.findByIdAndPartnerIdForUpdate` (returns 404 if not owned). Status is enforced via `Set.of("offered", "accepted")`. Expiry is checked via `enforceOfferNotExpired(offer)`. (`RoutingService.java#rejectOffer`) | None |
| 5 | **POST /api/partners/license** | It strictly requires the **`PARTNER`** role, inherited from the blanket `requestMatchers("/api/partners/**").hasRole("PARTNER")` rule in `SecurityConfig.java`. | None |
| 6 | **SecurityConfig rules** | **permitAll**: `/`, `/index.html`, `/assets/**`, `/favicon.ico`, `/error`, `/actuator/health`, `/api/auth/login`, `/api/auth/register`, `/api/auth/csrf`, `/api/auth/forgot-password`, `/api/auth/reset-password`.<br>**CSRF Protected**: login (web and Android clients bootstrap `/api/auth/csrf` and send `X-XSRF-TOKEN`).<br>**CORS**: Defaults to `http://localhost:3000,http://localhost:5173,http://localhost:8081`.<br>**Actuator/Swagger**: Locked to `hasRole("ADMIN")` (except `/health`). | None |
| 7 | **Docker configuration** | `docker-compose.yml` ONLY defines the `app` service. It **does not** spin up Postgres or Redis. `docker/postgres/init.sql` is an orphaned local script used manually to create `pgcrypto` and `uuid-ossp` extensions; it is unreferenced. | None |
| 8 | **Micrometer metrics** | There are **no** Timers or Gauges. Only six Counters exist in `RoboflowVisionProvider.java`: `ecoloop.ai.classification.` + `attempts`, `circuit_open`, `failures`, `empty`, `low_confidence`, `success`. | None |
| 9 | **Tests** | **NOT FOUND**. `RoutingOfferExpirationIntegrationTest` and `UploadStorageIntegrationTest` do not exist in the codebase. This was a hallucination in the previous documentation. | None |

---

## 2. Missing Information

### 10. First ADMIN Bootstrap
The first ADMIN is bootstrapped via `DataInitializer.java` (a `CommandLineRunner`). It triggers on startup if `SEED_ADMIN_ENABLED=true` (or `ecoloop.security.seed-admin.enabled=true`). It reads the `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables, hashes the password, and saves a user with `Role.ADMIN`. No manual SQL is required.

### 11. Complete Endpoint Inventory
There are exactly **67 API endpoints** exposed by the controllers (excluding the SPA fallback). The previous documentation severely undercounted (claiming 37) by missing internal aliases, the entire `AdminUserController`, `AdminAuditController`, and multiple `PartnerController` routes.

*Key Controller Counts:*
- `PartnerController`: 13 endpoints
- `PickupController`: 8 endpoints
- `AuthController`: 7 endpoints
- `UserController`: 7 endpoints (mapped at `/api/users/me`)
- `AdminPartnerController`: 5 endpoints
- `DeviceController`, `AdminUserController`, `RoutingController`, `RewardsController`: 4 endpoints each
- `NotificationController`, `AdminAuditController`: 3 endpoints each
- `AdminPickupController`: 2 endpoints
- `AiController`, `UploadController`, `AdminOverviewController`: 1 endpoint each

### 12. Configuration Keys
Beyond the documented keys, the application relies on:
- `ECOLOOP_UPLOAD_DIR`: Determines where files are stored (default: `uploads/devices`). *Note: Rendered obsolete by V6 DB storage but still present in config.*
- `APP_LOG_LEVEL`: Controls logging verbosity for `com.ecoloop` (default: `INFO`).
- `SEED_ADMIN_ENABLED`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`: Used by `DataInitializer`.

### 13. Dead or Inconsistent Code
- **`in_progress` status**: **RESOLVED** in Flyway V13. Legacy rows are normalized to `accepted`, and the canonical database constraint and state machine exclude this unused state.
- **Unreachable Methods**: `PickupService.complete(UUID, UUID)` (household complete) is completely disconnected from any controller mapping.
- **Duplicated Endpoints**: To accept an offer, a partner can call `/api/pickups/{id}/accept`, `/api/routing/offers/{id}/accept`, OR `/api/partners/offers/{id}/accept`. They all proxy to the exact same logic, creating unnecessary API surface bloat.

### 14. Stray Files
`script.py`, `dummy.jpg`, and `search_results.txt` are orphaned artifacts in the repository root. `script.py` is a brittle regex script previously used to parse controllers (which is why the 37 endpoint count was wrong). They are unreferenced by the application.

### 15. Operations Clues
**NOT FOUND**. There are no `.github/workflows`, `.gitlab-ci.yml`, `Procfile`, or deployment shell scripts. The `Dockerfile` is a standard multi-stage build without a `HEALTHCHECK` directive. Hosting target is completely agnostic.

---

## 3. Corrections to Previous Documentation

1. **Endpoint Count**: Corrected from 37 to 67. The previous document completely missed the `UserController` (`/api/users/me`), Audit controllers, and aliased partner routes.
2. **Household Complete Vulnerability**: Corrected. Households *cannot* complete their own pickup to steal points. The controller correctly blocks this with `@PreAuthorize("hasRole('PARTNER')")`, even though the underlying service method would theoretically allow it.
3. **Docker Orchestration**: Corrected. `docker-compose.yml` does *not* orchestrate Postgres or Redis. It only builds and runs the Spring Boot container.
4. **Integration Tests**: Removed claims that `RoutingOfferExpirationIntegrationTest` and `UploadStorageIntegrationTest` exist.

---

## 4. New Findings

| Severity | Issue | File Reference | Recommendation |
|----------|-------|----------------|----------------|
| **Med** | **Dead Code (Household Complete)** | `PickupService.java:310` | Remove the `complete(userId, pickupId)` method. If a controller is accidentally mapped to it in the future, it creates a massive IDOR/fraud vulnerability allowing households to infinitely generate points. |
| **Low** | **API Surface Bloat** | `PartnerController`, `RoutingController`, `PickupController` | Consolidate the 3 distinct endpoints used for accepting/rejecting/completing jobs into a single RESTful path to reduce maintenance surface. |
| **Low** | **Orphaned Scripts** | Root Directory | Delete `script.py`, `dummy.jpg`, and `search_results.txt` to keep the repository clean. |

---

## 5. Remaining Unknowns

1. **Frontend / Mobile Behavior**: Because the repo is strictly backend, it is impossible to determine how the frontend handles the three duplicated accept endpoints, whether it actively respects the `notification_preferences`, or how it renders the AI boundary states (`manual`).
2. **Production Operations**: Without CI/CD configs or hosting files, it is unknown how the `DATABASE_URL` injections, SSL termination, and horizontal scaling (Redis sessions) are managed in the production environment.
