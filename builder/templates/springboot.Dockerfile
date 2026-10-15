# DeployHub template for Spring Boot (Maven). Used when the repo has no Dockerfile of its own.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q package -DskipTests \
 && find target -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /app.jar app.jar
USER 1001
EXPOSE 8080
# DeployHub injects PORT (the port the user registered). A -D system property beats
# application.properties, so the app listens there even if it never reads PORT itself.
# sh -c expands the variable; exec keeps java as PID 1 so it receives SIGTERM.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"]
