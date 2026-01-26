# ---- build stage ----
    FROM eclipse-temurin:21-jdk AS build
    WORKDIR /app
    
    # api-server 디렉터리가 build context 루트가 됨
    COPY . .
    
    # gradle wrapper가 있으면 이 방식이 가장 안정적
    RUN chmod +x ./gradlew && ./gradlew clean bootJar -x test
    
    # ---- run stage ----
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    COPY --from=build /app/build/libs/*.jar /app/app.jar
    
    EXPOSE 8080
    ENTRYPOINT ["java","-jar","/app/app.jar"]
    