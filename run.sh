#!/bin/bash

# Simple script to run Attendance IO Backend with Docker
# Usage: ./run.sh

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Starting Attendance IO Backend...${NC}"

# Check if .env file exists
if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠️  .env file not found. Please create it with your environment variables.${NC}"
    exit 1
fi

# Load environment variables
set -a
source .env
set +a

# Create network if it doesn't exist
docker network create attendance-io-network 2>/dev/null || true

# Stop and remove existing container if it exists
if [ "$(docker ps -aq -f name=attendance-io-backend)" ]; then
    echo -e "${YELLOW}🛑 Stopping existing container...${NC}"
    docker stop attendance-io-backend 2>/dev/null || true
    docker rm attendance-io-backend 2>/dev/null || true
fi

# Pull latest image
echo -e "${GREEN}📦 Pulling latest image...${NC}"
docker pull paramsavjanidev/attendance-io-backend:latest

# Run the container
echo -e "${GREEN}🚀 Starting container...${NC}"
docker run -d \
  --name attendance-io-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  --network attendance-io-network \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}" \
  -e SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}" \
  -e SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID="${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID}" \
  -e SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET="${SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET}" \
  paramsavjanidev/attendance-io-backend:latest

# Wait a moment
sleep 2

# Check if container is running
if [ "$(docker ps -q -f name=attendance-io-backend)" ]; then
    echo -e "${GREEN}✅ Container started successfully!${NC}"
    echo -e "${GREEN}📝 View logs with: docker logs -f attendance-io-backend${NC}"
    echo -e "${GREEN}🌐 Application should be available at: http://localhost:8080${NC}"
else
    echo -e "${RED}❌ Container failed to start. Check logs with: docker logs attendance-io-backend${NC}"
    exit 1
fi

