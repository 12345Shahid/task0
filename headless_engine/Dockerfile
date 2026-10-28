FROM node:20-bookworm-slim

# Install system dependencies, Google Chrome Stable, and Xvfb for virtual display
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    xvfb \
    fonts-liberation \
    fonts-noto-color-emoji \
    libnss3 \
    libatk-bridge2.0-0 \
    libxcomposite1 \
    libxrandr2 \
    libxdamage1 \
    libgbm1 \
    libasound2 \
    libcups2 \
    procps \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Google Chrome Stable directly from Google official repository
RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

ENV CHROME_BIN=/usr/bin/google-chrome-stable
ENV DISPLAY=:99

WORKDIR /app

# Copy dependency manifests
COPY package*.json tsconfig.json ./

# Install project dependencies
RUN npm install

# Copy source code
COPY src/ ./src/

# Build TypeScript to JavaScript
RUN npm run build

# Create entrypoint runner script that starts Xvfb before launching the engine
RUN echo '#!/bin/bash\n\
rm -f /tmp/.X99-lock\n\
Xvfb :99 -screen 0 1920x1080x24 -ac &\n\
sleep 2\n\
exec npm run serve\n'\
> /app/entrypoint.sh && chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]
