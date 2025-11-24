FROM node:20-alpine

WORKDIR /app

# Copy health proxy server
COPY server.js .

# Run as non-root user
USER node

# Expose health proxy port
EXPOSE 9090

# Set default environment variables
ENV HEALTH_PROXY_PORT=9090
ENV USER_APP_PORT=3000
ENV USER_APP_HOST=localhost

# Start health proxy
CMD ["node", "server.js"]
