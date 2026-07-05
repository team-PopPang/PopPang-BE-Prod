# PopPang BE Deployment Runbook

이 문서는 PopPang BE 배포와 운영 확인을 반복 가능하게 만들기 위한 루트 런북이다. 민감값은 절대 문서, 커밋, 채팅, 로그에 남기지 않는다. 실제 값이 필요하면 로컬 private config나 GitHub Secrets에서만 다룬다.

## 현재 배포 경로

이 저장소에는 배포 경로가 두 개 공존한다.

| 경로 | 트리거 | 실제 동작 | 주의 |
|---|---|---|---|
| 로컬 `makefile` | 사용자가 `make prod-deploy VERSION=x.y.z` 실행 | `bootJar` -> Docker `linux/amd64` buildx -> tar 저장 -> `scp poppang-server` -> 원격 `deploy-prod.sh` 호출 | `VERSION`을 항상 명시한다. 기본값은 오래될 수 있다. |
| GitHub Actions `cicd.yml` | `main` push 또는 수동 실행 | private config 다운로드 -> `bootJar` -> Docker build/save -> 서버 전송 -> 원격 `deploy-prod.sh` 호출 | 현재 `APP_NAME: poppang-dev`인데 `deploy-prod.sh`를 호출한다. 운영 배포로 신뢰하기 전 대상과 의도를 확인한다. |

PR에는 `build-test.yml`이 실행되어 private config를 받은 뒤 `./gradlew clean build`를 수행한다. 이슈/프로젝트/이메일 workflow도 있으나 배포 런북의 source of truth는 아니다.

## Preconditions

- 배포 대상 브랜치와 커밋이 의도한 상태인지 확인한다.
- 로컬 수동 배포를 할 경우 SSH alias `poppang-server`가 동작해야 한다.
- Docker Desktop/buildx가 동작해야 한다.
- 루트 `.env`에 private repo 접근용 token이 있어야 한다.
- 원격 서버의 `/home/poppang/opt/deploy/deploy-prod.sh`가 현재 이미지명, 포트, env, volume 정책과 맞아야 한다.
- 팝업 제보 이미지를 운영에 보존하려면 `SUBMISSION_IMAGE_ROOT` 또는 기본 `/opt/submission_images`가 persistent volume으로 유지되어야 한다.

```env
GITHUB_ACCESS_TOKEN=...
```

권장 token 설정:

- GitHub Fine-grained personal access token
- Resource owner: `team-PopPang`
- Repository: `PopPang-Private`
- Permission: `Contents: Read-only`

토큰은 채팅, 커밋, 로그에 붙여넣지 않는다. 노출된 토큰은 즉시 revoke한다.

## Private Config

Private config는 `team-PopPang/PopPang-Private` repository의 `BE` branch에서 받는다.

로컬 `make getKey`가 받는 파일:

- `src/main/resources/application-prod.yml`
- `src/main/resources/auth/AuthKey_382T2TB4RW.p8`

GitHub Actions가 받는 파일:

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/auth/AuthKey_382T2TB4RW.p8`

중요한 차이:

- 로컬 `make getKey`는 현재 `application.yml`을 받지 않는다. 클린 클론에서 공통 `application.yml`이 없으면 로컬 실행/빌드 전 별도 확보가 필요할 수 있다.
- `make getKey` 내부 `curl -s`는 HTTP 실패 응답을 파일로 저장할 수 있다. 아래 sanity check를 먼저 수행한다.
- `application*.yml`, `.p8`, `.env`는 `.gitignore` 대상이므로 커밋하면 안 된다.

## Safe Private Config Refresh

`make getKey` 전 private 파일 접근을 `curl -f`로 검증한다. 출력은 구조 확인용으로만 마스킹한다.

```bash
source ./.env
curl -fsSL \
  -H "Authorization: Bearer ${GITHUB_ACCESS_TOKEN}" \
  -o /tmp/poppang-application-prod.yml \
  https://raw.githubusercontent.com/team-PopPang/PopPang-Private/BE/src/main/resources/application-prod.yml

