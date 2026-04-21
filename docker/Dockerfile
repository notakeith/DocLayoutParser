FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY . .

RUN ./gradlew --no-daemon clean build

EXPOSE 8080

CMD java -cp "build/classes/java/main" org.sparklingduo.App