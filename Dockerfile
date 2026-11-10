FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/app
COPY settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
COPY --from=build /home/app/build/libs/*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
