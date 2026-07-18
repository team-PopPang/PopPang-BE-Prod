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

if [[ "${action_outcome}" == "success" ]] \
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
