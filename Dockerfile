FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S -g 1001 wisekoin \
    && adduser -S -D -H -u 1001 -G wisekoin wisekoin

WORKDIR /app

COPY --from=build --chown=wisekoin:wisekoin /app/target/backend-*.jar /app/app.jar

USER wisekoin:wisekoin

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Xmx384m -Xms128m -Duser.timezone=Asia/Ho_Chi_Minh"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
