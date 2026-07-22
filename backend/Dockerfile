FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew

RUN ./gradlew --no-daemon dependencies

COPY src ./src

RUN ./gradlew --no-daemon bootJar


FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system fuelfinder \
    && useradd \
        --system \
        --gid fuelfinder \
        --home-dir /app \
        --shell /usr/sbin/nologin \
        fuelfinder

COPY --from=build \
    --chown=fuelfinder:fuelfinder \
    /workspace/build/libs/app.jar \
    /app/app.jar

USER fuelfinder

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]