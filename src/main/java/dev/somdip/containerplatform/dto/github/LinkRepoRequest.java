package dev.somdip.containerplatform.dto.github;

import java.util.Map;

/**
 * Request DTO for linking a GitHub repository to a container
 */
public class LinkRepoRequest {
    private String containerId;
    private String repoFullName;       // owner/repo format
    private String deployBranch;       // Branch to deploy from
    private String rootDirectory;      // Subdirectory containing app
    private String dockerfilePath;     // Path to Dockerfile
    private Boolean autoDeploy;        // Deploy on push
    private Map<String, String> buildEnvVars;

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }

    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }

    public String getDeployBranch() { return deployBranch; }
    public void setDeployBranch(String deployBranch) { this.deployBranch = deployBranch; }

    public String getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }

    public String getDockerfilePath() { return dockerfilePath; }
    public void setDockerfilePath(String dockerfilePath) { this.dockerfilePath = dockerfilePath; }

    public Boolean getAutoDeploy() { return autoDeploy; }
    public void setAutoDeploy(Boolean autoDeploy) { this.autoDeploy = autoDeploy; }

    public Map<String, String> getBuildEnvVars() { return buildEnvVars; }
    public void setBuildEnvVars(Map<String, String> buildEnvVars) { this.buildEnvVars = buildEnvVars; }
}
