# Stage 1: compile with Maven (this stage's ~500MB+ never ends up in the final image)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: a JRE (not a full JDK) is enough to run the already-built jar, and is
# noticeably smaller.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
# JAVA_OPTS is empty by default (local `docker run`) but set to a conservative heap limit
# in docker-compose.prod.yml, same reasoning as the other 3 JVMs sharing that box.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
