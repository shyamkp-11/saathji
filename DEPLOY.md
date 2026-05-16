# Deploying ruhani-backend

The backend is shipped as a Docker image built and deployed by Jenkins on
**build.devwshyam.com**. MySQL lives separately at `10.0.0.83:3306` and is
reachable from the Jenkins host.

```
   ┌────────┐  webhook   ┌──────────────────────┐  docker compose up -d
   │ GitHub │ ─────────▶ │  Jenkins             │ ──────────────────────▶  ruhani-backend:latest
   └────────┘            │  build.devwshyam.com │                          (container on same host)
                         └──────────────────────┘
                                  │
                                  │  JDBC over LAN
                                  ▼
                         ┌──────────────────────┐
                         │  MySQL 10.0.0.83     │
                         └──────────────────────┘
```

---

## One-time setup on the Jenkins host

1. **Install Docker + the Compose plugin** (skip if already done):
   ```bash
   sudo apt install docker.io docker-compose-plugin
   sudo usermod -aG docker jenkins   # so `docker compose` works without sudo
   sudo systemctl restart jenkins
   ```

2. **Create the two required credentials** in Jenkins
   (Manage Jenkins → Credentials → Global → Add):

   | Kind          | ID                       | Value                                          |
   | ------------- | ------------------------ | ---------------------------------------------- |
   | Secret text   | `ruhani-mysql-password`  | the password for the `ruhani` MySQL user       |
   | Secret text   | `ruhani-jwt-secret`      | `openssl rand -base64 48` (≥32 bytes)          |

   If you want SES OTP delivery instead of the stdout logger, also add:

   | Kind        | ID                          | Value                |
   | ----------- | --------------------------- | -------------------- |
   | Secret text | `ruhani-aws-access-key`     | AWS access key       |
   | Secret text | `ruhani-aws-secret-key`     | AWS secret key       |

   …then wire them into the `environment {}` block of the Jenkinsfile.

3. **Create the pipeline job**
   - New item → Multibranch Pipeline (or Pipeline if only `main` matters)
   - Branch source → GitHub → point at the repo
   - Script Path: `Jenkinsfile`

4. **Network sanity check** on the Jenkins host:
   ```bash
   nc -zv 10.0.0.83 3306    # expect "succeeded"
   ```

---

## What a build does

The pipeline (`Jenkinsfile`) runs four stages:

1. **Checkout** the repo at the triggering commit.
2. **Build image** via `docker compose build --pull` — uses the multi-stage
   Dockerfile so the boot jar is rebuilt inside an `eclipse-temurin:21-jdk`
   container and shipped on a slim `eclipse-temurin:21-jre`.
3. **Deploy** with `docker compose up -d --remove-orphans`. Compose only
   recreates the container if the image or its config changed, so re-builds
   without source changes are effectively no-ops.
4. **Smoke test** polls `http://localhost:8080/actuator/health` for 120 s,
   failing the build if it never reports `UP`. On failure the last 100 lines
   of container logs are printed.

The container restarts automatically (`restart: unless-stopped` in
docker-compose.yml) so the host can reboot and bring the service back.

---

## Exposing the API publicly

Right now `docker compose` binds **8080 on the host**. Two paths to get HTTPS
+ a real hostname in front:

- **Reverse proxy on the same box** (recommended)
  Install [Caddy](https://caddyserver.com/) and drop in a one-liner:
  ```caddy
  api.devwshyam.com {
      reverse_proxy localhost:8080
  }
  ```
  Caddy handles cert issuance via Let's Encrypt automatically.

- **Cloudflare Tunnel** if you don't want to expose any host ports at all.

Once a public hostname exists, point the frontend's `BackendConfig` at it
and flip `baseProtocol = HTTPS`.

---

## Rolling back

`docker compose` tags the image as `ruhani-backend:latest` every build, so
"the previous version" isn't kept by name. Two practical options:

1. **Revert in git**, push, let Jenkins redeploy the older commit.
2. **Tag immutable images per build** by adding to the Build stage:
   ```bash
   docker tag ruhani-backend:latest ruhani-backend:${BUILD_NUMBER}
   ```
   Then roll back with `docker tag ruhani-backend:42 ruhani-backend:latest && docker compose up -d`.

---

## Local dev still works the same way

The Jenkinsfile doesn't change anything about `docker compose up` locally —
fill in `.env` from `.env.example` and run:

```bash
docker compose up --build
```

Or, without Docker:

```bash
MYSQL_PASSWORD='change-me' ./gradlew bootRun
```
