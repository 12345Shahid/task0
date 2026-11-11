const express = require('express');
const http = require('http');
const cors = require('cors');
const path = require('path');
const SessionManager = require('./engine/sessionManager');
const StreamGateway = require('./engine/streamGateway');
const { PROFILES } = require('./config/profiles');
const { COUNTRIES } = require('./config/countries');

const app = express();
const server = http.createServer(app);
const PORT = process.env.PORT || 4000;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Multi-User Session Manager & WebSocket Streaming Gateway
const sessionManager = new SessionManager({ maxSessions: 15, idleTimeoutMs: 30 * 60 * 1000 });
const streamGateway = new StreamGateway(server, sessionManager);

// ==========================================
// Health & Telemetry Endpoints
// ==========================================
app.get('/health', (req, res) => {
  const memoryUsage = process.memoryUsage();
  res.json({
    status: 'healthy',
    uptime: Math.round(process.uptime()),
    activeSessions: sessionManager.sessions.size,
    memory: {
      rssMB: Math.round(memoryUsage.rss / 1024 / 1024),
      heapUsedMB: Math.round(memoryUsage.heapUsed / 1024 / 1024)
    },
    timestamp: new Date().toISOString()
  });
});

// ==========================================
// REST API Endpoints
// ==========================================

// 1. Engine Status
app.get('/api/status', (req, res) => {
  const sessions = sessionManager.listSessions();
  res.json({
    engine: 'AuraBrowser Cloud Web Engine (Phase 4)',
    status: 'online',
    totalSessions: sessions.length,
    sessions
  });
});

// 2. Profiles & Countries
app.get('/api/profiles', (req, res) => {
  const profileList = Object.values(PROFILES).map(p => ({
    id: p.id,
    name: p.name,
    isMobile: p.isMobile,
    platform: p.platform,
    gpu: `${p.webgl.vendor} - ${p.webgl.renderer}`,
    screen: `${p.screen.width}x${p.screen.height}`
  }));
  res.json({ profiles: profileList });
});

app.get('/api/countries', (req, res) => {
  res.json({ countries: Object.values(COUNTRIES) });
});

// 3. Multi-Session Management
app.get('/api/sessions', (req, res) => {
  res.json({ sessions: sessionManager.listSessions() });
});

