# syntax=docker/dockerfile:1

############################################
# 1) Build stage
############################################
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/aip-server

# Gradle wrapper & build scripts 먼저 복사 (캐시 최적화)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* gradle.properties ./

# 소스 복사
COPY src ./src

# 실행권한 + 빌드
RUN chmod +x ./gradlew \
  && ./gradlew clean bootJar -x test

############################################
# 2) Runtime stage
############################################
FROM eclipse-temurin:21-jre
WORKDIR /app

# (선택) 컨테이너 TZ
ENV TZ=Asia/Seoul

# 빌드 결과 복사
COPY --from=build /app/build/libs/*.jar /app/api-server/app.jar

# Render가 PORT를 주입하므로 EXPOSE는 문서용(선택)
EXPOSE 8080

# JVM 옵션은 Render ENV(JAVA_OPTS)로도 오버라이드 가능
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
