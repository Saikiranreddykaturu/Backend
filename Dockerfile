FROM node:22-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./src/main/resources/static/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=backend-build /workspace/target/backend-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]