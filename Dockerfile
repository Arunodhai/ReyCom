FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S reycom && adduser -S reycom -G reycom

COPY --from=build /workspace/target/*.jar app.jar

USER reycom

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
