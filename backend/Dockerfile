# Backend image: build with Maven (wrapper) on a JDK, run on a slim Java 21 JRE.

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Download dependencies first so they are cached when only the source changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY src src
# Tests need Postgres + Kafka; they are run separately (.\mvnw.cmd test), not during the image build.
RUN sh mvnw -B -q -DskipTests package && cp target/claims-*.jar app.jar

# ---- run ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S claims && adduser -S claims -G claims
COPY --from=build /app/app.jar app.jar
USER claims
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
