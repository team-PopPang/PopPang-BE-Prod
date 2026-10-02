# =========================================
# 🚀 PopPang BE 로컬 빌드용 Makefile
#   - 운영 설정·Apple 키는 서버 런타임에서 주입하므로 private 파일을 내려받지 않는다.
#   - 운영(EC2) 배포는 main의 GitHub Actions(.github/workflows/cicd.yml)로만 한다.
#   - makefile로는 운영 서버(미니 PC 포함)에 배포하지 않는다.
# =========================================

.DEFAULT_GOAL := help

# ===== 공통 변수 =====
APP_NAME        := poppang-prod
VERSION         := 1.2.3
IMAGE_NAME      := $(APP_NAME):$(VERSION)
IMAGE_TAR       := $(APP_NAME)-$(VERSION).tar

# ===== PHONY =====
.PHONY: help build-jar build-image save-image send-image remote-deploy prod-deploy

help:
	@echo "로컬 빌드 타깃: build-jar, build-image, save-image (VERSION=x.y.z 명시)"
	@echo "운영 배포는 main의 GitHub Actions로만 합니다."

# =========================================
# 🟢 로컬 빌드 + 산출물 검사
# =========================================

# 1. JAR 빌드
build-jar:
	./gradlew clean bootJar
	bash scripts/ci/verify-build-artifacts.sh jar build/libs/*.jar

# 2. Docker 이미지 빌드 (prod용, linux/amd64)
build-image: build-jar
	docker buildx build --platform linux/amd64 -t $(IMAGE_NAME) --load .
	bash scripts/ci/verify-build-artifacts.sh image $(IMAGE_NAME)

# 3. Docker 이미지 tar 로 저장
save-image: build-image
	docker save -o $(IMAGE_TAR) $(IMAGE_NAME)

# =========================================
# ⛔ 수동 운영 배포 경로 차단 (AWS용 코드를 미니 PC 운영에 배포하지 않는다)
# =========================================
send-image remote-deploy: prod-deploy

prod-deploy:
	@echo "❌ makefile로 운영 서버에 배포하지 않습니다. 운영(EC2) 배포는 main의 GitHub Actions(cicd.yml)로만 합니다."
	@exit 1
