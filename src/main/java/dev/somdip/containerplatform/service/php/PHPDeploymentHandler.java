package dev.somdip.containerplatform.service.php;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates PHP application deployment with automatic framework detection
 * and configuration generation
 */
@Slf4j
@Service
public class PHPDeploymentHandler {

    private final PHPFrameworkDetector frameworkDetector;
    private final PHPDockerfileGenerator dockerfileGenerator;
    private final PHPNginxConfigGenerator nginxConfigGenerator;

    public PHPDeploymentHandler(PHPFrameworkDetector frameworkDetector,
                                PHPDockerfileGenerator dockerfileGenerator,
                                PHPNginxConfigGenerator nginxConfigGenerator) {
        this.frameworkDetector = frameworkDetector;
        this.dockerfileGenerator = dockerfileGenerator;
        this.nginxConfigGenerator = nginxConfigGenerator;
    }

    /**
     * Check if the project is a PHP application
     */
    public boolean isPHPProject(Path projectPath) {
        try {
            // Check for common PHP indicators
            boolean hasComposerJson = Files.exists(projectPath.resolve("composer.json"));
            boolean hasIndexPhp = Files.exists(projectPath.resolve("index.php")) ||
                                 Files.exists(projectPath.resolve("public/index.php"));
            boolean hasPhpFiles = Files.walk(projectPath, 2)
                    .anyMatch(path -> path.toString().endsWith(".php"));

            return hasComposerJson || hasIndexPhp || hasPhpFiles;
        } catch (IOException e) {
            log.error("Error checking if project is PHP: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate all necessary files for PHP deployment:
     * - Dockerfile with nginx + PHP-FPM
     * - nginx.conf
     * - default.conf (site config)
     * - start.sh (startup script)
     */
    public PHPDeploymentResult handlePHPDeployment(Path projectPath) throws IOException {
        log.info("Starting PHP deployment handling for: {}", projectPath);

        // Detect PHP framework/structure
        PHPApplicationInfo appInfo = frameworkDetector.detectFramework(projectPath);
        log.info("Detected PHP application: {} (PHP {})",
                appInfo.getFramework().getDisplayName(),
                appInfo.getPhpVersion());

        // Generate Dockerfile
        dockerfileGenerator.generateDockerfile(projectPath, appInfo);
        log.info("Generated Dockerfile for PHP application");

        // Generate nginx configuration
        nginxConfigGenerator.generateNginxConfig(projectPath, appInfo);
        log.info("Generated nginx configuration");

        // Generate startup script
        dockerfileGenerator.generateStartupScript(projectPath);
        log.info("Generated startup script");

        // Read generated Dockerfile content
        String dockerfileContent = Files.readString(projectPath.resolve("Dockerfile"));

        PHPDeploymentResult result = PHPDeploymentResult.builder()
                .applicationInfo(appInfo)
                .dockerfileContent(dockerfileContent)
                .success(true)
                .message("PHP deployment files generated successfully for " +
                        appInfo.getFramework().getDisplayName())
                .build();

        log.info("PHP deployment handling complete: {}", result.getMessage());
        return result;
    }

    /**
     * Result of PHP deployment preparation
     */
    @lombok.Data
    @lombok.Builder
    public static class PHPDeploymentResult {
        private PHPApplicationInfo applicationInfo;
        private String dockerfileContent;
        private boolean success;
        private String message;
    }
}
