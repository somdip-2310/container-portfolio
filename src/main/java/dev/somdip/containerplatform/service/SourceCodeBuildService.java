package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for building Docker images from source code using AWS CodeBuild
 */
@Service
public class SourceCodeBuildService {
    private static final Logger log = LoggerFactory.getLogger(SourceCodeBuildService.class);

    private final CodeBuildClient codeBuildClient;
    private final EcrClient ecrClient;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.accountId}")
    private String awsAccountId;

    @Value("${aws.s3.bucket.assets}")
    private String s3Bucket;

    @Value("${aws.ecr.repository-prefix:container-platform}")
    private String ecrRepositoryPrefix;

    @Value("${aws.codebuild.serviceRoleArn}")
    private String codeBuildServiceRoleArn;

    public SourceCodeBuildService(CodeBuildClient codeBuildClient, EcrClient ecrClient) {
        this.codeBuildClient = codeBuildClient;
        this.ecrClient = ecrClient;
    }

    /**
     * Start a CodeBuild job to build Docker image from source code
     */
    public BuildResult startBuild(String projectId, String userId, String containerName, String s3Key) {
        try {
            // Ensure ECR repository exists
            String repositoryName = ensureEcrRepository(userId, containerName);
            String imageUri = String.format("%s.dkr.ecr.%s.amazonaws.com/%s:latest",
                awsAccountId, awsRegion, repositoryName);

            // Ensure CodeBuild project exists
            String projectName = ensureCodeBuildProject(userId, containerName);

            // Start the build
            Map<String, String> environmentVariables = new HashMap<>();
            environmentVariables.put("IMAGE_URI", imageUri);
            environmentVariables.put("S3_BUCKET", s3Bucket);
            environmentVariables.put("S3_KEY", s3Key);
            environmentVariables.put("AWS_DEFAULT_REGION", awsRegion);
            environmentVariables.put("AWS_ACCOUNT_ID", awsAccountId);
            environmentVariables.put("REPOSITORY_NAME", repositoryName);

            StartBuildRequest buildRequest = StartBuildRequest.builder()
                .projectName(projectName)
                .environmentVariablesOverride(
                    environmentVariables.entrySet().stream()
                        .map(e -> EnvironmentVariable.builder()
                            .name(e.getKey())
                            .value(e.getValue())
                            .type(EnvironmentVariableType.PLAINTEXT)
                            .build())
                        .toList()
                )
                .build();

            StartBuildResponse response = codeBuildClient.startBuild(buildRequest);
            String buildId = response.build().id();

            log.info("Started CodeBuild job: {} for project: {}", buildId, projectId);

            return new BuildResult(buildId, projectName, imageUri, repositoryName);

        } catch (Exception e) {
            log.error("Failed to start CodeBuild job for project: {}", projectId, e);
            throw new RuntimeException("Failed to start build: " + e.getMessage(), e);
        }
    }

    /**
     * Get the status of a CodeBuild job
     */
    public BuildStatus getBuildStatus(String buildId) {
        try {
            BatchGetBuildsRequest request = BatchGetBuildsRequest.builder()
                .ids(buildId)
                .build();

            BatchGetBuildsResponse response = codeBuildClient.batchGetBuilds(request);

            if (response.builds().isEmpty()) {
                return new BuildStatus("UNKNOWN", "Build not found", null, null);
            }

            Build build = response.builds().get(0);
            StatusType status = build.buildStatus();

            String statusStr = mapBuildStatus(status);
            String phase = build.currentPhase();

            return new BuildStatus(
                statusStr,
                phase,
                build.startTime(),
                build.endTime()
            );

        } catch (Exception e) {
            log.error("Failed to get build status for: {}", buildId, e);
            return new BuildStatus("ERROR", "Failed to get status: " + e.getMessage(), null, null);
        }
    }

    /**
     * Ensure ECR repository exists for the user's container
     */
    private String ensureEcrRepository(String userId, String containerName) {
        String repositoryName = String.format("%s-%s", ecrRepositoryPrefix,
            containerName.toLowerCase().replaceAll("[^a-z0-9-_]", "-"));

        try {
            // Check if repository exists
            DescribeRepositoriesRequest describeRequest = DescribeRepositoriesRequest.builder()
                .repositoryNames(repositoryName)
                .build();

            ecrClient.describeRepositories(describeRequest);
            log.info("ECR repository already exists: {}", repositoryName);

        } catch (RepositoryNotFoundException e) {
            // Create the repository
            log.info("Creating ECR repository: {}", repositoryName);

            CreateRepositoryRequest createRequest = CreateRepositoryRequest.builder()
                .repositoryName(repositoryName)
                .imageScanningConfiguration(cfg -> cfg.scanOnPush(true))
                .build();

            ecrClient.createRepository(createRequest);
            log.info("Created ECR repository: {}", repositoryName);
        }

        return repositoryName;
    }

    /**
     * Ensure CodeBuild project exists
     */
    private String ensureCodeBuildProject(String userId, String containerName) {
        String projectName = String.format("container-platform-%s",
            containerName.toLowerCase().replaceAll("[^a-z0-9-_]", "-"));

        try {
            // Check if project exists
            BatchGetProjectsRequest getRequest = BatchGetProjectsRequest.builder()
                .names(projectName)
                .build();

            BatchGetProjectsResponse getResponse = codeBuildClient.batchGetProjects(getRequest);

            if (!getResponse.projects().isEmpty()) {
                log.info("CodeBuild project already exists: {}", projectName);
                return projectName;
            }

            // Create the project
            log.info("Creating CodeBuild project: {}", projectName);

            CreateProjectRequest createRequest = CreateProjectRequest.builder()
                .name(projectName)
                .description("Build Docker image for container: " + containerName)
                .source(ProjectSource.builder()
                    .type(SourceType.NO_SOURCE)
                    .buildspec(getBuildSpec())
                    .build())
                .artifacts(ProjectArtifacts.builder()
                    .type(ArtifactsType.NO_ARTIFACTS)
                    .build())
                .environment(ProjectEnvironment.builder()
                    .type(EnvironmentType.LINUX_CONTAINER)
                    .image("aws/codebuild/standard:7.0")
                    .computeType(ComputeType.BUILD_GENERAL1_SMALL)
                    .privilegedMode(true) // Required for Docker builds
                    .build())
                .serviceRole(codeBuildServiceRoleArn)
                .build();

            codeBuildClient.createProject(createRequest);
            log.info("Created CodeBuild project: {}", projectName);

            return projectName;

        } catch (Exception e) {
            log.error("Failed to ensure CodeBuild project: {}", projectName, e);
            throw new RuntimeException("Failed to create CodeBuild project: " + e.getMessage(), e);
        }
    }

    /**
     * Get the buildspec.yml content for CodeBuild
     */
    private String getBuildSpec() {
        return """
            version: 0.2

            phases:
              pre_build:
                commands:
                  - echo Logging in to Amazon ECR...
                  - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com
                  - echo Downloading source code from S3...
                  - aws s3 cp s3://$S3_BUCKET/$S3_KEY source.zip
                  - unzip -q source.zip -d /tmp/source
                  - cd /tmp/source
                  - echo Source code extracted
                  - echo Replacing Docker Hub base images with ECR images...
                  - ECR_REGISTRY=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com
                  - |
                    if [ -f Dockerfile ]; then
                      # Replace Docker Hub images with ECR base images to avoid rate limiting
                      sed -i "s|FROM maven:3.9-eclipse-temurin-17|FROM ${ECR_REGISTRY}/base-images/maven:3.9-eclipse-temurin-17|gi" Dockerfile
                      sed -i "s|FROM maven:latest|FROM ${ECR_REGISTRY}/base-images/maven:3.9-eclipse-temurin-17|gi" Dockerfile
                      sed -i "s|FROM node:18-alpine|FROM ${ECR_REGISTRY}/base-images/node:18-alpine|gi" Dockerfile
                      sed -i "s|FROM node:18|FROM ${ECR_REGISTRY}/base-images/node:18-alpine|gi" Dockerfile
                      sed -i "s|FROM python:3.11-slim|FROM ${ECR_REGISTRY}/base-images/python:3.11-slim|gi" Dockerfile
                      sed -i "s|FROM python:3-slim|FROM ${ECR_REGISTRY}/base-images/python:3.11-slim|gi" Dockerfile
                      sed -i "s|FROM eclipse-temurin:17-jdk-alpine|FROM ${ECR_REGISTRY}/base-images/eclipse-temurin:17-jdk-alpine|gi" Dockerfile
                      sed -i "s|FROM eclipse-temurin:17-jre-alpine|FROM ${ECR_REGISTRY}/base-images/eclipse-temurin:17-jre-alpine|gi" Dockerfile
                      sed -i "s|FROM golang:1.21-alpine|FROM ${ECR_REGISTRY}/base-images/golang:1.21-alpine|gi" Dockerfile
                      sed -i "s|FROM alpine:latest|FROM ${ECR_REGISTRY}/base-images/alpine:latest|gi" Dockerfile
                      sed -i "s|FROM nginx:alpine|FROM ${ECR_REGISTRY}/base-images/nginx:alpine|gi" Dockerfile
                      sed -i "s|FROM php:8.2-fpm-alpine|FROM ${ECR_REGISTRY}/base-images/php:8.2-fpm-alpine|gi" Dockerfile
                      sed -i "s|FROM ruby:3.2-alpine|FROM ${ECR_REGISTRY}/base-images/ruby:3.2-alpine|gi" Dockerfile
                      sed -i "s|COPY --from=composer:latest|COPY --from=${ECR_REGISTRY}/base-images/composer:latest|gi" Dockerfile
                      echo "Dockerfile after base image replacement:"
                      cat Dockerfile
                    fi
              build:
                commands:
                  - echo Build started on `date`
                  - echo Building the Docker image...
                  - docker build -t $REPOSITORY_NAME .
                  - docker tag $REPOSITORY_NAME:latest $IMAGE_URI
              post_build:
                commands:
                  - echo Build completed on `date`
                  - echo Pushing the Docker image to ECR...
                  - docker push $IMAGE_URI
                  - echo Image pushed successfully
                  - printf '[{"name":"container","imageUri":"%s"}]' $IMAGE_URI > imagedefinitions.json

            artifacts:
              files:
                - imagedefinitions.json
            """;
    }

    /**
     * Map CodeBuild status to our deployment status
     */
    private String mapBuildStatus(StatusType status) {
        return switch (status) {
            case IN_PROGRESS -> "BUILDING";
            case SUCCEEDED -> "BUILD_COMPLETED";
            case FAILED, FAULT, TIMED_OUT -> "BUILD_FAILED";
            case STOPPED -> "BUILD_CANCELLED";
            default -> "UNKNOWN";
        };
    }

    /**
     * Result of starting a build
     */
    public static class BuildResult {
        private final String buildId;
        private final String projectName;
        private final String imageUri;
        private final String repositoryName;

        public BuildResult(String buildId, String projectName, String imageUri, String repositoryName) {
            this.buildId = buildId;
            this.projectName = projectName;
            this.imageUri = imageUri;
            this.repositoryName = repositoryName;
        }

        public String getBuildId() {
            return buildId;
        }

        public String getProjectName() {
            return projectName;
        }

        public String getImageUri() {
            return imageUri;
        }

        public String getRepositoryName() {
            return repositoryName;
        }
    }

    /**
     * Status of a build
     */
    public static class BuildStatus {
        private final String status;
        private final String phase;
        private final java.time.Instant startTime;
        private final java.time.Instant endTime;

        public BuildStatus(String status, String phase, java.time.Instant startTime, java.time.Instant endTime) {
            this.status = status;
            this.phase = phase;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getStatus() {
            return status;
        }

        public String getPhase() {
            return phase;
        }

        public java.time.Instant getStartTime() {
            return startTime;
        }

        public java.time.Instant getEndTime() {
            return endTime;
        }
    }
}
