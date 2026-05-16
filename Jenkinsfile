// Declarative pipeline for build.devwshyam.com.
//
// What it does:
//   1. Build the Docker image via the multi-stage Dockerfile.
//   2. Bring up `docker compose up -d` on the Jenkins box itself.
//   3. Poll /actuator/health until it returns 200 — fail the build if not.
//   4. On any failure, dump the last 100 lines of container logs.
//
// What it expects on the Jenkins agent:
//   - Docker + the `docker compose` plugin
//   - The jenkins user is in the `docker` group (so `docker compose` doesn't need sudo)
//   - Two Jenkins "Secret text" credentials:
//       ruhani-mysql-password  → the password for MySQL_USER (default `ruhani`)
//       ruhani-jwt-secret      → ≥32-byte HMAC secret
//   - Reachability from this host to MySQL at 10.0.0.83:3306
//
// To switch to push-to-registry + ssh-deploy later, swap the body of the
// `Deploy` stage; the rest of the pipeline stays the same.

pipeline {
  agent any

  options {
    timeout(time: 15, unit: 'MINUTES')
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    WEBAPP_CREDENTIALS = credentials('saathji-backend.webapp-secrets');
  }

  stages {

    stage ('Init') {
      steps {
        script {
          def props = readProperties file: env.WEBAPP_CREDENTIALS
          // Compose reads these from env. Jenkins masks them in logs.
          env.MYSQL_PASSWORD    = props.MYSQL_PASSWORD
          env.RUHANI_JWT_SECRET = props.RUHANI_JWT_SECRET
          // Non-secret defaults — override via Jenkins env vars if needed.
          env.MYSQL_HOST = "${env.MYSQL_HOST ?: '10.0.0.83'}"
          env.MYSQL_PORT = "${env.MYSQL_PORT ?: '3306'}"
          env.MYSQL_DB   = "${env.MYSQL_DB ?: 'ruhani'}"
          env.MYSQL_USER = "${env.MYSQL_USER ?: 'ruhani'}"
          // OTP delivery — set RUHANI_EMAIL_PROVIDER=ses + AWS_* credentials in
          // Jenkins to enable real SES. Default `log` keeps things printing to stdout.
          env.RUHANI_EMAIL_PROVIDER = "${env.RUHANI_EMAIL_PROVIDER ?: 'log'}"
          env.RUHANI_EMAIL_FROM     = "${env.RUHANI_EMAIL_FROM ?: 'no-reply@saathji.com'}"
          env.RUHANI_AWS_REGION     = "${env.RUHANI_AWS_REGION ?: 'us-east-2'}"
          env.BACKEND_PORT = "${env.BACKEND_PORT ?: '8085'}"
        }
      }
    }

    stage('Build image') {
      when {
        expression {
      	  return true
        }
      }
      steps {
        sh 'docker compose build --pull'
      }
    }

    stage('Deploy') {
      steps {
        // `up -d` recreates the container only if its config / image changed,
        // so unchanged deploys are no-ops. `--remove-orphans` cleans up any
        // service that was removed from compose.yml.
        sh 'docker compose up -d --remove-orphans'
      }
    }

    stage('Smoke test') {
    agent any
      steps {
        // BACKEND_PORT is the HOST port compose publishes to (defaults 8080,
        // currently 8085 on this Jenkins box). The container is always on
        // 8080 internally; the smoke test runs on the host so it uses the
        // published port.
        sh '''
          set -e
          echo "Waiting for /actuator/health on port ${BACKEND_PORT:-8080}..."
          for i in $(seq 1 60); do
            if curl -fsS --max-time 2 "http://localhost:${BACKEND_PORT:-8080}/actuator/health" > /tmp/health.json 2>/dev/null; then
              echo "✓ healthy:"
              cat /tmp/health.json
              echo
              exit 0
            fi
            sleep 2
          done
          echo "✗ /actuator/health never returned 200 within 120s"
          exit 1
        '''
      }
    }
  }

  post {
    success {
      sh 'docker compose ps'
    }
    failure {
      // Print logs so the cause of the failed deploy is visible in the build page.
      sh 'docker compose logs --tail=100 || true'
      sh 'docker compose ps || true'
    }
  }
}
