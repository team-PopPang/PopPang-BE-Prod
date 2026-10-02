#!/usr/bin/env bash

set -euo pipefail

readonly action_outcome="${DEPLOYMENT_ACTION_OUTCOME:-unknown}"
readonly deployment_output="${DEPLOYMENT_OUTPUT:-}"
readonly commit_sha="${DEPLOYMENT_COMMIT:-unknown}"
readonly image_name="${DEPLOYMENT_IMAGE:-unknown}"
readonly summary_path="${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"

deployment_status="FAILED"
rollback_status="UNKNOWN"
manual_recovery="YES"
detail="Deployment or result capture failed before rollback could be confirmed."

has_marker() {
  [[ "${deployment_output}" == *"$1"* ]]
}

last_value() {
  tr -d '\r' <<<"${deployment_output}" | sed -n "s/^$1\([A-Za-z0-9_]*\)\$/\1/p" | tail -n 1
}

# EC2 배포 진입점의 결과 표식과 종료 코드 계약
expected_exit_code() {
  case "$1" in
    success) printf '0' ;;
    rolled_back) printf '10' ;;
    rollback_failed) printf '20' ;;
    rollback_unavailable) printf '21' ;;
    rejected) printf '64' ;;
    busy) printf '75' ;;
    failed) printf '70' ;;
    *) printf 'none' ;;
  esac
}

entrypoint_status="$(last_value 'POPPANG_DEPLOY_RESULT status=')"
remote_exit_code="$(last_value 'remote_exit_code=')"

if [[ "${action_outcome}" == "success" && -n "${entrypoint_status}" ]]; then
  # 표식과 종료 코드가 계약대로 일치할 때만 판정하고, 아니면 UNKNOWN(수동 복구 필요)으로 둔다.
  if [[ "$(expected_exit_code "${entrypoint_status}")" == "${remote_exit_code}" ]]; then
    case "${entrypoint_status}" in
      success)
        if has_marker "deployment_result=success" && has_marker "new_health=UP"; then
          deployment_status="SUCCESS"
          rollback_status="NOT_REQUIRED"
          manual_recovery="NO"
          detail="The new image passed the health check."
        fi
        ;;
      rolled_back)
        rollback_status="SUCCESS"
        manual_recovery="NO"
        detail="The previous image was restored, but the new release was rejected."
        ;;
      rollback_failed)
        rollback_status="FAILED"
        detail="Automatic rollback failed. Immediate manual recovery is required."
        ;;
      rollback_unavailable)
        rollback_status="UNAVAILABLE"
        detail="No verified previous image was available. Immediate manual recovery is required."
        ;;
      rejected)
        rollback_status="NOT_REQUIRED"
        manual_recovery="NO"
        detail="The deploy entrypoint rejected the request before replacing the running container."
        ;;
      busy)
        rollback_status="NOT_REQUIRED"
        manual_recovery="NO"
        detail="Another deployment holds the server lock. Nothing was changed."
        ;;
      failed)
        rollback_status="NOT_REQUIRED"
        manual_recovery="NO"
        detail="The deploy entrypoint failed before replacing the running container."
        ;;
    esac
  fi
elif [[ "${action_outcome}" == "success" ]] \
  && has_marker "remote_exit_code=0" \
  && has_marker "deployment_result=success" \
  && has_marker "new_health=UP"; then
  deployment_status="SUCCESS"
  rollback_status="NOT_REQUIRED"
  manual_recovery="NO"
  detail="The new image passed the health check."
elif [[ "${action_outcome}" == "success" ]] && has_marker "rollback_result=success"; then
  rollback_status="SUCCESS"
  manual_recovery="NO"
  detail="The previous image was restored, but the new release was rejected."
elif [[ "${action_outcome}" == "success" ]] && has_marker "rollback_result=failed"; then
  rollback_status="FAILED"
  detail="Automatic rollback failed. Immediate manual recovery is required."
elif [[ "${action_outcome}" == "success" ]] && has_marker "rollback_result=unavailable"; then
  rollback_status="UNAVAILABLE"
  detail="No verified previous image was available. Immediate manual recovery is required."
fi

{
  printf '%s\n' "## Production deployment result" ""
  printf '%s\n' "| Field | Result |" "| --- | --- |"
  printf '| Deployment | %s |\n' "${deployment_status}"
  printf '| Rollback | %s |\n' "${rollback_status}"
  printf '| Manual recovery | %s |\n' "${manual_recovery}"
  printf '| Commit | `%s` |\n' "${commit_sha}"
  printf '| Image | `%s` |\n' "${image_name}"
  printf '\n%s\n' "${detail}"
} >> "${summary_path}"

printf 'deployment_summary=%s rollback=%s manual_recovery=%s\n' \
  "${deployment_status}" "${rollback_status}" "${manual_recovery}"

if [[ "${deployment_status}" == "SUCCESS" ]]; then
  exit 0
fi

exit 1
