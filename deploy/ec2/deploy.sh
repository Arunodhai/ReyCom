#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.prod.yml"
ENV_FILE="${SCRIPT_DIR}/.env"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: '$1' is required but is not installed." >&2
    exit 1
  fi
}

require_value() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Error: ${name} must be set in ${ENV_FILE}." >&2
    exit 1
  fi
}

require_command aws
require_command docker

if ! docker compose version >/dev/null 2>&1; then
  echo "Error: the Docker Compose plugin is required." >&2
  exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: ${ENV_FILE} does not exist." >&2
  echo "Create it with: cp ${SCRIPT_DIR}/.env.example ${ENV_FILE}" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "${ENV_FILE}"
set +a

require_value AWS_ACCOUNT_ID
require_value AWS_REGION
require_value ECR_REPOSITORY
require_value POSTGRES_DB
require_value POSTGRES_USER
require_value POSTGRES_PASSWORD
require_value JWT_SECRET

if [ "${POSTGRES_PASSWORD}" = "change-me" ]; then
  echo "Error: replace the default POSTGRES_PASSWORD before deploying." >&2
  exit 1
fi

if [ "${JWT_SECRET}" = "replace-with-long-secret" ] || [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "Error: JWT_SECRET must be replaced with a value of at least 32 characters." >&2
  exit 1
fi

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

echo "Logging in to Amazon ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "Validating deployment configuration..."
"${COMPOSE[@]}" config --quiet

echo "Pulling deployment images..."
"${COMPOSE[@]}" pull

echo "Starting ReyCom services..."
"${COMPOSE[@]}" up -d --remove-orphans

echo
"${COMPOSE[@]}" ps
echo
echo "ReyCom deployment started."
echo "Health check: http://<EC2_PUBLIC_IP>:8080/api/health"
