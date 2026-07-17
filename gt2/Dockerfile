# Stage 1: build the React/TypeScript UI
FROM node:20-alpine AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Stage 2: build the Spring Boot jar with the UI baked into static resources
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=ui /ui/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# Stage 3: slim runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/grindtrack.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
