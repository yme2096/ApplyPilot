FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 applypilot \
    && useradd --system --uid 10001 --gid applypilot applypilot \
    && mkdir -p /app/data/uploads /app/ai/applypilot/config \
    && chown -R applypilot:applypilot /app/data
COPY --from=build /workspace/target/applypilot-api-0.1.0.jar /app/app.jar
COPY --from=build /workspace/target/classes/ai/applypilot/config/HealthProbe.class /app/ai/applypilot/config/HealthProbe.class
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
