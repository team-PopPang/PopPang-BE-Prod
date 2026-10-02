FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
# 힙 상한을 명시한다. 컨테이너 메모리 감지는 JVM 기본값(켜짐)을 그대로 쓴다.
ENV JAVA_TOOL_OPTIONS="-Xmx1g"

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
