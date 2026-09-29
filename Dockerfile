# Build stage: compile the Spring Boot JAR with Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first so they are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI; skipping here keeps the image build independent of test config
RUN mvn -B -q package -DskipTests

# Runtime stage: JRE only
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 app
COPY --from=build /app/target/*.jar app.jar
USER app

# Respect the container memory limit instead of the host's RAM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
