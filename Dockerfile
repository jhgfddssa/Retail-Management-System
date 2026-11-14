FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw ./mvnw
COPY backend/pom.xml backend/pom.xml
COPY backend/src/ backend/src/
RUN chmod +x ./mvnw \
    && ./mvnw -B -ntp -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/target/retail-management-api-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
