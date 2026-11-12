# Etapa 1: compilar con Maven y Java 25
FROM maven:3.9-eclipse-temurin-25 AS compilar
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Etapa 2: solo lo necesario para correr
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=compilar /src/target/quarkus-app/ ./
EXPOSE 8080
USER 1001
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
