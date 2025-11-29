package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.dto.EnvVarSuggestion;
import dev.somdip.containerplatform.model.LinkedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to detect required environment variables from source code.
 * Supports multiple tech stacks: Java/Spring, Node.js, Python, Go, PHP, Ruby, .NET
 */
@Service
public class EnvironmentVariableDetectorService {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentVariableDetectorService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final GitHubOAuthService oAuthService;

    public EnvironmentVariableDetectorService(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    // Secret patterns - env vars matching these are likely secrets
    private static final Set<String> SECRET_PATTERNS = Set.of(
        "KEY", "SECRET", "PASSWORD", "TOKEN", "CREDENTIAL", "AUTH",
        "API_KEY", "APIKEY", "PRIVATE", "CERTIFICATE", "CERT"
    );

    // Files to check for env vars
    private static final List<String> ENV_EXAMPLE_FILES = List.of(
        ".env.example", ".env.sample", ".env.template", ".env.local.example",
        "env.example", "env.sample", ".env.development.example"
    );

    private static final List<String> DOCKERFILE_NAMES = List.of(
        "Dockerfile", "dockerfile", "Dockerfile.prod", "Dockerfile.production"
    );

    /**
     * Detect environment variables from a GitHub repository using individual parameters.
     * This is used by the controller when detecting env vars before linking.
     */
    public List<EnvVarSuggestion> detectFromRepository(String userId, String owner, String repoName,
                                                        String branch, String rootDir, String dockerfilePath) {
        // Get access token from OAuth service (already decrypted)
        String accessToken = oAuthService.getAccessToken(userId);
        if (accessToken == null) {
            throw new IllegalStateException("GitHub not connected");
        }

        // Create a temporary LinkedRepository object with the provided parameters
        LinkedRepository tempRepo = new LinkedRepository();
        tempRepo.setRepoFullName(owner + "/" + repoName);
        tempRepo.setRepoOwner(owner);
        tempRepo.setRepoName(repoName);
        tempRepo.setDeployBranch(branch);
        tempRepo.setRootDirectory(rootDir);
        tempRepo.setDockerfilePath(dockerfilePath);

        return detectFromRepository(tempRepo, accessToken);
    }

    /**
     * Detect environment variables from a GitHub repository
     */
    public List<EnvVarSuggestion> detectFromRepository(LinkedRepository repo, String accessToken) {
        log.info("Detecting environment variables from repository: {}", repo.getRepoFullName());

        Map<String, EnvVarSuggestion> suggestions = new LinkedHashMap<>();
        String rootDir = repo.getRootDirectory() != null ? repo.getRootDirectory() : "";

        // 1. Check Dockerfile
        for (String dockerfileName : DOCKERFILE_NAMES) {
            String path = rootDir.isEmpty() ? dockerfileName : rootDir + "/" + dockerfileName;
            String content = fetchFileContent(repo.getRepoFullName(), path, repo.getDeployBranch(), accessToken);
            if (content != null) {
                detectFromDockerfile(content, suggestions);
                break;
            }
        }

        // 2. Check .env.example files
        for (String envFile : ENV_EXAMPLE_FILES) {
            String path = rootDir.isEmpty() ? envFile : rootDir + "/" + envFile;
            String content = fetchFileContent(repo.getRepoFullName(), path, repo.getDeployBranch(), accessToken);
            if (content != null) {
                detectFromEnvExample(content, suggestions);
            }
        }

        // 3. Detect framework and check framework-specific files
        String framework = detectFramework(repo, accessToken);
        if (framework != null) {
            detectFrameworkSpecificEnvVars(repo, accessToken, framework, suggestions);
        }

        log.info("Detected {} environment variables from repository", suggestions.size());
        return new ArrayList<>(suggestions.values());
    }

    /**
     * Detect environment variables from Dockerfile content
     */
    public void detectFromDockerfile(String content, Map<String, EnvVarSuggestion> suggestions) {
        // Pattern for ENV instructions: ENV VAR_NAME=value or ENV VAR_NAME value
        Pattern envPattern = Pattern.compile("^ENV\\s+([A-Za-z_][A-Za-z0-9_]*)(?:[=\\s](.*))?", Pattern.MULTILINE);
        Matcher matcher = envPattern.matcher(content);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);
            if (defaultValue != null) {
                defaultValue = defaultValue.trim().replaceAll("^[\"']|[\"']$", ""); // Remove quotes
            }

            if (!suggestions.containsKey(varName)) {
                suggestions.put(varName, EnvVarSuggestion.builder()
                    .name(varName)
                    .defaultValue(defaultValue)
                    .source("DOCKERFILE")
                    .framework("GENERIC")
                    .required(defaultValue == null || defaultValue.isEmpty())
                    .isSecret(isSecretVariable(varName))
                    .build());
            }
        }

