# Cloud container: Java 21 + modern Oracle JDBC driver for an Oracle Autonomous DB endpoint.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -Pcloud -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin morrow
COPY --from=build --chown=morrow:morrow /workspace/target/morrow-wallet-0.1.0-SNAPSHOT.jar /app/app.jar
USER morrow
ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
