FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon

RUN mkdir -p /app/build/libs
RUN cp $(find /app/build/libs -name "*.jar" ! -name "*-plain.jar") /app/app.jar || true

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]