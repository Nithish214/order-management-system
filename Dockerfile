# Stage 1: compile with Maven (this stage's ~500MB+ never ends up in the final image)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: a JRE (not a full JDK) is enough to run the already-built jar, and is
# noticeably smaller -- worth it on a resource-constrained t3.micro.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
# JAVA_OPTS is empty by default (local `docker run`) but set to conservative heap limits
# in docker-compose.prod.yml, since 4 JVMs share a t3.micro's 1GB of RAM.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
