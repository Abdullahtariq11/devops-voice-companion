FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/devops-voice-companion-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]