        // Pattern for ARG instructions that might be used as env vars
        Pattern argPattern = Pattern.compile("^ARG\\s+([A-Za-z_][A-Za-z0-9_]*)(?:=(.*))?", Pattern.MULTILINE);
        matcher = argPattern.matcher(content);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            if (!suggestions.containsKey(varName)) {
                suggestions.put(varName, EnvVarSuggestion.builder()
                    .name(varName)
                    .defaultValue(defaultValue)
                    .source("DOCKERFILE")
                    .framework("GENERIC")
                    .description("Build argument (may be needed at runtime)")
                    .required(false)
                    .isSecret(isSecretVariable(varName))
                    .build());
            }
        }
    }

    /**
     * Detect environment variables from .env.example file
     */
    public void detectFromEnvExample(String content, Map<String, EnvVarSuggestion> suggestions) {
        String[] lines = content.split("\n");
        String lastComment = null;

        for (String line : lines) {
            line = line.trim();

            // Track comments as descriptions
            if (line.startsWith("#")) {
                lastComment = line.substring(1).trim();
                continue;
            }

            // Parse VAR_NAME=value or VAR_NAME=
            if (line.contains("=") && !line.startsWith("#")) {
                String[] parts = line.split("=", 2);
                String varName = parts[0].trim();
                String defaultValue = parts.length > 1 ? parts[1].trim() : null;

                if (varName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    // Remove quotes from value
                    if (defaultValue != null) {
                        defaultValue = defaultValue.replaceAll("^[\"']|[\"']$", "");
                    }

                    boolean isPlaceholder = defaultValue != null && (
                        defaultValue.contains("your_") ||
                        defaultValue.contains("YOUR_") ||
                        defaultValue.contains("<") ||
                        defaultValue.contains("xxx") ||
                        defaultValue.isEmpty()
                    );

                    suggestions.put(varName, EnvVarSuggestion.builder()
                        .name(varName)
                        .defaultValue(isPlaceholder ? null : defaultValue)
                        .description(lastComment)
                        .source("ENV_EXAMPLE")
                        .framework("GENERIC")
                        .required(isPlaceholder || defaultValue == null || defaultValue.isEmpty())
                        .isSecret(isSecretVariable(varName))
                        .build());
                }
            }

            // Reset comment if we didn't use it
            if (!line.isEmpty() && !line.startsWith("#")) {
                lastComment = null;
            }
        }
    }

    /**
     * Detect the framework/language used in the repository
     */
    private String detectFramework(LinkedRepository repo, String accessToken) {
        String rootDir = repo.getRootDirectory() != null ? repo.getRootDirectory() : "";

        // Check for Java/Spring (pom.xml, build.gradle)
        if (fileExists(repo, "pom.xml", accessToken) || fileExists(repo, "build.gradle", accessToken)) {
            // Check if it's Spring Boot
            String pomContent = fetchFileContent(repo.getRepoFullName(),
                rootDir.isEmpty() ? "pom.xml" : rootDir + "/pom.xml",
                repo.getDeployBranch(), accessToken);
            if (pomContent != null && pomContent.contains("spring-boot")) {
                return "SPRING";
            }
            return "JAVA";
        }

        // Check for Node.js (package.json)
        if (fileExists(repo, "package.json", accessToken)) {
            String pkgContent = fetchFileContent(repo.getRepoFullName(),
                rootDir.isEmpty() ? "package.json" : rootDir + "/package.json",
                repo.getDeployBranch(), accessToken);
            if (pkgContent != null) {
                if (pkgContent.contains("\"next\"") || pkgContent.contains("\"nextjs\"")) {
                    return "NEXTJS";
                } else if (pkgContent.contains("\"express\"")) {
                    return "EXPRESS";
                } else if (pkgContent.contains("\"nestjs\"") || pkgContent.contains("@nestjs")) {
                    return "NESTJS";
                }
            }
            return "NODE";
        }

        // Check for Python
        if (fileExists(repo, "requirements.txt", accessToken) ||
            fileExists(repo, "pyproject.toml", accessToken) ||
            fileExists(repo, "setup.py", accessToken)) {
            String reqContent = fetchFileContent(repo.getRepoFullName(),
                rootDir.isEmpty() ? "requirements.txt" : rootDir + "/requirements.txt",
                repo.getDeployBranch(), accessToken);
            if (reqContent != null) {
                if (reqContent.contains("django")) return "DJANGO";
                if (reqContent.contains("flask")) return "FLASK";
                if (reqContent.contains("fastapi")) return "FASTAPI";
            }
            return "PYTHON";
        }

        // Check for Go
        if (fileExists(repo, "go.mod", accessToken)) {
            return "GO";
        }

        // Check for PHP
        if (fileExists(repo, "composer.json", accessToken)) {
            String composerContent = fetchFileContent(repo.getRepoFullName(),
                rootDir.isEmpty() ? "composer.json" : rootDir + "/composer.json",
                repo.getDeployBranch(), accessToken);
            if (composerContent != null && composerContent.contains("laravel")) {
                return "LARAVEL";
            }
            return "PHP";
        }

        // Check for Ruby
        if (fileExists(repo, "Gemfile", accessToken)) {
            String gemContent = fetchFileContent(repo.getRepoFullName(),
                rootDir.isEmpty() ? "Gemfile" : rootDir + "/Gemfile",
                repo.getDeployBranch(), accessToken);
            if (gemContent != null && gemContent.contains("rails")) {
                return "RAILS";
            }
            return "RUBY";
        }

        // Check for .NET
        if (fileExists(repo, "*.csproj", accessToken) || fileExists(repo, "*.fsproj", accessToken)) {
            return "DOTNET";
        }

        return null;
    }

    /**
     * Detect framework-specific environment variables
     */
    private void detectFrameworkSpecificEnvVars(LinkedRepository repo, String accessToken,
                                                String framework, Map<String, EnvVarSuggestion> suggestions) {
        String rootDir = repo.getRootDirectory() != null ? repo.getRootDirectory() : "";

        switch (framework) {
            case "SPRING":
                detectSpringEnvVars(repo, accessToken, suggestions);
                break;
            case "NODE":
            case "EXPRESS":
            case "NEXTJS":
            case "NESTJS":
                detectNodeEnvVars(repo, accessToken, suggestions, framework);
                break;
            case "PYTHON":
            case "DJANGO":
            case "FLASK":
            case "FASTAPI":
                detectPythonEnvVars(repo, accessToken, suggestions, framework);
                break;
            case "GO":
                detectGoEnvVars(repo, accessToken, suggestions);
                break;
            case "PHP":
            case "LARAVEL":
                detectPhpEnvVars(repo, accessToken, suggestions, framework);
                break;
            case "RUBY":
            case "RAILS":
                detectRubyEnvVars(repo, accessToken, suggestions, framework);
                break;
            case "DOTNET":
                detectDotNetEnvVars(repo, accessToken, suggestions);
                break;
        }
    }

    /**
     * Detect Spring Boot environment variables from application.properties/yml
     */
    private void detectSpringEnvVars(LinkedRepository repo, String accessToken,
                                    Map<String, EnvVarSuggestion> suggestions) {
        String rootDir = repo.getRootDirectory() != null ? repo.getRootDirectory() : "";

        // Check application.properties
        String[] configPaths = {
            "src/main/resources/application.properties",
            "src/main/resources/application.yml",
            "src/main/resources/application.yaml",
            "application.properties",
            "application.yml"
        };

        for (String configPath : configPaths) {
            String path = rootDir.isEmpty() ? configPath : rootDir + "/" + configPath;
            String content = fetchFileContent(repo.getRepoFullName(), path, repo.getDeployBranch(), accessToken);

            if (content != null) {
                // Pattern for ${VAR_NAME} or ${VAR_NAME:default}
                Pattern pattern = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\\}");
                Matcher matcher = pattern.matcher(content);

                while (matcher.find()) {
                    String varName = matcher.group(1);
                    String defaultValue = matcher.group(2);

                    if (!suggestions.containsKey(varName)) {
                        suggestions.put(varName, EnvVarSuggestion.builder()
                            .name(varName)
                            .defaultValue(defaultValue)
                            .source("CONFIG_FILE")
                            .framework("SPRING")
                            .required(defaultValue == null)
                            .isSecret(isSecretVariable(varName))
                            .build());
                    }
                }
            }
        }
    }

    /**
     * Detect Node.js environment variables from source code
     */
    private void detectNodeEnvVars(LinkedRepository repo, String accessToken,
                                   Map<String, EnvVarSuggestion> suggestions, String framework) {
        // Detect from common patterns in JS/TS files
        // process.env.VAR_NAME, process.env['VAR_NAME'], process.env["VAR_NAME"]
        String[] searchPatterns = {
            "process\\.env\\.([A-Za-z_][A-Za-z0-9_]*)",
            "process\\.env\\[\"([A-Za-z_][A-Za-z0-9_]*)\"\\]",
            "process\\.env\\['([A-Za-z_][A-Za-z0-9_]*)'\\]"
        };

        // For Next.js, also check for NEXT_PUBLIC_ pattern
        if ("NEXTJS".equals(framework)) {
            suggestions.put("NEXT_PUBLIC_API_URL", EnvVarSuggestion.builder()
                .name("NEXT_PUBLIC_API_URL")
                .source("CONFIG_FILE")
                .framework(framework)
                .description("Public API URL for client-side requests")
                .required(false)
                .isSecret(false)
                .build());
        }

        // Add common Node.js env vars
        addCommonEnvVar(suggestions, "NODE_ENV", "production", framework, false);
        addCommonEnvVar(suggestions, "PORT", "3000", framework, false);
    }

    /**
     * Detect Python environment variables
     */
    private void detectPythonEnvVars(LinkedRepository repo, String accessToken,
                                     Map<String, EnvVarSuggestion> suggestions, String framework) {
        // Common Python env vars
        addCommonEnvVar(suggestions, "PYTHONUNBUFFERED", "1", framework, false);

        if ("DJANGO".equals(framework)) {
            addCommonEnvVar(suggestions, "DJANGO_SECRET_KEY", null, framework, true);
            addCommonEnvVar(suggestions, "DJANGO_DEBUG", "False", framework, false);
            addCommonEnvVar(suggestions, "DJANGO_ALLOWED_HOSTS", "*", framework, false);
            addCommonEnvVar(suggestions, "DATABASE_URL", null, framework, true);
        } else if ("FLASK".equals(framework)) {
            addCommonEnvVar(suggestions, "FLASK_APP", "app.py", framework, false);
            addCommonEnvVar(suggestions, "FLASK_ENV", "production", framework, false);
            addCommonEnvVar(suggestions, "SECRET_KEY", null, framework, true);
        } else if ("FASTAPI".equals(framework)) {
            addCommonEnvVar(suggestions, "HOST", "0.0.0.0", framework, false);
            addCommonEnvVar(suggestions, "PORT", "8000", framework, false);
        }
    }

    /**
     * Detect Go environment variables
     */
    private void detectGoEnvVars(LinkedRepository repo, String accessToken,
                                Map<String, EnvVarSuggestion> suggestions) {
        addCommonEnvVar(suggestions, "PORT", "8080", "GO", false);
        addCommonEnvVar(suggestions, "GIN_MODE", "release", "GO", false);
    }

    /**
     * Detect PHP environment variables
     */
    private void detectPhpEnvVars(LinkedRepository repo, String accessToken,
                                  Map<String, EnvVarSuggestion> suggestions, String framework) {
        if ("LARAVEL".equals(framework)) {
            addCommonEnvVar(suggestions, "APP_KEY", null, framework, true);
            addCommonEnvVar(suggestions, "APP_ENV", "production", framework, false);
            addCommonEnvVar(suggestions, "APP_DEBUG", "false", framework, false);
            addCommonEnvVar(suggestions, "APP_URL", null, framework, false);
            addCommonEnvVar(suggestions, "DB_CONNECTION", "mysql", framework, false);
            addCommonEnvVar(suggestions, "DB_HOST", null, framework, false);
            addCommonEnvVar(suggestions, "DB_DATABASE", null, framework, false);
            addCommonEnvVar(suggestions, "DB_USERNAME", null, framework, true);
            addCommonEnvVar(suggestions, "DB_PASSWORD", null, framework, true);
        }
    }

    /**
     * Detect Ruby environment variables
     */
    private void detectRubyEnvVars(LinkedRepository repo, String accessToken,
                                   Map<String, EnvVarSuggestion> suggestions, String framework) {
        if ("RAILS".equals(framework)) {
            addCommonEnvVar(suggestions, "RAILS_ENV", "production", framework, false);
            addCommonEnvVar(suggestions, "SECRET_KEY_BASE", null, framework, true);
            addCommonEnvVar(suggestions, "DATABASE_URL", null, framework, true);
            addCommonEnvVar(suggestions, "RAILS_SERVE_STATIC_FILES", "true", framework, false);
        }
    }

    /**
     * Detect .NET environment variables
     */
    private void detectDotNetEnvVars(LinkedRepository repo, String accessToken,
                                     Map<String, EnvVarSuggestion> suggestions) {
        addCommonEnvVar(suggestions, "ASPNETCORE_ENVIRONMENT", "Production", "DOTNET", false);
        addCommonEnvVar(suggestions, "ASPNETCORE_URLS", "http://0.0.0.0:8080", "DOTNET", false);
    }

    /**
     * Helper to add common env var suggestions
     */
    private void addCommonEnvVar(Map<String, EnvVarSuggestion> suggestions, String name,
                                 String defaultValue, String framework, boolean required) {
        if (!suggestions.containsKey(name)) {
            suggestions.put(name, EnvVarSuggestion.builder()
                .name(name)
                .defaultValue(defaultValue)
                .source("CONFIG_FILE")
                .framework(framework)
                .required(required)
                .isSecret(isSecretVariable(name))
                .build());
        }
    }

    /**
     * Check if a variable name looks like a secret
     */
    private boolean isSecretVariable(String varName) {
        String upper = varName.toUpperCase();
        return SECRET_PATTERNS.stream().anyMatch(upper::contains);
    }

    /**
     * Fetch file content from GitHub
     */
    private String fetchFileContent(String repoFullName, String path, String branch, String accessToken) {
        try {
            String url = String.format("https://api.github.com/repos/%s/contents/%s?ref=%s",
                repoFullName, path, branch != null ? branch : "main");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/vnd.github.raw");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            return response.getBody();
        } catch (Exception e) {
            log.debug("File not found or error fetching: {}/{}", repoFullName, path);
            return null;
        }
    }

    /**
     * Check if a file exists in the repository
     */
    private boolean fileExists(LinkedRepository repo, String filename, String accessToken) {
        String rootDir = repo.getRootDirectory() != null ? repo.getRootDirectory() : "";
        String path = rootDir.isEmpty() ? filename : rootDir + "/" + filename;
        return fetchFileContent(repo.getRepoFullName(), path, repo.getDeployBranch(), accessToken) != null;
    }

    /**
     * Validate that all required environment variables are provided
     */
    public List<String> validateRequiredEnvVars(List<EnvVarSuggestion> suggestions,
                                                Map<String, String> providedEnvVars) {
        List<String> missing = new ArrayList<>();

        for (EnvVarSuggestion suggestion : suggestions) {
            if (suggestion.isRequired()) {
                String value = providedEnvVars != null ? providedEnvVars.get(suggestion.getName()) : null;
                if (value == null || value.isEmpty()) {
                    missing.add(suggestion.getName());
                }
            }
        }

        return missing;
    }
}
