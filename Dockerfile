# Multi-stage, parametrized build shared by every microservice.
# Usage (see docker-compose.yml):
#   build:
#     context: .
#     dockerfile: Dockerfile
#     args: { SERVICE: dispatch-service }

FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/app
COPY settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY services ./services
ARG SERVICE
RUN gradle :services:${SERVICE}:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre AS runtime
ARG SERVICE
WORKDIR /app
COPY --from=build /home/app/services/${SERVICE}/build/libs/*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
