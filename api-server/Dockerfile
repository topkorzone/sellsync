# syntax=docker/dockerfile:1

############################################
# 1) Build Stage
############################################
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 1) Gradle wrapper / 설정 (레포 루트 기준)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* ./

# 2) api-server 모듈의 build 설정 + 소스
COPY api-server/build.gradle* api-server/settings.gradle* ./api-server/
COPY api-server/src ./api-server/src

# 빌드 (api-server 모듈만)
RUN chmod +x ./gradlew \
    && ./gradlew :api-server:clean :api-server:bootJar -x test

############################################
# 2) Runtime Stage
############################################
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_OPTS=""

# api-server 산출물 복사
COPY --from=build /app/api-server/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
