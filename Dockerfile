# Multi-stage build: Maven compiles the Spring Boot application; runtime uses a smaller JRE image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package && cp target/morrow-wallet-*.jar target/morrow-app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
COPY --from=build /app/target/morrow-app.jar /app/morrow-app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/morrow-app.jar"]
