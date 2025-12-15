# Deploying with Just Docker (No Docker Compose)

You can absolutely use just Docker commands on your VPS! Here's how:

## Option 1: Simple Docker Commands

### Initial Setup

```bash
# Pull the image from Docker Hub
docker pull paramsavjanidev/attendance-io-backend:latest

# Create a network (optional, but recommended)
docker network create attendance-io-network

# Run the container
docker run -d \
  --name attendance-io-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  --network attendance-io-network \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="your-database-url" \
  -e SPRING_DATASOURCE_USERNAME="your-username" \
  -e SPRING_DATASOURCE_PASSWORD="your-password" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID="your-client-id" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET="your-secret" \
  paramsavjanidev/attendance-io-backend:latest
```
