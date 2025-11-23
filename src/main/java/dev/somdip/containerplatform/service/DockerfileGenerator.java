package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DockerfileGenerator {
    private static final Logger log = LoggerFactory.getLogger(DockerfileGenerator.class);

    public String generateDockerfile(ProjectAnalyzer.ProjectInfo projectInfo) {
        log.info("Generating Dockerfile for project type: {}", projectInfo.getType());

        return switch (projectInfo.getType()) {
            case NODEJS -> generateNodeJsDockerfile(projectInfo);
            case PYTHON -> generatePythonDockerfile(projectInfo);
            case JAVA_MAVEN -> generateJavaMavenDockerfile(projectInfo);
            case JAVA_GRADLE -> generateJavaGradleDockerfile(projectInfo);
            case GO -> generateGoDockerfile(projectInfo);
            case PHP -> generatePhpDockerfile(projectInfo);
            case RUBY -> generateRubyDockerfile(projectInfo);
            case DOTNET -> generateDotNetDockerfile(projectInfo);
            case STATIC_HTML -> generateStaticHtmlDockerfile(projectInfo);
            default -> throw new IllegalArgumentException("Unsupported project type: " + projectInfo.getType());
        };
    }

    private String generateNodeJsDockerfile(ProjectAnalyzer.ProjectInfo info) {
        String packageManager = info.getPackageManager();
        boolean isYarn = "yarn".equals(packageManager);
        boolean isPnpm = "pnpm".equals(packageManager);

        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Multi-stage build for Node.js application\n");
        dockerfile.append("FROM node:18-alpine AS builder\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Copy package files
        if (isPnpm) {
            dockerfile.append("# Enable pnpm\n");
            dockerfile.append("RUN npm install -g pnpm\n\n");
            dockerfile.append("COPY pnpm-lock.yaml package.json ./\n");
            dockerfile.append("RUN pnpm install --frozen-lockfile\n\n");
        } else if (isYarn) {
            dockerfile.append("COPY yarn.lock package.json ./\n");
            dockerfile.append("RUN yarn install --frozen-lockfile\n\n");
        } else {
            dockerfile.append("COPY package*.json ./\n");
            dockerfile.append("RUN npm ci\n\n");
        }

        // Copy source and build
        dockerfile.append("COPY . .\n");

        // Add build step if needed
        String framework = info.getMetadata().get("framework");
        if ("Next.js".equals(framework)) {
            dockerfile.append("RUN npm run build\n\n");
        }

        // Production stage
        dockerfile.append("# Production stage\n");
        dockerfile.append("FROM node:18-alpine\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Create non-root user
        dockerfile.append("RUN addgroup -g 1001 -S nodejs && adduser -S nodejs -u 1001\n\n");

        // Copy from builder
        if ("Next.js".equals(framework)) {
            dockerfile.append("COPY --from=builder --chown=nodejs:nodejs /app/.next ./.next\n");
            dockerfile.append("COPY --from=builder --chown=nodejs:nodejs /app/node_modules ./node_modules\n");
            dockerfile.append("COPY --from=builder --chown=nodejs:nodejs /app/package.json ./package.json\n");
            dockerfile.append("COPY --from=builder --chown=nodejs:nodejs /app/public ./public\n\n");
        } else {
            dockerfile.append("COPY --from=builder --chown=nodejs:nodejs /app ./\n\n");
        }

        dockerfile.append("USER nodejs\n\n");
        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \\\n");
        dockerfile.append("  CMD node -e \"require('http').get('http://localhost:")
                .append(info.getPort())
                .append("/health', (r) => {process.exit(r.statusCode === 200 ? 0 : 1)})\"\n\n");

        dockerfile.append("CMD [\"").append(info.getStartCommand() != null ? info.getStartCommand() : "npm start").append("\"]\n");

        return dockerfile.toString();
    }

    private String generatePythonDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Python application Dockerfile\n");
        dockerfile.append("FROM python:3.11-slim\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Install system dependencies
        dockerfile.append("RUN apt-get update && apt-get install -y --no-install-recommends \\\n");
        dockerfile.append("    gcc \\\n");
        dockerfile.append("    && rm -rf /var/lib/apt/lists/*\n\n");

        // Install Python dependencies
        String packageManager = info.getPackageManager();
        if ("poetry".equals(packageManager)) {
            dockerfile.append("RUN pip install --no-cache-dir poetry\n");
            dockerfile.append("COPY pyproject.toml poetry.lock* ./\n");
            dockerfile.append("RUN poetry config virtualenvs.create false \\\n");
            dockerfile.append("    && poetry install --no-dev --no-interaction --no-ansi\n\n");
        } else if ("pipenv".equals(packageManager)) {
            dockerfile.append("RUN pip install --no-cache-dir pipenv\n");
            dockerfile.append("COPY Pipfile Pipfile.lock ./\n");
            dockerfile.append("RUN pipenv install --system --deploy\n\n");
        } else {
            dockerfile.append("COPY requirements.txt .\n");
            dockerfile.append("RUN pip install --no-cache-dir -r requirements.txt\n\n");
        }

        // Copy application
        dockerfile.append("COPY . .\n\n");

        // Create non-root user
        dockerfile.append("RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app\n");
        dockerfile.append("USER appuser\n\n");

        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \\\n");
        dockerfile.append("  CMD python -c \"import urllib.request; urllib.request.urlopen('http://localhost:")
                .append(info.getPort())
                .append("/health').read()\"\n\n");

        String startCommand = info.getStartCommand() != null ? info.getStartCommand() : "python app.py";
        dockerfile.append("CMD [\"sh\", \"-c\", \"").append(startCommand).append("\"]\n");

        return dockerfile.toString();
    }

    private String generateJavaMavenDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Multi-stage build for Java Maven application\n");
        dockerfile.append("FROM eclipse-temurin:17-jdk-alpine AS builder\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Copy Maven files
        dockerfile.append("COPY pom.xml ./\n");
        dockerfile.append("COPY .mvn .mvn\n");
        dockerfile.append("COPY mvnw ./\n");
        dockerfile.append("RUN chmod +x mvnw\n\n");

        // Download dependencies
        dockerfile.append("RUN ./mvnw dependency:go-offline -B\n\n");

        // Copy source and build
        dockerfile.append("COPY src ./src\n");
        dockerfile.append("RUN ./mvnw clean package -DskipTests\n\n");

        // Production stage
        dockerfile.append("# Production stage\n");
        dockerfile.append("FROM eclipse-temurin:17-jre-alpine\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Create non-root user
        dockerfile.append("RUN addgroup -g 1000 appgroup && adduser -D -u 1000 -G appgroup appuser\n\n");

        // Copy JAR
        dockerfile.append("COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar\n\n");

        dockerfile.append("USER appuser\n\n");
        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \\\n");
        dockerfile.append("  CMD wget --quiet --tries=1 --spider http://localhost:")
                .append(info.getPort())
                .append("/actuator/health || exit 1\n\n");

        dockerfile.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");

        return dockerfile.toString();
    }

    private String generateJavaGradleDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Multi-stage build for Java Gradle application\n");
        dockerfile.append("FROM eclipse-temurin:17-jdk-alpine AS builder\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Copy Gradle files
        dockerfile.append("COPY build.gradle settings.gradle ./\n");
        dockerfile.append("COPY gradle gradle\n");
        dockerfile.append("COPY gradlew ./\n");
        dockerfile.append("RUN chmod +x gradlew\n\n");

        // Download dependencies
        dockerfile.append("RUN ./gradlew dependencies --no-daemon\n\n");

        // Copy source and build
        dockerfile.append("COPY src ./src\n");
        dockerfile.append("RUN ./gradlew build -x test --no-daemon\n\n");

        // Production stage
        dockerfile.append("# Production stage\n");
        dockerfile.append("FROM eclipse-temurin:17-jre-alpine\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Create non-root user
        dockerfile.append("RUN addgroup -g 1000 appgroup && adduser -D -u 1000 -G appgroup appuser\n\n");

        // Copy JAR
        dockerfile.append("COPY --from=builder --chown=appuser:appgroup /app/build/libs/*.jar app.jar\n\n");

        dockerfile.append("USER appuser\n\n");
        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \\\n");
        dockerfile.append("  CMD wget --quiet --tries=1 --spider http://localhost:")
                .append(info.getPort())
                .append("/actuator/health || exit 1\n\n");

        dockerfile.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");

        return dockerfile.toString();
    }

    private String generateGoDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Multi-stage build for Go application\n");
        dockerfile.append("FROM golang:1.21-alpine AS builder\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Copy go mod files
        dockerfile.append("COPY go.mod go.sum ./\n");
        dockerfile.append("RUN go mod download\n\n");

        // Copy source and build
        dockerfile.append("COPY . .\n");
        dockerfile.append("RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .\n\n");

        // Production stage
        dockerfile.append("# Production stage\n");
        dockerfile.append("FROM alpine:latest\n\n");
        dockerfile.append("RUN apk --no-cache add ca-certificates\n\n");

        dockerfile.append("WORKDIR /root/\n\n");

        // Copy binary
        dockerfile.append("COPY --from=builder /app/main .\n\n");

        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \\\n");
        dockerfile.append("  CMD wget --quiet --tries=1 --spider http://localhost:")
                .append(info.getPort())
                .append("/health || exit 1\n\n");

        dockerfile.append("CMD [\"./main\"]\n");

        return dockerfile.toString();
    }

    private String generatePhpDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# PHP application Dockerfile\n");
        dockerfile.append("FROM php:8.2-fpm-alpine\n\n");
        dockerfile.append("WORKDIR /var/www/html\n\n");

        // Install dependencies
        dockerfile.append("RUN apk add --no-cache nginx supervisor\n\n");

        // Install PHP extensions
        dockerfile.append("RUN docker-php-ext-install pdo pdo_mysql\n\n");

        // Install Composer
        dockerfile.append("COPY --from=composer:latest /usr/bin/composer /usr/bin/composer\n\n");

        // Copy application
        dockerfile.append("COPY . .\n");
        dockerfile.append("RUN composer install --no-dev --optimize-autoloader\n\n");

        // Set permissions
        dockerfile.append("RUN chown -R www-data:www-data /var/www/html\n\n");

        dockerfile.append("EXPOSE 80\n\n");

        dockerfile.append("CMD [\"php-fpm\"]\n");

        return dockerfile.toString();
    }

    private String generateRubyDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Ruby application Dockerfile\n");
        dockerfile.append("FROM ruby:3.2-alpine\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Install dependencies
        dockerfile.append("RUN apk add --no-cache build-base postgresql-dev nodejs yarn\n\n");

        // Install gems
        dockerfile.append("COPY Gemfile Gemfile.lock ./\n");
        dockerfile.append("RUN bundle install --without development test\n\n");

        // Copy application
        dockerfile.append("COPY . .\n\n");

        // Precompile assets if Rails
        if ("Ruby on Rails".equals(info.getMetadata().get("framework"))) {
            dockerfile.append("RUN bundle exec rails assets:precompile\n\n");
        }

        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        dockerfile.append("CMD [\"bundle\", \"exec\", \"rails\", \"server\", \"-b\", \"0.0.0.0\"]\n");

        return dockerfile.toString();
    }

    private String generateDotNetDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Multi-stage build for ASP.NET Core application\n");
        dockerfile.append("FROM mcr.microsoft.com/dotnet/sdk:7.0-alpine AS builder\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        // Restore dependencies
        dockerfile.append("COPY *.csproj ./\n");
        dockerfile.append("RUN dotnet restore\n\n");

        // Copy source and build
        dockerfile.append("COPY . .\n");
        dockerfile.append("RUN dotnet publish -c Release -o out\n\n");

        // Production stage
        dockerfile.append("# Production stage\n");
        dockerfile.append("FROM mcr.microsoft.com/dotnet/aspnet:7.0-alpine\n\n");
        dockerfile.append("WORKDIR /app\n\n");

        dockerfile.append("COPY --from=builder /app/out .\n\n");

        dockerfile.append("EXPOSE ").append(info.getPort()).append("\n\n");

        dockerfile.append("ENTRYPOINT [\"dotnet\", \"*.dll\"]\n");

        return dockerfile.toString();
    }

    private String generateStaticHtmlDockerfile(ProjectAnalyzer.ProjectInfo info) {
        StringBuilder dockerfile = new StringBuilder();
        dockerfile.append("# Static HTML Dockerfile\n");
        dockerfile.append("FROM nginx:alpine\n\n");

        // Copy custom nginx config if needed
        dockerfile.append("# Copy custom nginx configuration\n");
        dockerfile.append("RUN echo 'server { listen 80; root /usr/share/nginx/html; index index.html; location / { try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf\n\n");

        // Copy static files
        dockerfile.append("COPY . /usr/share/nginx/html\n\n");

        dockerfile.append("EXPOSE 80\n\n");

        // Health check
        dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1\n\n");

        dockerfile.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");

        return dockerfile.toString();
    }

    public void writeDockerfile(Path projectPath, String dockerfileContent) throws IOException {
        Path dockerfilePath = projectPath.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);
        log.info("Dockerfile written to: {}", dockerfilePath);
    }
}
