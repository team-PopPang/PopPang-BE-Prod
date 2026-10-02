#!/usr/bin/env bash

set -euo pipefail

# 빌드 산출물(bootJar, Docker 이미지)에 운영 설정·Apple .p8·실제 비밀값이 없는지 검사한다.
# 공개 레포의 Actions 로그에 남으므로 파일 내용과 파일명은 출력하지 않고 고정 라벨과 개수만 출력한다.
#
#   verify-build-artifacts.sh jar <boot-jar>
#   verify-build-artifacts.sh image <docker-image>
#
# JAR의 application.yml·application-prod.yml은 커밋된 템플릿(git HEAD)과 바이트 단위로 같아야 한다.
# ARTIFACT_TEMPLATE_SOURCE=worktree이면 작업 트리 파일과 비교한다(git 메타데이터가 없는 검증 사본용).

readonly CLASSES_DIR="BOOT-INF/classes"
readonly ALLOWED_CONFIGS="application.yml application-prod.yml"
readonly CONFIG_FILE_PATTERN='^(application|bootstrap)[^/]*\.(ya?ml|properties)$'
readonly FORBIDDEN_FILE_PATTERN='(\.p8|\.pem|\.key|\.p12|\.pfx|\.jks|\.keystore)$|(^|/)\.env(\.[^/]*)?$'
readonly FORBIDDEN_IMAGE_FILE_PATTERN='\.p8$|(^|/)\.env(\.[^/]*)?$|(^|/)(application|bootstrap)[^/]*\.(ya?ml|properties)$'
readonly SECRET_PATTERN='-----BEGIN ([A-Z]+ )*PRIVATE KEY-----|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{22,}|AIza[0-9A-Za-z_-]{35}|xox[abprs]-[A-Za-z0-9-]{10,}'
readonly ALLOWED_IMAGE_ENV="PATH JAVA_HOME LANG LANGUAGE LC_ALL JAVA_VERSION TZ JAVA_TOOL_OPTIONS"

repo_root="${ARTIFACT_REPO_ROOT:-$(pwd)}"
template_source="${ARTIFACT_TEMPLATE_SOURCE:-git}"
work_dir=""
container_id=""
failures=0

cleanup() {
  if [[ -n "${container_id}" ]]; then
    docker rm --force "${container_id}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${work_dir}" ]]; then
    rm -rf -- "${work_dir}"
  fi
}

trap cleanup EXIT

fail() {
  printf '%s\n' "artifact_check=failed reason=$1"
  failures=$((failures + 1))
}

count_matches() {
  LC_ALL=C grep -Ec -- "$1" || true
}

template_bytes() {
  if [[ "${template_source}" == "git" ]]; then
    git -C "${repo_root}" show "HEAD:src/main/resources/$1"
  else
    cat -- "${repo_root}/src/main/resources/$1"
  fi
}

