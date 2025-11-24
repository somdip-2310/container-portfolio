const http = require('http');

// Configuration from environment variables
const HEALTH_PROXY_PORT = process.env.HEALTH_PROXY_PORT || 9090;
const USER_APP_PORT = process.env.USER_APP_PORT || 3000;
const USER_APP_HOST = process.env.USER_APP_HOST || 'localhost';

// Paths to try for user's health endpoint
const HEALTH_PATHS = ['/health', '/healthz', '/actuator/health', '/api/health', '/'];

/**
 * Check if user's application responds to a specific path
 */
function checkUserHealth(path) {
  return new Promise((resolve) => {
    const options = {
      hostname: USER_APP_HOST,
      port: USER_APP_PORT,
      path: path,
      method: 'GET',
      timeout: 3000
    };

    const req = http.request(options, (res) => {
      // Accept any 2xx or 3xx status code
      const isHealthy = res.statusCode >= 200 && res.statusCode < 400;
      resolve({ healthy: isHealthy, status: res.statusCode, path: path });
    });

    req.on('error', () => {
      resolve({ healthy: false, status: 0, path: path });
    });

    req.on('timeout', () => {
      req.destroy();
      resolve({ healthy: false, status: 0, path: path });
    });

    req.end();
  });
}

/**
 * Try multiple health check paths in order
 */
async function smartHealthCheck() {
  for (const path of HEALTH_PATHS) {
    const result = await checkUserHealth(path);
    if (result.healthy) {
      console.log(`Health check passed: ${path} returned ${result.status}`);
      return { success: true, checkedPath: path, status: result.status };
    }
  }

  console.error('Health check failed: no responsive endpoints found');
  return { success: false, error: 'No responsive endpoints' };
}

// Create health proxy server
const server = http.createServer(async (req, res) => {
  if (req.url === '/health' || req.url === '/healthz') {
    try {
      const result = await smartHealthCheck();

      if (result.success) {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          status: 'healthy',
          userAppPort: USER_APP_PORT,
          checkedPath: result.checkedPath,
          timestamp: new Date().toISOString()
        }));
      } else {
        res.writeHead(503, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          status: 'unhealthy',
          error: result.error,
          timestamp: new Date().toISOString()
        }));
      }
    } catch (error) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 'error',
        error: error.message,
        timestamp: new Date().toISOString()
      }));
    }
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found. Use /health or /healthz');
  }
});

server.listen(HEALTH_PROXY_PORT, () => {
  console.log(`Health Proxy running on port ${HEALTH_PROXY_PORT}`);
  console.log(`Monitoring user app at ${USER_APP_HOST}:${USER_APP_PORT}`);
  console.log(`Will check paths: ${HEALTH_PATHS.join(', ')}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully');
  server.close(() => {
    console.log('Health proxy stopped');
    process.exit(0);
  });
});