sed -n '1,20p' /tmp/poppang-application-prod.yml | sed -E 's/(: ).+$/\1***MASKED***/'
```

검증이 성공하면 private 파일을 갱신한다.

```bash
make getKey
```

갱신 후 최소 확인:

```bash
test -s src/main/resources/application-prod.yml
test -s src/main/resources/auth/AuthKey_382T2TB4RW.p8
sed -n '1,20p' src/main/resources/application-prod.yml | sed -E 's/(: ).+$/\1***MASKED***/'
```

`application-prod.yml` 또는 `.p8` 내용이 `404`, `Not Found`, HTML 응답으로 시작하면 배포하지 말고 정상 파일을 복구한다.

## Local Pre-Deploy Checks

```bash
git status --short
./gradlew spotlessCheck
./gradlew test
```

주의:

- 테스트는 private DB/Redis/JWT 설정을 필요로 할 수 있다. `JWT_SECRET` 하나만 주입하면 된다고 가정하지 않는다.
- 외부 DB/Redis에 연결되는 profile로 테스트가 실행될 수 있으므로, 로컬/CI 설정이 안전한지 먼저 확인한다.
- `./gradlew build`는 test까지 실행한다.

## Manual Deploy

배포 버전은 기존 최신 태그/이미지보다 높은 값으로 정한다. 예: `1.2.4`.

```bash
make prod-deploy VERSION=1.2.4
```

실제 흐름:

1. `./gradlew clean bootJar`
2. `docker buildx build --platform linux/amd64 -t poppang-prod:<VERSION> --load .`
3. `docker save -o poppang-prod-<VERSION>.tar poppang-prod:<VERSION>`
4. `scp poppang-prod-<VERSION>.tar poppang-server:/home/poppang/opt/deploy/`
5. `ssh poppang-server "bash /home/poppang/opt/deploy/deploy-prod.sh ..."`

주의:

- 루트에서 `make`만 실행하면 `getKey`와 `prod-deploy`가 모두 실행된다.
- `makefile` 기본 `VERSION`은 고정값이다. 배포 명령에는 `VERSION=x.y.z`를 반드시 명시한다.
- `deploy-prod.sh`는 저장소에 없다. 컨테이너명, 포트 매핑, env, volume, rollback 동작은 원격 서버 파일 기준으로 확인해야 한다.

## GitHub Actions Deploy

`cicd.yml`은 `main` push와 `workflow_dispatch`에서 동작한다.

현재 workflow 특성:

- private config를 GitHub Secret `PERSONAL_ACCESS_TOKEN`으로 다운로드한다.
- image version은 `${GITHUB_SHA::7}`이다.
- Docker image name은 현재 `poppang-dev:<short-sha>`다.
- 원격 호출은 `deploy-prod.sh`다.
- workflow 자체에는 health check나 rollback step이 없다.

이 조합은 prod/dev 이름이 섞여 있으므로, 실제 운영 배포를 Actions에 맡기기 전에 `APP_NAME`, 서버, 포트, 원격 deploy script를 함께 검증한다.

## Verify

배포 직후 health check:

```bash
curl -i http://poppang.co.kr:4002/actuator/health
```

기대값:

```json
{"status":"UP"}
```

서버 컨테이너 확인:

```bash
ssh poppang-server "docker ps --filter name=poppang-prod --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'"
```

최근 로그 확인:

```bash
ssh poppang-server "docker logs --since 2m poppang-prod | grep -E 'Started|ERROR|Exception|WARN' | tail -80"
```

확인 포인트:

- `Started PoppangBeApplication`이 보인다.
- 컨테이너가 반복 재시작하지 않는다.
- 외부 포트 `4002`가 컨테이너 `8080`으로 연결된다.
- 제보 이미지 저장 경로가 volume으로 유지된다.
- 커스텀 Swagger 경로가 필요하면 기본 `/swagger-ui`가 아니라 `application.yml`의 `springdoc.swagger-ui.path`를 확인한다.

## Tag

배포가 정상 확인된 뒤 release tag를 만든다.

```bash
git tag -a 1.2.4 -m "Release 1.2.4"
git push origin 1.2.4
```

이미 태그가 있으면 새로 만들지 말고 현재 태그가 어느 커밋을 가리키는지 먼저 확인한다.

```bash
git show --no-patch --decorate --oneline 1.2.4
```

## Rollback

health check 실패, 컨테이너 반복 재시작, 주요 endpoint 장애가 확인되면 원인 분석보다 먼저 직전 정상 이미지로 복구한다.

서버에 있는 기존 tar와 image name을 확인한다.

```bash
ssh poppang-server "ls -lh /home/poppang/opt/deploy/*prod*.tar | tail"
ssh poppang-server "docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.CreatedSince}}\t{{.Size}}' | grep '^poppang-prod'"
```

직전 정상 tar/image로 deploy script를 다시 실행한다.

```bash
ssh poppang-server "bash /home/poppang/opt/deploy/deploy-prod.sh /home/poppang/opt/deploy/poppang-prod-이전버전.tar poppang-prod:이전버전"
```

이후 다시 health check를 확인한다.

```bash
curl -i http://poppang.co.kr:4002/actuator/health
```

## Known Operational Risks

- `.dockerignore`가 없다. Docker build context에 `.env`, private config, `.p8`, tar 산출물이 포함되지 않도록 주의한다.
- Dockerfile은 JAR만 복사하지만, 빌드된 JAR 안에 private config와 Apple key가 포함될 수 있다.
- 원격 `deploy-prod.sh`가 저장소에 없어 런북만으로 container run 옵션을 검증할 수 없다.
- 로컬 makefile과 GitHub Actions가 받는 private config 파일 목록이 다르다.
- GitHub Actions CD는 `poppang-dev` image name과 `deploy-prod.sh` 호출이 혼재한다.
- 제보 이미지가 컨테이너 내부 파일시스템에만 저장되면 재배포/롤백 때 유실될 수 있다.
