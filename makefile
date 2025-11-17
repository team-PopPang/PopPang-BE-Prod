# ===== 공통 변수 =====
APP_NAME        := poppang-prod
VERSION         := 1.0.0 ### 버전에 맞게 계속 바꿔줘야함 (이 부분만 수정하기)###
IMAGE_NAME      := $(APP_NAME):$(VERSION)
IMAGE_TAR       := $(APP_NAME)-$(VERSION).tar

# 서버 정보
SERVER_USER     := poppang
SERVER_HOST     := 183.103.19.203
SERVER_DIR      := /home/poppang/opt/deploy

# ===== 1. JAR 빌드 =====
build-jar:
	./gradlew clean bootJar

# ===== 2. Docker 이미지 빌드 (prod용) =====
build-image: build-jar
	docker buildx build --platform linux/amd64 -t $(IMAGE_NAME) --load .

# ===== 3. Docker 이미지 tar로 저장 =====
save-image: build-image
	docker save -o $(IMAGE_TAR) $(IMAGE_NAME)

# ===== 4. 서버로 tar 전송 =====
send-image: save-image
	scp $(IMAGE_TAR) $(SERVER_USER)@$(SERVER_HOST):$(SERVER_DIR)/

# ===== 5. 서버에서 이미지 로드 + 컨테이너 재시작 =====
remote-deploy:
	ssh $(SERVER_USER)@$(SERVER_HOST) "bash /home/poppang/opt/deploy/deploy-prod.sh $(SERVER_DIR)/$(IMAGE_TAR) $(IMAGE_NAME)"

# ===== 6. 풀 파이프라인 (FE가 쓸 메인 명령어) =====
prod-deploy: send-image remote-deploy
	@echo "✅ prod 배포 완료: $(IMAGE_NAME)"