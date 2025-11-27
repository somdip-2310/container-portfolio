package dev.somdip.containerplatform.service.php;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates optimized Dockerfiles for PHP applications based on detected framework
 */
@Slf4j
@Service
public class PHPDockerfileGenerator {

    private final String ecrRegistry;

    public PHPDockerfileGenerator(@Value("${aws.ecr.baseImages.registry}") String ecrRegistry) {
        this.ecrRegistry = ecrRegistry;
    }

    /**
     * Generate Dockerfile for the detected PHP application
     */
    public void generateDockerfile(Path sourceDirectory, PHPApplicationInfo appInfo) throws IOException {
        log.info("Generating Dockerfile for {} application", appInfo.getFramework().getDisplayName());

        String dockerfileContent = buildDockerfileContent(appInfo);
        Path dockerfilePath = sourceDirectory.resolve("Dockerfile");
        Files.writeString(dockerfilePath, dockerfileContent);

        log.info("Dockerfile generated at: {}", dockerfilePath);
    }

    private String buildDockerfileContent(PHPApplicationInfo appInfo) {
        StringBuilder dockerfile = new StringBuilder();

        // Base image selection - using AWS Public ECR to avoid Docker Hub rate limits
        // AWS Public ECR doesn't have rate limits and mirrors official Docker Hub images
        String baseImage = "public.ecr.aws/docker/library/php:" + appInfo.getPhpVersion() + "-fpm";
        dockerfile.append("FROM ").append(baseImage).append("\n\n");

        // Install system dependencies
        dockerfile.append("# Install system dependencies and nginx\n");
        dockerfile.append("RUN apt-get update && apt-get install -y \\\n");
        dockerfile.append("    nginx \\\n");
        dockerfile.append("    libpng-dev \\\n");
        dockerfile.append("    libonig-dev \\\n");
        dockerfile.append("    libxml2-dev \\\n");
        dockerfile.append("    libzip-dev \\\n");
        dockerfile.append("    zip \\\n");
        dockerfile.append("    unzip \\\n");
        dockerfile.append("    git \\\n");
        dockerfile.append("    curl \\\n");
        dockerfile.append("    && rm -rf /var/lib/apt/lists/*\n\n");

        // Install PHP extensions
        dockerfile.append("# Install PHP extensions\n");
        dockerfile.append("RUN docker-php-ext-install pdo_mysql mbstring exif pcntl bcmath gd zip\n\n");

        // Install Composer if needed
        if (appInfo.isRequiresComposer()) {
            dockerfile.append("# Install Composer\n");
            // Use ECR composer image to avoid Docker Hub rate limits
            dockerfile.append("COPY --from=" + ecrRegistry + "/base-images/composer:latest /usr/bin/composer /usr/bin/composer\n\n");
        }

        // Set working directory
        dockerfile.append("# Set working directory\n");
        dockerfile.append("WORKDIR /var/www/html\n\n");

        // Copy application files
        dockerfile.append("# Copy application files\n");
        dockerfile.append("COPY . /var/www/html\n\n");

        // Framework-specific setup
        switch (appInfo.getFramework()) {
            case LARAVEL:
                dockerfile.append("# Laravel specific setup\n");
                if (appInfo.isRequiresComposer()) {
                    dockerfile.append("RUN composer install --no-dev --optimize-autoloader --no-interaction\n");
                }
                dockerfile.append("RUN chown -R www-data:www-data /var/www/html/storage /var/www/html/bootstrap/cache\n");
                dockerfile.append("RUN chmod -R 775 /var/www/html/storage /var/www/html/bootstrap/cache\n\n");
                break;

            case SYMFONY:
                dockerfile.append("# Symfony specific setup\n");
                if (appInfo.isRequiresComposer()) {
                    dockerfile.append("RUN composer install --no-dev --optimize-autoloader --no-interaction\n");
                }
                dockerfile.append("RUN chown -R www-data:www-data /var/www/html/var\n");
                dockerfile.append("RUN chmod -R 775 /var/www/html/var\n\n");
                break;

            case WORDPRESS:
                dockerfile.append("# WordPress specific setup\n");
                dockerfile.append("RUN chown -R www-data:www-data /var/www/html\n\n");
                break;

            default:
                if (appInfo.isRequiresComposer()) {
                    dockerfile.append("# Install dependencies\n");
                    dockerfile.append("RUN composer install --no-dev --optimize-autoloader --no-interaction\n\n");
                }
                dockerfile.append("# Set permissions\n");
                dockerfile.append("RUN chown -R www-data:www-data /var/www/html\n\n");
                break;
        }

        // Copy nginx configuration
        dockerfile.append("# Copy nginx configuration\n");
        dockerfile.append("COPY nginx.conf /etc/nginx/nginx.conf\n");
        dockerfile.append("COPY default.conf /etc/nginx/conf.d/default.conf\n\n");

        // Create nginx directories and test configuration
        dockerfile.append("# Create nginx directories and test configuration\n");
        dockerfile.append("RUN mkdir -p /var/log/nginx /var/lib/nginx /var/run \\\n");
        dockerfile.append("    && chown -R www-data:www-data /var/log/nginx /var/lib/nginx \\\n");
        dockerfile.append("    && nginx -t\n\n");

        // Copy startup script
        dockerfile.append("# Copy startup script\n");
        dockerfile.append("COPY start.sh /start.sh\n");
        dockerfile.append("RUN chmod +x /start.sh\n\n");

        // Expose port
        dockerfile.append("# Expose port 8000\n");
        dockerfile.append("EXPOSE 8000\n\n");

        // Start command
        dockerfile.append("# Start services\n");
        dockerfile.append("CMD [\"/start.sh\"]\n");

        return dockerfile.toString();
    }

    /**
     * Generate startup script that starts both PHP-FPM and nginx
     */
    public void generateStartupScript(Path sourceDirectory) throws IOException {
        log.info("Generating startup script");

        String scriptContent = """
                #!/bin/bash
                set -e

                echo "Starting PHP-FPM and nginx..."

                # Start PHP-FPM in background
                echo "Starting PHP-FPM..."
                php-fpm -D
                if [ $? -eq 0 ]; then
                    echo "PHP-FPM started successfully"
                else
                    echo "ERROR: PHP-FPM failed to start"
                    exit 1
                fi

                # Wait a moment for PHP-FPM to initialize
                sleep 2

                # Verify PHP-FPM is running
                if ! pgrep -x php-fpm > /dev/null; then
                    echo "ERROR: PHP-FPM is not running"
                    exit 1
                fi

                # Test nginx configuration
                echo "Testing nginx configuration..."
                nginx -t
                if [ $? -ne 0 ]; then
                    echo "ERROR: nginx configuration test failed"
                    exit 1
                fi

                # Start nginx in foreground
                echo "Starting nginx on port 8000..."
                nginx -g 'daemon off;'
                """;

        Path scriptPath = sourceDirectory.resolve("start.sh");
        Files.writeString(scriptPath, scriptContent);

        log.info("Startup script generated at: {}", scriptPath);
    }
}
