# =========================================
# 🔐 .env 자동 include (핵심 부분)
# =========================================
ifneq (,$(wildcard .env))
include .env
export
endif

# =========================================
# 🚀 PopPang BE PROD 배포용 Makefile
# =========================================

.DEFAULT_GOAL := all

# ===== 공통 변수 =====
APP_NAME        := poppang-dev
VERSION         := 1.1.4
IMAGE_NAME      := $(APP_NAME):$(VERSION)
IMAGE_TAR       := $(APP_NAME)-$(VERSION).tar

# ===== 서버 설정 =====
# SSH_HOST: ~/.ssh/config 에 설정해둔 Host 별칭
SSH_HOST        := poppang-server
SERVER_DIR      := /home/poppang/opt/deploy

# ===== Private Repository 설정 =====
PRIVATE_REPO       := team-PopPang/PopPang-Private
PRIVATE_BRANCH     := BE
PRIVATE_BASE_URL   := https://raw.githubusercontent.com/$(PRIVATE_REPO)/$(PRIVATE_BRANCH)

# ===== PHONY =====
.PHONY: all getKey reboot build-jar build-image save-image send-image remote-deploy dev-deploy

# =========================================
# 🔐 Private 파일 다운로드 함수
# $(1): 디렉토리, $(2): 파일명
# =========================================
define download_file
	mkdir -p $(1) && \
	curl -s -H "Authorization: Bearer $(GITHUB_ACCESS_TOKEN)" \
	     -o $(1)/$(2) \
	     $(PRIVATE_BASE_URL)/$(1)/$(2)
endef

# =========================================
# 🔐 GitHub Token 로딩 + Private 파일 다운로드
# =========================================
getKey:
	@echo "🔐 Checking GitHub token..."
	@if [ ! -f .env ]; then \
		read -p "Enter GitHub Access Token: " token; \
		echo "GITHUB_ACCESS_TOKEN=$$token" > .env; \
	fi
	@echo "🔐 Downloading private files..."
	@set -a && . .env && set +a && \
	$(call download_file,src/main/resources/auth,AuthKey_382T2TB4RW.p8) && \
	$(call download_file,src/main/resources,application-dev.yml) && \
	$(call download_file,src/main/resources,application-local.yml)
	@echo "✅ download completed."

# =========================================
# 🧩 기본(make) 동작: getKey + dev-deploy
#   → "시크릿 가져오고 + 재배포"
# =========================================
all: getKey dev-deploy
	@echo "🎉 모든 작업 완료 (getKey + dev-deploy)"

# make 만 쳐도 all 이 실행됨
default: all

# =========================================
# 🟢 실제 배포 파이프라인(dev-deploy)
# =========================================

# 1. JAR 빌드
build-jar:
	./gradlew clean bootJar

# 2. Docker 이미지 빌드 (dev용)
build-image: build-jar
	docker buildx build --platform linux/amd64 -t $(IMAGE_NAME) --load .

# 3. Docker 이미지 tar 로 저장
save-image: build-image
	docker save -o $(IMAGE_TAR) $(IMAGE_NAME)

# 4. 서버로 tar 전송
send-image: save-image
	scp $(IMAGE_TAR) $(SSH_HOST):$(SERVER_DIR)/

# 5. 서버에서 이미지 로드 + 컨테이너 재시작
remote-deploy:
	ssh $(SSH_HOST) "bash $(SERVER_DIR)/deploy-dev.sh $(SERVER_DIR)/$(IMAGE_TAR) $(IMAGE_NAME)"

# 6. 전체 배포 파이프라인
dev-deploy: send-image remote-deploy
	@echo ""
	@echo "🎉🎉🎉===================================="
	@echo "   🚀 DEV 배포 완료!"
	@echo "   이미지: $(IMAGE_NAME)"
	@echo "====================================🎉🎉🎉"