check_jar() {
  local jar="$1"
  local extract_dir="$2"
  local entries
  local own_entries
  local forbidden
  local unexpected=0
  local entry
  local config
  local matched
  local status=0

  if [[ ! -f "${jar}" ]]; then
    fail "jar_missing"
    return
  fi
  if ! entries="$(unzip -Z1 "${jar}" 2>/dev/null)"; then
    fail "jar_unreadable"
    return
  fi

  # BOOT-INF/lib의 서드파티 JAR 내부는 검사하지 않는다. JAR 루트·META-INF·BOOT-INF/classes만 본다.
  own_entries="$(grep -v '^BOOT-INF/lib/' <<<"${entries}" || true)"

  forbidden="$(count_matches "${FORBIDDEN_FILE_PATTERN}" <<<"${own_entries}")"
  if ((forbidden > 0)); then
    fail "forbidden_file_type count=${forbidden}"
  fi

  while IFS= read -r entry; do
    if [[ -z "${entry}" || "${entry}" == */ || ! "${entry##*/}" =~ ${CONFIG_FILE_PATTERN} ]]; then
      continue
    fi
    case "${entry}" in
      "${CLASSES_DIR}/application.yml" | "${CLASSES_DIR}/application-prod.yml") ;;
      *) unexpected=$((unexpected + 1)) ;;
    esac
  done <<<"${own_entries}"
  if ((unexpected > 0)); then
    fail "unexpected_config_file count=${unexpected}"
  fi

  mkdir -p "${extract_dir}"
  unzip -qq -o "${jar}" -x 'BOOT-INF/lib/*' '*.class' -d "${extract_dir}" >/dev/null 2>&1 || status=$?
  if ((status > 1)); then
    fail "jar_extract_failed"
    return
  fi

  for config in ${ALLOWED_CONFIGS}; do
    if [[ ! -f "${extract_dir}/${CLASSES_DIR}/${config}" ]]; then
      fail "config_template_missing name=${config}"
    elif ! template_bytes "${config}" 2>/dev/null \
      | cmp -s - "${extract_dir}/${CLASSES_DIR}/${config}"; then
      fail "config_not_committed_template name=${config}"
    fi
  done

  status=0
  matched="$(LC_ALL=C grep -rlIE -- "${SECRET_PATTERN}" "${extract_dir}")" || status=$?
  if ((status > 1)); then
    fail "secret_scan_failed"
  elif ((status == 0)); then
    fail "secret_pattern count=$(grep -c '' <<<"${matched}")"
  fi
}

check_image() {
  local image="$1"
  local env_keys
  local key
  local unexpected_env=0
  local listing
  local app_entries
  local forbidden
  local status=0

  if ! docker image inspect "${image}" >/dev/null 2>&1; then
    fail "image_missing"
    return
  fi

  env_keys="$(docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${image}" \
    | sed 's/=.*//')"
  while IFS= read -r key; do
    if [[ -n "${key}" && " ${ALLOWED_IMAGE_ENV} " != *" ${key} "* ]]; then
      unexpected_env=$((unexpected_env + 1))
    fi
  done <<<"${env_keys}"
  if ((unexpected_env > 0)); then
    fail "unexpected_image_env count=${unexpected_env}"
  fi

  if ! container_id="$(docker create "${image}" 2>/dev/null)"; then
    container_id=""
    fail "image_create_failed"
    return
  fi

  listing="$(docker export "${container_id}" | tar -tf -)" || status=$?
  if ((status != 0)); then
    fail "image_export_failed"
    return
  fi

  app_entries="$(grep -E '^/?app(/|$)' <<<"${listing}" | sed -E 's#^/##; s#/$##' | sort -u || true)"
  if [[ "${app_entries}" != $'app\napp/app.jar' ]]; then
    fail "unexpected_app_directory_content"
  fi

  forbidden="$(count_matches "${FORBIDDEN_IMAGE_FILE_PATTERN}" <<<"${listing}")"
  if ((forbidden > 0)); then
    fail "forbidden_image_file count=${forbidden}"
  fi

  if ! docker cp "${container_id}:/app/app.jar" "${work_dir}/image-app.jar" >/dev/null 2>&1; then
    fail "image_jar_copy_failed"
    return
  fi
  check_jar "${work_dir}/image-app.jar" "${work_dir}/image-jar"
}

if [[ "$#" -ne 2 || ( "$1" != "jar" && "$1" != "image" ) ]]; then
  fail "invalid_arguments"
  exit 1
fi
if [[ "${template_source}" != "git" && "${template_source}" != "worktree" ]]; then
  fail "invalid_template_source"
  exit 1
fi

work_dir="$(mktemp -d)"
if [[ "$1" == "jar" ]]; then
  check_jar "$2" "${work_dir}/jar"
else
  check_image "$2"
fi

if ((failures > 0)); then
  exit 1
fi
printf '%s\n' "artifact_check=passed mode=$1"
