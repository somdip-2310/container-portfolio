# GitHub Actions CI/CD Workflows

This directory contains the CI/CD pipelines for the Container Platform project.

## Workflows

### 1. CI/CD Pipeline (`ci-cd.yml`)

Main pipeline that runs on pushes to `main` and `develop` branches.

**Jobs:**
- **Build and Test**: Compiles code, runs unit/integration tests, generates coverage
- **Security Scan**: Runs OWASP dependency check
- **Docker Build & Push**: Builds Docker image and pushes to Amazon ECR
- **Deploy to ECS**: Deploys new image to Amazon ECS
- **Post-Deployment Tests**: Runs smoke tests after deployment

**Triggers:**
- Push to `main` or `develop` branches
- Pull requests to `main`

### 2. Pull Request Checks (`pr-checks.yml`)

Validation pipeline for pull requests.

**Jobs:**
- **Code Quality**: SonarCloud analysis, checkstyle, SpotBugs
- **Build & Test**: Builds and tests the application
- **Docker Build**: Tests Docker image build

**Triggers:**
- Pull requests to `main` or `develop`

## Required GitHub Secrets

Configure these secrets in your GitHub repository settings:

```
AWS_ACCESS_KEY_ID         # AWS access key for ECR and ECS
AWS_SECRET_ACCESS_KEY     # AWS secret access key
SONAR_TOKEN              # SonarCloud authentication token (optional)
SLACK_WEBHOOK            # Slack webhook for notifications (optional)
```

## Required AWS Setup

### 1. IAM User Permissions

The AWS user needs these permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecs:DescribeTaskDefinition",
        "ecs:RegisterTaskDefinition",
        "ecs:UpdateService",
        "ecs:DescribeServices",
        "iam:PassRole"
      ],
      "Resource": "*"
    }
  ]
}
```

### 2. ECR Repository

Ensure the ECR repository exists:
```bash
aws ecr create-repository --repository-name somdip/container-platform --region us-east-1
```

### 3. ECS Cluster and Service

The following must exist:
- ECS Cluster: `somdip-dev-cluster`
- ECS Service: `container-platform`

## Setup Instructions

### 1. Fork/Clone Repository

```bash
git clone https://github.com/somdip-2310/container-portfolio.git
cd container-portfolio
```

### 2. Configure GitHub Secrets

Go to: Repository → Settings → Secrets and variables → Actions → New repository secret

Add all required secrets listed above.

### 3. Update Workflow Variables

Edit `.github/workflows/ci-cd.yml` if needed:

```yaml
env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: somdip/container-platform
  ECS_CLUSTER: somdip-dev-cluster
  ECS_SERVICE: container-platform
  CONTAINER_NAME: platform
```

### 4. Enable GitHub Actions

Go to: Repository → Actions → Enable workflows

### 5. Test the Pipeline

Create a pull request or push to `main`:

```bash
git checkout -b test-ci
# Make some changes
git add .
git commit -m "Test CI/CD pipeline"
git push origin test-ci
# Create PR on GitHub
```

## Workflow Badges

Add these badges to your README.md:

```markdown
![CI/CD](https://github.com/somdip-2310/container-portfolio/workflows/CI/CD%20Pipeline/badge.svg)
![PR Checks](https://github.com/somdip-2310/container-portfolio/workflows/Pull%20Request%20Checks/badge.svg)
```

## Monitoring Deployments

### View Workflow Runs

1. Go to the **Actions** tab in your repository
2. Click on a workflow run to see details
3. Expand jobs to see individual steps

### Check ECS Deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster somdip-dev-cluster \
  --services container-platform \
  --region us-east-1

# Check running tasks
aws ecs list-tasks \
  --cluster somdip-dev-cluster \
  --service-name container-platform \
  --region us-east-1

# View service logs
aws logs tail /ecs/container-platform --follow --region us-east-1
```

## Troubleshooting

### Build Fails

1. Check Maven build logs in the workflow output
2. Ensure all dependencies are available
3. Run build locally: `mvn clean package`

### Docker Build Fails

1. Check Dockerfile syntax
2. Ensure base images are accessible
3. Test locally: `docker build -t test .`

### Deployment Fails

1. Verify AWS credentials
2. Check ECS service exists
3. Verify task definition is valid
4. Check CloudWatch logs for errors

### Tests Fail

1. Review test output in workflow
2. Run tests locally: `mvn test`
3. Check for flaky tests
4. Verify test dependencies

## Best Practices

1. **Never commit secrets** - Use GitHub Secrets
2. **Keep workflows DRY** - Use reusable workflows
3. **Use caching** - Speed up builds with dependency caching
4. **Fail fast** - Run quick tests before slow ones
5. **Monitor costs** - GitHub Actions minutes are limited
6. **Use matrix builds** - Test multiple Java versions if needed

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [AWS ECS Deploy GitHub Action](https://github.com/aws-actions/amazon-ecs-deploy-task-definition)
- [Docker Build Push Action](https://github.com/docker/build-push-action)
- [Maven in GitHub Actions](https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-maven)