app.post('/api/sessions/create', async (req, res) => {
  try {
    const { profileId = 'poco_m3', countryCode = 'US' } = req.body;
    const sessionRecord = await sessionManager.createSession({ profileId, countryCode });
    res.json({
      success: true,
      sessionId: sessionRecord.id,
      session: sessionRecord.meta
    });
  } catch (err) {
    console.error('Session creation error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/sessions/:id/stop', async (req, res) => {
  try {
    const success = await sessionManager.destroySession(req.params.id);
    res.json({ success, message: success ? 'Session destroyed' : 'Session not found' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4. Default Session Convenience Endpoints (Backward Compatible with Phase 1-3)
app.post('/api/session/start', async (req, res) => {
  try {
    const { profileId = 'poco_m3', countryCode = 'US' } = req.body;
    const sessionRecord = await sessionManager.createSession({ profileId, countryCode });
    res.json({
      success: true,
      sessionId: sessionRecord.id,
      session: sessionRecord.meta
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/session/navigate', async (req, res) => {
  try {
    const { url, sessionId } = req.body;
    const session = sessionId ? sessionManager.getSession(sessionId) : sessionManager.getDefaultSession();
    if (!session || !session.isActive) {
      return res.status(400).json({ error: 'No active session' });
    }
    const navResult = await session.navigate(url);
    res.json({ success: true, ...navResult });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/session/stop', async (req, res) => {
  try {
    const { sessionId } = req.body;
    if (sessionId) {
      await sessionManager.destroySession(sessionId);
    } else {
      await sessionManager.destroyAll();
    }
    res.json({ success: true, message: 'Session(s) terminated' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// Phase 4 Web Dashboard Client UI
// ==========================================
app.get('/', (req, res) => {
  res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>AuraBrowser Cloud — Universal Anti-Detect Studio</title>
  <style>
    :root {
      --bg: #07090e;
      --card-bg: #0f172a;
      --header-bg: rgba(15, 23, 42, 0.95);
      --border: #1e293b;
      --border-focus: #38bdf8;
      --accent: #38bdf8;
      --accent-glow: rgba(56, 189, 248, 0.25);
      --text: #f8fafc;
      --muted: #94a3b8;
      --tab-bg: #1e293b;
      --tab-active: #2563eb;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 0;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      height: 100vh;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      user-select: none;
    }

    /* Top Command Header */
    header {
      background: var(--header-bg);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border);
      padding: 8px 16px;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      z-index: 50;
    }
    .brand {
      font-weight: 800;
      font-size: 15px;
      letter-spacing: -0.5px;
      background: linear-gradient(135deg, #38bdf8, #818cf8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin-right: 4px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 7px;
      border-radius: 999px;
      background: #0369a1;
      color: #e0f2fe;
    }
    select, input, button {
      background: #090d16;
      border: 1px solid var(--border);
      color: #fff;
      border-radius: 6px;
      font-size: 13px;
      outline: none;
      transition: all 0.2s;
    }
    select:focus, input:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 2px var(--accent-glow);
    }
    select { padding: 5px 10px; cursor: pointer; }
    button {
      padding: 5px 12px;
      cursor: pointer;
      font-weight: 600;
      background: #1e293b;
      display: flex;
      align-items: center;
      gap: 5px;
    }
    button:hover { background: #334155; }
    button.primary { background: #2563eb; }
    button.primary:hover { background: #1d4ed8; }
    button.danger { background: #991b1b; }
    button.danger:hover { background: #b91c1c; }

    /* Tabs Bar */
    .tabs-strip {
      background: #090e1a;
      border-bottom: 1px solid var(--border);
      padding: 6px 12px 0 12px;
      display: flex;
      align-items: center;
      gap: 6px;
      overflow-x: auto;
    }
    .tab-item {
      background: var(--tab-bg);
      border-radius: 6px 6px 0 0;
      border: 1px solid var(--border);
      border-bottom: none;
      padding: 6px 14px;
      font-size: 12px;
      font-weight: 500;
      color: var(--muted);
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      max-width: 180px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: all 0.15s;
    }
    .tab-item.active {
      background: var(--tab-active);
      color: #fff;
      font-weight: 600;
      box-shadow: 0 -2px 10px rgba(37, 99, 235, 0.4);
    }
    .tab-close {
      opacity: 0.6;
      border-radius: 50%;
      padding: 0 4px;
      font-size: 13px;
    }
    .tab-close:hover { opacity: 1; background: rgba(0, 0, 0, 0.3); }
    .new-tab-btn {
      padding: 4px 10px;
      font-size: 14px;
      background: transparent;
      border: none;
      color: var(--accent);
      cursor: pointer;
    }

    /* Sub Navigation Bar */
    .sub-bar {
      background: #0c1220;
      border-bottom: 1px solid var(--border);
      padding: 6px 16px;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
    }
    .url-group {
      display: flex;
      align-items: center;
      gap: 4px;
      flex: 1;
      min-width: 260px;
    }
    .url-input {
      flex: 1;
      padding: 6px 10px;
      font-family: monospace;
      font-size: 12px;
    }
    .quick-pill {
      background: #1e293b;
      border: 1px solid #334155;
      color: #94a3b8;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 10px;
      border-radius: 999px;
      cursor: pointer;
      transition: all 0.2s;
    }
    .quick-pill:hover {
      background: #38bdf8;
      color: #0f172a;
      border-color: #38bdf8;
    }

    /* Telemetry */
    .telemetry {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 11px;
      font-family: monospace;
      color: var(--muted);
      margin-left: auto;
    }
    .indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #ef4444;
      display: inline-block;
    }
    .indicator.live {
      background: #22c55e;
      box-shadow: 0 0 6px #22c55e;
    }

    /* Viewport Workspace */
    main {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #030712;
      position: relative;
      overflow: hidden;
      padding: 10px;
    }

    .device-frame {
      position: relative;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .device-frame.framed {
      padding: 12px;
      background: #111827;
      border-radius: 36px;
      border: 3px solid #374151;
      box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.9), inset 0 0 0 2px #1f2937;
    }
    .device-notch {
      display: none;
      position: absolute;
      top: 18px;
      width: 80px;
      height: 14px;
      background: #000;
      border-radius: 12px;
      z-index: 20;
    }
    .device-frame.framed .device-notch { display: block; }
    .screen-container {
      border-radius: 8px;
      overflow: hidden;
      background: #000;
      display: flex;
      box-shadow: 0 0 0 1px var(--border);
    }
    .device-frame.framed .screen-container {
      border-radius: 24px;
    }
    canvas {
      display: block;
      cursor: crosshair;
      touch-action: none;
    }

    /* Fast Paste Bar */
    .paste-bar {
      position: absolute;
      bottom: 12px;
      background: rgba(15, 23, 42, 0.9);
      backdrop-filter: blur(8px);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 6px 12px;
      display: flex;
      align-items: center;
      gap: 8px;
      z-index: 40;
      box-shadow: 0 10px 25px rgba(0,0,0,0.6);
    }
    .paste-input {
      width: 240px;
      padding: 5px 10px;
      font-size: 12px;
    }
  </style>
</head>
<body>

  <header>
    <div class="brand">
      <span>🛡️ AuraBrowser Cloud</span>
      <span class="badge">Production V4</span>
    </div>

    <!-- Profile Selector -->
    <select id="profileSelect">
      <option value="poco_m3">📱 Xiaomi POCO M3 (Snapdragon 662 • Adreno 610)</option>
      <option value="samsung_s24_ultra">📱 Samsung Galaxy S24 Ultra (Snapdragon 8 • Adreno 750)</option>
      <option value="oneplus_nord_n30">📱 OnePlus Nord N30 5G (Adreno 619)</option>
      <option value="iphone_15_pro_max">🍎 Apple iPhone 15 Pro Max (Apple GPU)</option>
      <option value="windows_11_pc">💻 Windows 11 Desktop (NVIDIA RTX 4070)</option>
    </select>

    <!-- Country Selector -->
    <select id="countrySelect">
      <option value="US">🇺🇸 United States</option>
      <option value="GB">🇬🇧 United Kingdom</option>
      <option value="AU">🇦🇺 Australia</option>
      <option value="CA">🇨🇦 Canada</option>
      <option value="DE">🇩🇪 Germany</option>
      <option value="FR">🇫🇷 France</option>
      <option value="IT">🇮🇹 Italy</option>
      <option value="ES">🇪🇸 Spain</option>
      <option value="RU">🇷🇺 Russia</option>
      <option value="NL">🇳🇱 Netherlands</option>
      <option value="JP">🇯🇵 Japan</option>
    </select>

    <button class="primary" onclick="spawnNewSession()">⚡ Spawn Sandbox</button>
    <button onclick="togglePhoneFrame()" id="frameToggleBtn">📱 Frame: On</button>
    <button class="danger" onclick="wipeIdentity()">🧹 Wipe Identity</button>

    <!-- Live Telemetry -->
    <div class="telemetry">
      <span id="statusIndicator" class="indicator"></span>
      <span id="bandwidthBadge">🛡️ 0.00 MB</span>
      <span id="fpsBadge">0 FPS</span>
      <span id="latencyBadge">-- ms</span>
    </div>
  </header>

  <!-- Multi-Tabs Strip -->
  <div class="tabs-strip" id="tabsStrip">
    <div class="tab-item active">Tab 1: Poco M3</div>
    <button class="new-tab-btn" onclick="addNewTab()" title="New Tab">+</button>
  </div>

  <!-- Sub Navigation Bar -->
  <div class="sub-bar">
    <button onclick="sendAction('back')">◀</button>
    <button onclick="sendAction('forward')">▶</button>
    <button onclick="sendAction('reload')">🔄</button>

    <div class="url-group">
      <input type="text" id="urlInput" class="url-input" value="https://browserscan.net/" placeholder="Enter URL or affiliate link...">
      <button class="primary" onclick="navigate()">Go</button>
    </div>

    <div class="quick-pill" onclick="quickNav('https://browserscan.net/')">🔍 BrowserScan 100%</div>
    <div class="quick-pill" onclick="quickNav('https://pixelscan.net/')">🛡️ Pixelscan Green</div>
    <div class="quick-pill" onclick="quickNav('https://api.ipify.org?format=json')">🌐 My IP Check</div>
  </div>

  <!-- Main Viewport Workspace -->
  <main>
    <div class="device-frame framed" id="deviceFrame">
      <div class="device-notch"></div>
      <div class="screen-container">
        <canvas id="screenCanvas" width="412" height="915"></canvas>
      </div>
    </div>

    <!-- Fast Text Paste Bar -->
    <div class="paste-bar">
      <input type="text" id="pasteInput" class="paste-input" placeholder="Paste link or text to send...">
      <button onclick="pasteToRemote()">📋 Insert Text</button>
    </div>
  </main>

  <script>
    let ws = null;
    let canvas = document.getElementById('screenCanvas');
    let ctx = canvas.getContext('2d');
    let currentViewport = { width: 412, height: 915 };
    let pingInterval = null;
    let lastPing = Date.now();
    let isFramed = true;
    let currentSessionId = null;

    function connectWebSocket(sessionId = null) {
      if (ws) {
        try { ws.close(); } catch(e) {}
      }

      const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = protocol + '//' + location.host + '/stream' + (sessionId ? '?sessionId=' + sessionId : '');
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        document.getElementById('statusIndicator').className = 'indicator live';
        if (pingInterval) clearInterval(pingInterval);
        pingInterval = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            lastPing = Date.now();
            ws.send(JSON.stringify({ type: 'ping', time: lastPing }));
          }
        }, 2000);
      };

      ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);

        if (msg.type === 'frame') {
          const img = new Image();
          img.onload = () => {
            if (canvas.width !== img.width || canvas.height !== img.height) {
              canvas.width = img.width;
              canvas.height = img.height;
              adjustCanvasScale();
            }
            ctx.drawImage(img, 0, 0);
          };
          img.src = 'data:image/jpeg;base64,' + msg.data;

          if (msg.fps !== undefined) {
            document.getElementById('fpsBadge').innerText = msg.fps + ' FPS';
          }
          if (msg.bandwidthMB !== undefined) {
            document.getElementById('bandwidthBadge').innerText = '🛡️ ' + msg.bandwidthMB + ' MB';
          }
        } else if (msg.type === 'init') {
          currentSessionId = msg.sessionId;
          if (msg.viewport) {
            currentViewport = msg.viewport;
            canvas.width = msg.viewport.width;
            canvas.height = msg.viewport.height;
            adjustCanvasScale();
          }
          if (msg.url) {
            document.getElementById('urlInput').value = msg.url;
          }
          if (msg.tabs) {
            renderTabs(msg.tabs);
          }
        } else if (msg.type === 'tabs_updated') {
          renderTabs(msg.tabs);
        } else if (msg.type === 'url_changed') {
          document.getElementById('urlInput').value = msg.url;
        } else if (msg.type === 'pong') {
          const latency = Date.now() - msg.clientTime;
          document.getElementById('latencyBadge').innerText = latency + ' ms';
        } else if (msg.type === 'notification') {
          alert(msg.message);
        }
      };

      ws.onclose = () => {
        document.getElementById('statusIndicator').className = 'indicator';
        document.getElementById('fpsBadge').innerText = '0 FPS';
        setTimeout(() => connectWebSocket(currentSessionId), 1500);
      };
    }

    function renderTabs(tabs) {
      const container = document.getElementById('tabsStrip');
      container.innerHTML = '';
      tabs.forEach(t => {
        const tabEl = document.createElement('div');
        tabEl.className = 'tab-item' + (t.active ? ' active' : '');
        tabEl.onclick = () => switchTab(t.id);

        const titleSpan = document.createElement('span');
        titleSpan.innerText = t.title || 'Tab';
        tabEl.appendChild(titleSpan);

        if (tabs.length > 1) {
          const closeBtn = document.createElement('span');
          closeBtn.className = 'tab-close';
          closeBtn.innerHTML = '&times;';
          closeBtn.onclick = (e) => {
            e.stopPropagation();
            closeTab(t.id);
          };
          tabEl.appendChild(closeBtn);
        }

        container.appendChild(tabEl);
      });

      const addBtn = document.createElement('button');
      addBtn.className = 'new-tab-btn';
      addBtn.innerText = '+';
      addBtn.onclick = addNewTab;
      container.appendChild(addBtn);
    }

    function switchTab(tabId) {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'switch_tab', tabId }));
      }
    }

    function addNewTab() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'new_tab', url: 'https://browserscan.net/' }));
      }
    }

    function closeTab(tabId) {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'close_tab', tabId }));
      }
    }

    function adjustCanvasScale() {
      const main = document.querySelector('main');
      const maxW = main.clientWidth - (isFramed ? 60 : 30);
      const maxH = main.clientHeight - (isFramed ? 100 : 40);
      const scale = Math.min(maxW / canvas.width, maxH / canvas.height, 1);
      canvas.style.width = (canvas.width * scale) + 'px';
      canvas.style.height = (canvas.height * scale) + 'px';
    }

    window.addEventListener('resize', adjustCanvasScale);

    function togglePhoneFrame() {
      isFramed = !isFramed;
      const frameEl = document.getElementById('deviceFrame');
      const btn = document.getElementById('frameToggleBtn');
      if (isFramed) {
        frameEl.classList.add('framed');
        btn.innerText = '📱 Frame: On';
      } else {
        frameEl.classList.remove('framed');
        btn.innerText = '🖥️ Frame: Off';
      }
      adjustCanvasScale();
    }

    // Input Gateway
    function getNormalizedCoords(e) {
      const rect = canvas.getBoundingClientRect();
      const scaleX = canvas.width / rect.width;
      const scaleY = canvas.height / rect.height;
      return {
        x: (e.clientX - rect.left) * scaleX,
        y: (e.clientY - rect.top) * scaleY
      };
    }

    let isPointerDown = false;

    canvas.addEventListener('pointerdown', (e) => {
      isPointerDown = true;
      const coords = getNormalizedCoords(e);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'mouse',
          event: 'mousedown',
          x: coords.x,
          y: coords.y,
          button: e.button === 2 ? 'right' : 'left'
        }));
      }
    });

    canvas.addEventListener('pointermove', (e) => {
      if (!isPointerDown) return;
      const coords = getNormalizedCoords(e);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'mouse',
          event: 'mousemove',
          x: coords.x,
          y: coords.y,
          button: 'left'
        }));
      }
    });

    window.addEventListener('pointerup', (e) => {
      if (!isPointerDown) return;
      isPointerDown = false;
      const coords = getNormalizedCoords(e);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'mouse',
          event: 'mouseup',
          x: coords.x,
          y: coords.y,
          button: e.button === 2 ? 'right' : 'left'
        }));
      }
    });

    canvas.addEventListener('wheel', (e) => {
      e.preventDefault();
      const coords = getNormalizedCoords(e);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'wheel',
          x: coords.x,
          y: coords.y,
          deltaX: e.deltaX,
          deltaY: e.deltaY
        }));
      }
    }, { passive: false });

    // Keyboard
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'key',
          event: 'keydown',
          key: e.key,
          code: e.code,
          text: e.key.length === 1 ? e.key : '',
          keyCode: e.keyCode
        }));
      }
    });

    window.addEventListener('keyup', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'key',
          event: 'keyup',
          key: e.key,
          code: e.code,
          keyCode: e.keyCode
        }));
      }
    });

    // Session Management
    async function spawnNewSession() {
      const profileId = document.getElementById('profileSelect').value;
      const countryCode = document.getElementById('countrySelect').value;
      const res = await fetch('/api/sessions/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ profileId, countryCode })
      });
      const data = await res.json();
      if (data.success && data.sessionId) {
        currentSessionId = data.sessionId;
        connectWebSocket(currentSessionId);
      }
    }

    function navigate() {
      const url = document.getElementById('urlInput').value;
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'navigate', url }));
      }
    }

    function quickNav(url) {
      document.getElementById('urlInput').value = url;
      navigate();
    }

    document.getElementById('urlInput').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') navigate();
    });

    function sendAction(action) {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: action }));
      }
    }

    function pasteToRemote() {
      const text = document.getElementById('pasteInput').value;
      if (text && ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'paste_text', text }));
        document.getElementById('pasteInput').value = '';
      }
    }

    document.getElementById('pasteInput').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') pasteToRemote();
    });

    function wipeIdentity() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'clear_identity' }));
      }
    }

    connectWebSocket();
  </script>
