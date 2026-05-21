# Build stage
FROM gradle:9-jdk25 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src src
RUN gradle fatJar --no-daemon

# Run stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
