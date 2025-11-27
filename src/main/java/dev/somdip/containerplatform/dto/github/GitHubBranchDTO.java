package dev.somdip.containerplatform.dto.github;

public class GitHubBranchDTO {
    private String name;
    private Boolean isProtected;
    private String commitSha;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsProtected() { return isProtected; }
    public void setIsProtected(Boolean isProtected) { this.isProtected = isProtected; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
}
