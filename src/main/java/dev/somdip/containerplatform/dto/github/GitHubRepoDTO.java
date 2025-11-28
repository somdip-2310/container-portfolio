package dev.somdip.containerplatform.dto.github;

public class GitHubRepoDTO {
    private String id;
    private String name;
    private String fullName;
    private String description;
    private String owner;
    private String ownerAvatarUrl;
    private String htmlUrl;
    private String cloneUrl;
    private String defaultBranch;
    private Boolean isPrivate;
    private String language;
    private Long stargazersCount;
    private Long forksCount;
    private String pushedAt;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getOwnerAvatarUrl() { return ownerAvatarUrl; }
    public void setOwnerAvatarUrl(String ownerAvatarUrl) { this.ownerAvatarUrl = ownerAvatarUrl; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public String getCloneUrl() { return cloneUrl; }
    public void setCloneUrl(String cloneUrl) { this.cloneUrl = cloneUrl; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Long getStargazersCount() { return stargazersCount; }
    public void setStargazersCount(Long stargazersCount) { this.stargazersCount = stargazersCount; }

    public Long getForksCount() { return forksCount; }
    public void setForksCount(Long forksCount) { this.forksCount = forksCount; }

    public String getPushedAt() { return pushedAt; }
    public void setPushedAt(String pushedAt) { this.pushedAt = pushedAt; }
}
