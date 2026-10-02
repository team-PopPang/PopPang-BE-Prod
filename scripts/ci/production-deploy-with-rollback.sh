#!/usr/bin/env bash

set -euo pipefail

# EC2 BE 배포 helper.
# 배포 트랜잭션(flock, tar 검증·load, BE 단독 교체, 60초 health, 직전 이미지 롤백)은 root 배포 진입점
# /usr/local/sbin/poppang-deploy-be가 맡는다. 이 helper는 인자 계약을 검증한 뒤 서버 wrapper
# (/opt/poppang/deploy-prod.sh)를 한 번만 호출하고, 그 stdout과 종료 코드를 그대로 전파한다.
# health 재확인·재롤백·컨테이너 직접 조작은 하지 않는다.
#
# 진입점 결과: stdout 마지막 줄 `POPPANG_DEPLOY_RESULT status=<status>`
#   success/0, rolled_back/10, rollback_failed/20, rollback_unavailable/21,
#   rejected/64, busy/75, failed/70(교체 전 도구 오류)

readonly EXPECTED_CONTAINER_NAME="poppang-prod"
readonly EXPECTED_HEALTH_URL="http://localhost:4002/actuator/health"
readonly UPLOAD_DIR="/home/poppang-deploy/app"
readonly IMAGE_TAG_PATTERN='^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$'
readonly CONTRACT_REJECTED_EXIT_CODE=64

reject() {
  printf '%s\n' "deployment_result=$1 manual_recovery=not_required"
  exit "${CONTRACT_REJECTED_EXIT_CODE}"
}

if [[ "$#" -ne 7 ]]; then
  reject "invalid_arguments"
fi

readonly new_tar="$1"
readonly new_image="$2"
readonly container_name="$3"
readonly deploy_script="$4"
readonly health_url="$5"
readonly commit_sha="$6"
readonly run_key="$7"
readonly image_tag="${new_image#"${EXPECTED_CONTAINER_NAME}:"}"

if [[ "${new_image}" != "${EXPECTED_CONTAINER_NAME}:"* \
  || ! "${image_tag}" =~ ${IMAGE_TAG_PATTERN} \
  || "${new_tar}" != "${UPLOAD_DIR}/${EXPECTED_CONTAINER_NAME}-${image_tag}.tar" \
  || "${container_name}" != "${EXPECTED_CONTAINER_NAME}" \
  || "${health_url}" != "${EXPECTED_HEALTH_URL}" \
  || "${deploy_script}" != /*/deploy-prod.sh \
  || ! -x "${deploy_script}" \
  || ! "${commit_sha}" =~ ^[0-9a-fA-F]{40}$ \
  || ! "${run_key}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  reject "invalid_contract"
fi

printf '%s\n' "deployment_start commit=${commit_sha} new_image=${new_image}"

# 진입점 stdout은 고정 표식만 담으므로 그대로 내보낸다. stderr는 공개 Actions 로그에 남기지 않는다.
deploy_exit_code=0
"${deploy_script}" "${new_tar}" "${new_image}" 2>/dev/null || deploy_exit_code=$?
exit "${deploy_exit_code}"
