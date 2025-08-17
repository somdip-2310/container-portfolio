# Container Hosting Platform

A simple container hosting platform built on AWS ECS that allows users to deploy Docker containers with a single command.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- AWS Account with appropriate permissions
- AWS CLI configured with credentials

## Project Structure

```
container-platform/
├── src/
│   └── main/
│       ├── java/
│       │   └── dev/somdip/containerplatform/
│       │       ├── config/          # Configuration classes
│       │       ├── controller/      # REST controllers
│       │       ├── model/          # Domain models
│       │       ├── repository/     # Data access layer
│       │       ├── service/        # Business logic
│       │       └── utils/          # Utility classes
│       └── resources/
│           ├── application.properties
│           ├── application-local.properties
│           └── templates/          # Thymeleaf templates
└── pom.xml
```

## AWS Services Used

- **ECS (Elastic Container Service)**: Container orchestration
- **DynamoDB**: NoSQL database for storing user, container, and deployment data
- **S3**: Object storage for logs and backups
- **ALB (Application Load Balancer)**: Traffic distribution
- **Route53**: DNS management
- **CloudWatch**: Monitoring and logging
- **Secrets Manager**: Secure credential storage
- **ECR (Elastic Container Registry)**: Docker image storage

## Local Development Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd container-platform
```

### 2. Configure AWS Credentials

Option 1: Use AWS CLI (Recommended)
```bash
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region: us-east-1
# Enter default output format: json
```

Option 2: Set Environment Variables
```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
```

### 3. Build the Project

```bash
mvn clean install
```

### 4. Run Locally

```bash
# Run with local profile
mvn spring-boot:run -Dspring.profiles.active=local

# Or build and run the JAR
mvn clean package
java -jar target/container-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

### 5. Access the Application

- Application: http://localhost:8085
- Health Check: http://localhost:8085/health
- Detailed Health: http://localhost:8085/health/detailed

## Database Tables

The application will automatically create the following DynamoDB tables on startup:

1. **container-platform-users**
   - Partition Key: userId
   - Global Secondary Indexes:
     - EmailIndex (Partition Key: email)
     - ApiKeyIndex (Partition Key: apiKey)

2. **container-platform-containers**
   - Partition Key: containerId
   - Global Secondary Index:
     - UserIdIndex (Partition Key: userId)

3. **container-platform-deployments**
   - Partition Key: deploymentId
   - Global Secondary Index:
     - ContainerIdIndex (Partition Key: containerId)

## API Endpoints

### Health Check
- `GET /health` - Basic health check
- `GET /health/detailed` - Detailed health check with AWS service status

### Authentication (Coming in Day 3-4)
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/auth/logout` - User logout
- `POST /api/auth/refresh` - Refresh JWT token

### Container Management (Coming in Day 5-7)
- `GET /api/containers` - List user's containers
- `POST /api/containers` - Create new container
- `GET /api/containers/{id}` - Get container details
- `PUT /api/containers/{id}` - Update container
- `DELETE /api/containers/{id}` - Delete container

### Deployments (Coming in Week 2)
- `POST /api/deployments` - Deploy a container
- `GET /api/deployments/{id}` - Get deployment status
- `GET /api/containers/{id}/deployments` - List container deployments

## Configuration Properties

Key configuration properties in `application.properties`:

```properties
# AWS Configuration
aws.region=us-east-1
aws.ecs.cluster=somdip-dev-cluster
aws.dynamodb.tables.users=container-platform-users
aws.dynamodb.tables.containers=container-platform-containers
aws.dynamodb.tables.deployments=container-platform-deployments

# Application Configuration
server.port=8085
app.container.limits.free=1
app.container.limits.starter=3
app.container.limits.pro=10
app.container.limits.scale=25
```

## Testing AWS Connectivity

After starting the application, test AWS connectivity:

```bash
# Basic health check
curl http://localhost:8085/health

# Detailed health check (tests DynamoDB, ECS, S3)
curl http://localhost:8085/health/detailed
```

## Troubleshooting

### DynamoDB Table Creation Fails
- Ensure your AWS credentials have DynamoDB permissions
- Check if tables already exist in the AWS Console
- Set `aws.dynamodb.initialize=false` if tables exist

### ECS Access Denied
- Verify IAM permissions for ECS operations
- Check if the ECS cluster name is correct

### S3 Bucket Not Found
- Ensure S3 buckets exist or create them manually
- Verify bucket names in configuration

## Next Steps

This completes Day 1-2 of the implementation plan:
- ✅ Spring Boot project setup
- ✅ AWS SDK integration
- ✅ DynamoDB models and repositories
- ✅ Basic health check endpoints

Day 3-4 will implement:
- User authentication with JWT
- Security configuration
- User management endpoints

## License

Copyright 2024 Somdip Communications. All rights reserved.