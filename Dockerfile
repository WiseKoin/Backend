# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build package without running unit tests (to speed up build)
RUN mvn clean package -DskipTests

# Stage 2: Lightweight JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Render assigns port dynamically via $PORT
EXPOSE 8080

# Optimize JVM memory for Render Free Tier (512MB RAM)
ENV JAVA_TOOL_OPTIONS="-Xmx384m -Xms128m -Duser.timezone=Asia/Ho_Chi_Minh"

ENTRYPOINT ["sh", "-c", "java $JAVA_TOOL_OPTIONS -Dserver.port=${PORT:-8080} -jar app.jar"]
