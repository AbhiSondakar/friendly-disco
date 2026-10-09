# EcoLoop Backend

Spring Boot 3.3 / Java 21 monolith for e-waste collection. Authentication is **server-side session based**, not JWT: login creates an `ECOLOOP_SESSION` HttpOnly cookie and session state is stored in Redis.

## Run locally

`docker compose` intentionally starts only the backend. PostgreSQL and Redis are external managed services, so create an uncommitted `.env` file before starting it:

```dotenv
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/ecoloop
DATABASE_USERNAME=ecoloop
DATABASE_PASSWORD=replace-me
REDIS_URL=redis://host.docker.internal:6379
COOKIE_SECURE=false
```

When PostgreSQL or Redis run in another Docker Compose project, replace `host.docker.internal` with the reachable service hostname/network address. The Compose file fails fast if any database or Redis value is missing; it does not silently create an incomplete local stack.

```bash
docker compose up -d --build
docker compose ps
```

After both external services are reachable, verify `http://localhost:8090/actuator/health`. New uploaded files live in PostgreSQL, so Compose deliberately has no uploads volume.

The server binds to `0.0.0.0:8090` by default so a phone on the same LAN can reach it.
API from the development computer: `http://localhost:8090`  |  OpenAPI: `http://localhost:8090/swagger-ui.html`

For a physical phone, find the computer's LAN address with `ipconfig` on Windows or
`ip addr` on Linux/macOS. Use the Wi-Fi adapter address, for example
`http://192.168.0.101:8090`, and make sure the phone and computer are on the same network.
Allow inbound TCP 8090 in Windows Firewall when prompted, or run PowerShell as an
administrator and add a scoped rule:

```powershell
New-NetFirewallRule -DisplayName "EcoLoop API 8090" -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Private
```

Verify reachability from the phone browser with `http://<computer-lan-ip>:8090/actuator/health`.
The response should be reachable even though protected API endpoints still require login.

## Configuration

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `REDIS_URL`
- `SERVER_ADDRESS` (default `0.0.0.0`)
- `PORT`
- `COOKIE_SECURE` (default `true`; keep enabled for HTTPS deployments)
- `COOKIE_SAME_SITE` (default `none` for a separately hosted HTTPS frontend; use `lax` only when frontend and API are same-site)
- `CORS_ALLOWED_ORIGINS` (comma-separated exact frontend origins, including scheme and port when applicable; for example `https://admin.example.com`)
- `ROBOFLOW_API_KEY` and `ROBOFLOW_MODEL_ID` (default model ID `e-waste-qmxtt-zuyip/1`; device image inference uses Roboflow's model endpoint)

Images with no usable model prediction are saved as `other` with database status `manual` for manual review; inference failures are still rejected.

No bearer-token configuration is required. Session expiry defaults to seven days and login rotates the session ID to prevent fixation.

Login is CSRF-protected. Browser and Android clients first call `GET /api/auth/csrf` and send the returned `X-XSRF-TOKEN` value on every mutating request, including `POST /api/auth/login`.

The admin web frontend calls the API cross-origin with session cookies. Cross-site session cookies require HTTPS, `COOKIE_SECURE=true`, and `COOKIE_SAME_SITE=none`. Add the frontend's exact origin to `CORS_ALLOWED_ORIGINS` in the backend deployment environment; do not include a path or trailing slash. After changing backend environment values, redeploy the service. In browser developer tools, confirm `/api/auth/login` succeeds, the `ECOLOOP_SESSION` cookie is accepted, and the following `/api/auth/me` request sends that cookie and returns role `ADMIN`.

## Uploaded file storage

New device photos, partner licenses, and pickup evidence are stored in PostgreSQL: upload details are kept in `uploads` and the file bytes are kept in the related `upload_contents` table. Flyway migration `V6` creates this table. New uploads no longer depend on the service's local filesystem or a Render persistent disk, and downloads remain protected by the existing session and ownership/partner authorization checks.

Existing uploads created before this change still use their recorded filesystem path when their file is present. A database migration cannot restore file bytes that were already lost when an ephemeral host restarted or redeployed; those historical image links will continue to return not found.

Database-backed files make PostgreSQL larger and increase backup and restore time. Include the database in regular backups and monitor its storage as uploads grow. Each upload remains limited to 5 MB.
"# friendly-disco" 
