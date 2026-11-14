FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system mpos && useradd --system --gid mpos mpos
COPY --from=build /build/target/mpos-api-0.1.0-SNAPSHOT.jar app.jar
USER mpos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
