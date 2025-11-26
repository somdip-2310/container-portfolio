package dev.somdip.containerplatform.service.php;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Detects PHP framework and application structure from source code
 */
@Slf4j
@Service
public class PHPFrameworkDetector {

    public PHPApplicationInfo detectFramework(Path sourceDirectory) {
        log.info("Detecting PHP framework in directory: {}", sourceDirectory);

        try {
            // Check for composer.json
            Path composerJson = sourceDirectory.resolve("composer.json");
            if (Files.exists(composerJson)) {
                String content = Files.readString(composerJson);

                // Detect Laravel
                if (content.contains("laravel/framework")) {
                    log.info("Detected Laravel framework");
                    return PHPApplicationInfo.builder()
                            .framework(PHPFramework.LARAVEL)
                            .phpVersion("8.2")
                            .documentRoot("/var/www/html/public")
                            .entryPoint("public/index.php")
                            .requiresComposer(true)
                            .build();
                }

                // Detect Symfony
                if (content.contains("symfony/framework-bundle")) {
                    log.info("Detected Symfony framework");
                    return PHPApplicationInfo.builder()
                            .framework(PHPFramework.SYMFONY)
                            .phpVersion("8.2")
                            .documentRoot("/var/www/html/public")
                            .entryPoint("public/index.php")
                            .requiresComposer(true)
                            .build();
                }

                // Detect CodeIgniter
                if (content.contains("codeigniter/framework") || content.contains("codeigniter4/framework")) {
                    log.info("Detected CodeIgniter framework");
                    return PHPApplicationInfo.builder()
                            .framework(PHPFramework.CODEIGNITER)
                            .phpVersion("8.1")
                            .documentRoot("/var/www/html/public")
                            .entryPoint("public/index.php")
                            .requiresComposer(true)
                            .build();
                }
            }

            // Check for WordPress
            if (Files.exists(sourceDirectory.resolve("wp-config.php")) ||
                Files.exists(sourceDirectory.resolve("wp-config-sample.php"))) {
                log.info("Detected WordPress");
                return PHPApplicationInfo.builder()
                        .framework(PHPFramework.WORDPRESS)
                        .phpVersion("8.1")
                        .documentRoot("/var/www/html")
                        .entryPoint("index.php")
                        .requiresComposer(false)
                        .build();
            }

            // Check for common directory structures
            Path publicDir = sourceDirectory.resolve("public");
            if (Files.exists(publicDir) && Files.exists(publicDir.resolve("index.php"))) {
                log.info("Detected PHP application with public directory");
                return PHPApplicationInfo.builder()
                        .framework(PHPFramework.GENERIC_WITH_PUBLIC)
                        .phpVersion("8.1")
                        .documentRoot("/var/www/html/public")
                        .entryPoint("public/index.php")
                        .requiresComposer(Files.exists(composerJson))
                        .build();
            }

            // Check for index.php in root
            if (Files.exists(sourceDirectory.resolve("index.php"))) {
                log.info("Detected generic PHP application with index.php in root");
                return PHPApplicationInfo.builder()
                        .framework(PHPFramework.GENERIC)
                        .phpVersion("8.1")
                        .documentRoot("/var/www/html")
                        .entryPoint("index.php")
                        .requiresComposer(Files.exists(composerJson))
                        .build();
            }

            // Default: assume generic PHP with public dir
            log.info("No specific framework detected, using generic PHP setup");
            return PHPApplicationInfo.builder()
                    .framework(PHPFramework.GENERIC)
                    .phpVersion("8.1")
                    .documentRoot("/var/www/html")
                    .entryPoint("index.php")
                    .requiresComposer(Files.exists(composerJson))
                    .build();

        } catch (IOException e) {
            log.error("Error detecting PHP framework: {}", e.getMessage(), e);
            // Return safe default
            return PHPApplicationInfo.builder()
                    .framework(PHPFramework.GENERIC)
                    .phpVersion("8.1")
                    .documentRoot("/var/www/html")
                    .entryPoint("index.php")
                    .requiresComposer(false)
                    .build();
        }
    }

    /**
     * Detect PHP files and get basic statistics
     */
    public PHPProjectStats getProjectStats(Path sourceDirectory) {
        try {
            List<Path> phpFiles = Files.walk(sourceDirectory)
                    .filter(path -> path.toString().endsWith(".php"))
                    .collect(Collectors.toList());

            return PHPProjectStats.builder()
                    .phpFileCount(phpFiles.size())
                    .hasComposer(Files.exists(sourceDirectory.resolve("composer.json")))
                    .hasEnvFile(Files.exists(sourceDirectory.resolve(".env")))
                    .build();
        } catch (IOException e) {
            log.error("Error getting project stats: {}", e.getMessage());
            return PHPProjectStats.builder().build();
        }
    }
}
