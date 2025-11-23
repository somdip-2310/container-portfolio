package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ProjectAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ProjectAnalyzer.class);

    public enum ProjectType {
        NODEJS("Node.js", "node:18-alpine"),
        PYTHON("Python", "python:3.11-slim"),
        JAVA_MAVEN("Java (Maven)", "eclipse-temurin:17-jdk-alpine"),
        JAVA_GRADLE("Java (Gradle)", "eclipse-temurin:17-jdk-alpine"),
        GO("Go", "golang:1.21-alpine"),
        PHP("PHP", "php:8.2-fpm-alpine"),
        RUBY("Ruby", "ruby:3.2-alpine"),
        DOTNET("ASP.NET Core", "mcr.microsoft.com/dotnet/aspnet:7.0-alpine"),
        STATIC_HTML("Static HTML", "nginx:alpine"),
        UNKNOWN("Unknown", null);

        private final String displayName;
        private final String baseImage;

        ProjectType(String displayName, String baseImage) {
            this.displayName = displayName;
            this.baseImage = baseImage;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getBaseImage() {
            return baseImage;
        }
    }

    public static class ProjectInfo {
        private ProjectType type;
        private String packageManager;
        private String buildTool;
        private Integer port;
        private String startCommand;
        private Map<String, String> metadata;

        public ProjectInfo() {
            this.metadata = new HashMap<>();
        }

        public ProjectType getType() {
            return type;
        }

        public void setType(ProjectType type) {
            this.type = type;
        }

        public String getPackageManager() {
            return packageManager;
        }

        public void setPackageManager(String packageManager) {
            this.packageManager = packageManager;
        }

        public String getBuildTool() {
            return buildTool;
        }

        public void setBuildTool(String buildTool) {
            this.buildTool = buildTool;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getStartCommand() {
            return startCommand;
        }

        public void setStartCommand(String startCommand) {
            this.startCommand = startCommand;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    public ProjectInfo analyzeProject(Path projectPath) throws IOException {
        log.info("Analyzing project at: {}", projectPath);
        ProjectInfo info = new ProjectInfo();

        // Check for existing Dockerfile
        if (Files.exists(projectPath.resolve("Dockerfile"))) {
            log.info("Found existing Dockerfile");
            info.getMetadata().put("hasDockerfile", "true");
        }

        // Detect project type based on files
        if (Files.exists(projectPath.resolve("package.json"))) {
            info.setType(ProjectType.NODEJS);
            analyzeNodeJs(projectPath, info);
        } else if (Files.exists(projectPath.resolve("requirements.txt")) ||
                   Files.exists(projectPath.resolve("Pipfile")) ||
                   Files.exists(projectPath.resolve("pyproject.toml"))) {
            info.setType(ProjectType.PYTHON);
            analyzePython(projectPath, info);
        } else if (Files.exists(projectPath.resolve("pom.xml"))) {
            info.setType(ProjectType.JAVA_MAVEN);
            analyzeJavaMaven(projectPath, info);
        } else if (Files.exists(projectPath.resolve("build.gradle")) ||
                   Files.exists(projectPath.resolve("build.gradle.kts"))) {
            info.setType(ProjectType.JAVA_GRADLE);
            analyzeJavaGradle(projectPath, info);
        } else if (Files.exists(projectPath.resolve("go.mod"))) {
            info.setType(ProjectType.GO);
            analyzeGo(projectPath, info);
        } else if (Files.exists(projectPath.resolve("composer.json"))) {
            info.setType(ProjectType.PHP);
            analyzePhp(projectPath, info);
        } else if (Files.exists(projectPath.resolve("Gemfile"))) {
            info.setType(ProjectType.RUBY);
            analyzeRuby(projectPath, info);
        } else if (Files.exists(projectPath.resolve("*.csproj")) ||
                   Files.exists(projectPath.resolve("*.sln"))) {
            info.setType(ProjectType.DOTNET);
            analyzeDotNet(projectPath, info);
        } else if (hasHtmlFiles(projectPath)) {
            info.setType(ProjectType.STATIC_HTML);
            analyzeStaticHtml(projectPath, info);
        } else {
            info.setType(ProjectType.UNKNOWN);
        }

        log.info("Detected project type: {}", info.getType().getDisplayName());
        return info;
    }

    private void analyzeNodeJs(Path projectPath, ProjectInfo info) throws IOException {
        info.setPackageManager(detectNodePackageManager(projectPath));
        info.setPort(3000); // Default Node.js port

        Path packageJson = projectPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            String content = Files.readString(packageJson);

            // Detect framework
            if (content.contains("\"express\"")) {
                info.getMetadata().put("framework", "Express");
            } else if (content.contains("\"next\"")) {
                info.getMetadata().put("framework", "Next.js");
                info.setPort(3000);
            } else if (content.contains("\"react\"") && content.contains("\"react-scripts\"")) {
                info.getMetadata().put("framework", "Create React App");
            } else if (content.contains("\"vue\"")) {
                info.getMetadata().put("framework", "Vue.js");
                info.setPort(8080);
            }

            // Detect start command
            if (content.contains("\"start\":")) {
                info.setStartCommand("npm start");
            }
        }
    }

    private String detectNodePackageManager(Path projectPath) {
        if (Files.exists(projectPath.resolve("package-lock.json"))) {
            return "npm";
        } else if (Files.exists(projectPath.resolve("yarn.lock"))) {
            return "yarn";
        } else if (Files.exists(projectPath.resolve("pnpm-lock.yaml"))) {
            return "pnpm";
        }
        return "npm"; // default
    }

    private void analyzePython(Path projectPath, ProjectInfo info) throws IOException {
        info.setPort(8000); // Default Python port

        // Detect package manager
        if (Files.exists(projectPath.resolve("Pipfile"))) {
            info.setPackageManager("pipenv");
        } else if (Files.exists(projectPath.resolve("pyproject.toml"))) {
            info.setPackageManager("poetry");
        } else {
            info.setPackageManager("pip");
        }

        // Detect framework
        Path requirements = projectPath.resolve("requirements.txt");
        if (Files.exists(requirements)) {
            String content = Files.readString(requirements);

            if (content.contains("Flask")) {
                info.getMetadata().put("framework", "Flask");
                info.setStartCommand("gunicorn app:app");
            } else if (content.contains("Django")) {
                info.getMetadata().put("framework", "Django");
                info.setStartCommand("gunicorn config.wsgi:application");
            } else if (content.contains("fastapi")) {
                info.getMetadata().put("framework", "FastAPI");
                info.setStartCommand("uvicorn main:app --host 0.0.0.0");
            }
        }
    }

    private void analyzeJavaMaven(Path projectPath, ProjectInfo info) {
        info.setBuildTool("maven");
        info.setPort(8080); // Default Spring Boot port
        info.setStartCommand("java -jar target/*.jar");

        Path pom = projectPath.resolve("pom.xml");
        if (Files.exists(pom)) {
            try {
                String content = Files.readString(pom);
                if (content.contains("spring-boot")) {
                    info.getMetadata().put("framework", "Spring Boot");
                }
            } catch (IOException e) {
                log.warn("Error reading pom.xml", e);
            }
        }
    }

    private void analyzeJavaGradle(Path projectPath, ProjectInfo info) {
        info.setBuildTool("gradle");
        info.setPort(8080); // Default Spring Boot port
        info.setStartCommand("java -jar build/libs/*.jar");

        Path buildGradle = projectPath.resolve("build.gradle");
        if (!Files.exists(buildGradle)) {
            buildGradle = projectPath.resolve("build.gradle.kts");
        }

        if (Files.exists(buildGradle)) {
            try {
                String content = Files.readString(buildGradle);
                if (content.contains("spring-boot")) {
                    info.getMetadata().put("framework", "Spring Boot");
                }
            } catch (IOException e) {
                log.warn("Error reading build.gradle", e);
            }
        }
    }

    private void analyzeGo(Path projectPath, ProjectInfo info) {
        info.setPort(8080); // Default Go port
        info.setStartCommand("./app");

        Path goMod = projectPath.resolve("go.mod");
        if (Files.exists(goMod)) {
            try {
                String content = Files.readString(goMod);
                if (content.contains("gin-gonic/gin")) {
                    info.getMetadata().put("framework", "Gin");
                } else if (content.contains("gofiber/fiber")) {
                    info.getMetadata().put("framework", "Fiber");
                }
            } catch (IOException e) {
                log.warn("Error reading go.mod", e);
            }
        }
    }

    private void analyzePhp(Path projectPath, ProjectInfo info) {
        info.setPort(9000); // PHP-FPM port
        info.setPackageManager("composer");

        Path composerJson = projectPath.resolve("composer.json");
        if (Files.exists(composerJson)) {
            try {
                String content = Files.readString(composerJson);
                if (content.contains("laravel/framework")) {
                    info.getMetadata().put("framework", "Laravel");
                } else if (content.contains("symfony/")) {
                    info.getMetadata().put("framework", "Symfony");
                }
            } catch (IOException e) {
                log.warn("Error reading composer.json", e);
            }
        }
    }

    private void analyzeRuby(Path projectPath, ProjectInfo info) {
        info.setPort(3000); // Default Rails port
        info.setPackageManager("bundler");

        if (Files.exists(projectPath.resolve("config.ru"))) {
            info.getMetadata().put("framework", "Rack");
        }

        Path gemfile = projectPath.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            try {
                String content = Files.readString(gemfile);
                if (content.contains("rails")) {
                    info.getMetadata().put("framework", "Ruby on Rails");
                } else if (content.contains("sinatra")) {
                    info.getMetadata().put("framework", "Sinatra");
                }
            } catch (IOException e) {
                log.warn("Error reading Gemfile", e);
            }
        }
    }

    private void analyzeDotNet(Path projectPath, ProjectInfo info) {
        info.setPort(80); // Default ASP.NET port
        info.setBuildTool("dotnet");
    }

    private void analyzeStaticHtml(Path projectPath, ProjectInfo info) {
        info.setPort(80); // Nginx default port
        info.getMetadata().put("webServer", "nginx");
    }

    private boolean hasHtmlFiles(Path projectPath) {
        try (Stream<Path> paths = Files.walk(projectPath, 2)) {
            return paths.anyMatch(p -> p.toString().endsWith(".html"));
        } catch (IOException e) {
            return false;
        }
    }
}