</body>
</html>`);
});

// Start Server & Graceful Shutdown
server.listen(PORT, async () => {
  console.log(`====================================================`);
  console.log(`🚀 AuraBrowser Cloud Web Engine (Phase 4) running on port ${PORT}`);
  console.log(`🔗 Web Dashboard: http://localhost:${PORT}`);
  console.log(`🏥 Health Status : http://localhost:${PORT}/health`);
  console.log(`====================================================`);

  // Auto-initialize default session with POCO M3 & US residential proxy preloaded to BrowserScan
  try {
    console.log('[Startup] Auto-initializing primary POCO M3 session with 🇺🇸 US proxy...');
    const record = await sessionManager.createSession({ profileId: 'poco_m3', countryCode: 'US' });
    console.log('[Startup] Primary session ready! Pre-navigating to BrowserScan...');
    await record.session.navigate('https://www.browserscan.net/');
    console.log('[Startup] BrowserScan loaded successfully! Ready for live user stream.');
  } catch (err) {
    console.warn('[Startup] Auto-session warning:', err.message);
  }
});

process.on('SIGTERM', async () => {
  console.log('[Server] Shutting down, cleaning all sessions...');
  await sessionManager.destroyAll();
  process.exit(0);
});

process.on('SIGINT', async () => {
  console.log('[Server] SIGINT received, cleaning all sessions...');
  await sessionManager.destroyAll();
  process.exit(0);
});
