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
    // Compose reads these from env. Jenkins masks them in logs.
    MYSQL_PASSWORD    = credentials('ruhani-mysql-password')
    RUHANI_JWT_SECRET = credentials('ruhani-jwt-secret')

    // Non-secret defaults — override via Jenkins env vars if needed.
    MYSQL_HOST = "${env.MYSQL_HOST ?: '10.0.0.83'}"
    MYSQL_PORT = "${env.MYSQL_PORT ?: '3306'}"
    MYSQL_DB   = "${env.MYSQL_DB ?: 'ruhani'}"
    MYSQL_USER = "${env.MYSQL_USER ?: 'ruhani'}"

    // OTP delivery — set RUHANI_EMAIL_PROVIDER=ses + AWS_* credentials in
    // Jenkins to enable real SES. Default `log` keeps things printing to stdout.
    RUHANI_EMAIL_PROVIDER = "${env.RUHANI_EMAIL_PROVIDER ?: 'log'}"
    RUHANI_EMAIL_FROM     = "${env.RUHANI_EMAIL_FROM ?: 'no-reply@saathji.com'}"
    RUHANI_AWS_REGION     = "${env.RUHANI_AWS_REGION ?: 'us-east-2'}"
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build image') {
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
      steps {
        sh '''
          set -e
          echo "Waiting for /actuator/health..."
          for i in $(seq 1 60); do
            if curl -fsS --max-time 2 http://localhost:8080/actuator/health > /tmp/health.json 2>/dev/null; then
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
