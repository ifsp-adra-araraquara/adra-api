FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN adduser -S -u 10001 adra
ENV TZ=UTC
WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar
USER adra
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
