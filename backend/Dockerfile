# Build stage for production
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

# Production stage
FROM eclipse-temurin:17-jre AS production
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Development stage
FROM eclipse-temurin:17-jdk AS development
WORKDIR /app

# Install necessary tools
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy Maven wrapper and make it executable
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x ./mvnw

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

EXPOSE 8080 5005
CMD ["./mvnw", "spring-boot:run"]
