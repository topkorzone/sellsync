# syntax=docker/dockerfile:1

############################################
# 1) Build Stage
############################################
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle 캐시 최적화를 위한 사전 복사
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle* settings.gradle* ./

# 소스 복사
COPY src ./src

# 빌드
RUN chmod +x ./gradlew \
    && ./gradlew clean bootJar -x test

############################################
# 2) Runtime Stage
############################################
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_OPTS=""

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
