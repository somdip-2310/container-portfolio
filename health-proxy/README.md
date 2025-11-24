# Health Proxy Sidecar

A lightweight health check proxy that automatically detects if user containers have health endpoints.

## Features

- **Smart Health Detection**: Automatically tries multiple common health endpoints
- **Fallback Strategy**: Falls back to root path if no health endpoint exists
- **Flexible Status Codes**: Accepts any 2xx or 3xx response as healthy
- **Zero User Configuration**: Works automatically for all containers

## How It Works

1. Runs as a sidecar container alongside user's application
2. Listens on port 9090 for health checks
3. Tries these endpoints on the user's app (in order):
   - `/health`
   - `/healthz`
   - `/actuator/health` (Spring Boot)
   - `/api/health`
   - `/` (root fallback)
4. Returns 200 if ANY endpoint responds with 2xx/3xx
5. Returns 503 if ALL endpoints fail

## Environment Variables

- `HEALTH_PROXY_PORT`: Port for health proxy (default: 9090)
- `USER_APP_PORT`: User's application port (default: 3000)
- `USER_APP_HOST`: User's application host (default: localhost)

## Building

```bash
docker build -t health-proxy:latest .
```

## Testing Locally

```bash
# Start user app on port 3000
docker run -d --name myapp -p 3000:3000 myapp:latest

# Start health proxy
docker run --rm \
  --network container:myapp \
  -e USER_APP_PORT=3000 \
  health-proxy:latest

# Check health
curl http://localhost:9090/health
```

## ECS Task Definition Integration

```json
{
  "containerDefinitions": [
    {
      "name": "user-app",
      "image": "user/app:latest",
      "portMappings": [{"containerPort": 3000}]
    },
    {
      "name": "health-proxy",
      "image": "health-proxy:latest",
      "essential": false,
      "portMappings": [{"containerPort": 9090}],
      "environment": [
        {"name": "USER_APP_PORT", "value": "3000"},
        {"name": "USER_APP_HOST", "value": "localhost"}
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:9090/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

## Benefits

✅ Users don't need to implement health endpoints
✅ Works with any application (Node.js, Python, Java, Go, etc.)
✅ Doesn't mask real health check failures
✅ Provides detailed health check information
✅ Minimal resource overhead (~10MB memory, <1% CPU)
