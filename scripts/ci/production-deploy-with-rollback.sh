#!/usr/bin/env bash

set -euo pipefail

readonly HEALTH_TIMEOUT_SECONDS=60
readonly HEALTH_MAX_ATTEMPTS=12
readonly HEALTH_RETRY_INTERVAL_SECONDS=5
readonly HEALTH_REQUEST_TIMEOUT_SECONDS=4
readonly EXPECTED_CONTAINER_NAME="poppang-prod"
readonly EXPECTED_HEALTH_URL="http://localhost:4002/actuator/health"

if [[ "$#" -ne 8 ]]; then
  printf '%s\n' "deployment_result=invalid_arguments manual_recovery=required"
  exit 1
fi

readonly new_tar="$1"
readonly new_image="$2"
readonly container_name="$3"
readonly deploy_script="$4"
readonly health_url="$5"
readonly rollback_dir="$6"
readonly commit_sha="$7"
readonly run_key="$8"

rollback_tar=""
previous_image=""
rollback_available=false

log_result() {
  printf '%s\n' "$1"
}

cleanup_rollback_tar() {
  if [[ -z "${rollback_tar}" ]]; then
    return
  fi

  if [[ "${rollback_tar}" != "${rollback_dir}/poppang-prod-rollback-"*.tar ]]; then
    log_result "rollback_tar_cleanup=refused manual_recovery=required"
    return
  fi

  if rm -f -- "${rollback_tar}" >/dev/null 2>&1; then
    log_result "rollback_tar_cleanup=complete"
  else
    log_result "rollback_tar_cleanup=failed manual_recovery=required"
  fi
}

trap cleanup_rollback_tar EXIT

if [[ "${container_name}" != "${EXPECTED_CONTAINER_NAME}" \
  || "${health_url}" != "${EXPECTED_HEALTH_URL}" \
  || "${new_image}" != poppang-prod:* \
  || "${rollback_dir}" != /* \
  || "${rollback_dir}" == "/" \
  || ! "${commit_sha}" =~ ^[0-9a-fA-F]{40}$ \
  || ! "${run_key}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  log_result "deployment_result=invalid_contract manual_recovery=required"
  exit 1
fi

for required_command in docker curl jq bash; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    log_result "deployment_result=preflight_failed manual_recovery=required"
    exit 1
  fi
done

check_health() {
  local phase="$1"
  local attempt=1
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  local remaining
  local request_timeout
  local sleep_seconds
  local response
  local response_with_status
  local http_status

  while ((attempt <= HEALTH_MAX_ATTEMPTS && SECONDS < deadline)); do
    remaining=$((deadline - SECONDS))
    request_timeout="${HEALTH_REQUEST_TIMEOUT_SECONDS}"
    if ((remaining < request_timeout)); then
      request_timeout="${remaining}"
    fi

    response_with_status=""
    if response_with_status="$(
      curl --fail --silent --show-error \
        --connect-timeout "${request_timeout}" \
        --max-time "${request_timeout}" \
        --write-out $'\n%{http_code}' \
        "${health_url}" 2>/dev/null
    )"; then
      http_status="${response_with_status##*$'\n'}"
      response="${response_with_status%$'\n'*}"
      if [[ "${http_status}" =~ ^2[0-9]{2}$ ]] \
        && jq --exit-status '.status == "UP"' <<<"${response}" >/dev/null 2>&1; then
        log_result "${phase}_health=UP attempts=${attempt}"
        return 0
      fi
    fi

    if ((attempt >= HEALTH_MAX_ATTEMPTS || SECONDS >= deadline)); then
      break
    fi

    remaining=$((deadline - SECONDS))
    sleep_seconds="${HEALTH_RETRY_INTERVAL_SECONDS}"
    if ((remaining < sleep_seconds)); then
      sleep_seconds="${remaining}"
    fi
    if ((sleep_seconds > 0)); then
      sleep "${sleep_seconds}"
    fi
    attempt=$((attempt + 1))
  done

  log_result "${phase}_health=UNHEALTHY attempts=${attempt}"
  return 1
}

log_result "deployment_start commit=${commit_sha} new_image=${new_image}"

if previous_image="$(
  docker inspect --type container --format '{{.Config.Image}}' "${container_name}" 2>/dev/null
)" && [[ -n "${previous_image}" ]]; then
  log_result "previous_image=${previous_image}"

  if docker image inspect "${previous_image}" >/dev/null 2>&1; then
    rollback_tar="${rollback_dir}/poppang-prod-rollback-${run_key}.tar"
    if ! mkdir -p "${rollback_dir}"; then
      log_result "rollback_backup=failed manual_recovery=required"
      exit 1
    fi
    if docker save --output "${rollback_tar}" "${previous_image}" >/dev/null 2>&1; then
      rollback_available=true
      log_result "rollback_backup=created"
    else
      log_result "rollback_backup=failed manual_recovery=required"
      exit 1
    fi
  else
    log_result "rollback_backup=unavailable reason=previous_image_missing"
  fi
else
  previous_image=""
  log_result "previous_image=none rollback_backup=unavailable"
fi

if bash "${deploy_script}" "${new_tar}" "${new_image}" >/dev/null 2>&1; then
  log_result "new_deploy=completed"
  if check_health "new"; then
    log_result "deployment_result=success"
    exit 0
  fi
else
  log_result "new_deploy=failed"
fi

if [[ "${rollback_available}" != true ]]; then
  log_result "rollback_result=unavailable manual_recovery=required"
  log_result "deployment_result=failed_new_release"
  exit 1
fi

if ! bash "${deploy_script}" "${rollback_tar}" "${previous_image}" >/dev/null 2>&1; then
  log_result "rollback_result=failed manual_recovery=required"
  log_result "deployment_result=failed_new_release"
  exit 1
fi

if check_health "rollback"; then
  log_result "rollback_result=success"
  log_result "deployment_result=failed_new_release"
  exit 1
fi

log_result "rollback_result=failed manual_recovery=required"
log_result "deployment_result=failed_new_release"
exit 1
