#!/usr/bin/env bash
set -e

echo "=================================================================="
echo "🚀 DEPLOYING STEALTH ANTI-DETECT ENGINE ON HOSTINGER VPS"
echo "=================================================================="

# 1. Update OS and ensure prerequisites
if ! command -v docker &> /dev/null; then
    echo "📦 Installing Docker and Docker Compose..."
    apt-get update
    apt-get install -y ca-certificates curl gnupg lsb-release
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg || true
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi

# 2. Optimize system parameters for high-concurrency Chromium
echo "⚙️ Optimizing system parameters..."
sysctl -w fs.file-max=2097152 > /dev/null
sysctl -w vm.max_map_count=262144 > /dev/null

# 3. Build and launch container in background
echo "🔨 Building and launching container..."
docker compose down || true
docker compose up -d --build

echo "=================================================================="
echo "✅ DEPLOYMENT COMPLETE & RUNNING IN BACKGROUND!"
echo ""
echo "📊 To monitor live sessions and telemetry in real time, run:"
echo "   docker logs -f stealth-traffic-engine"
echo ""
echo "🛑 To stop the engine at any time, run:"
echo "   docker compose down"
echo "=================================================================="
