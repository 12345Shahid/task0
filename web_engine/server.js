const express = require('express');
const cors = require('cors');
const path = require('path');
const BrowserSession = require('./engine/browserSession');
const { PROFILES } = require('./config/profiles');
const { COUNTRIES } = require('./config/countries');

const app = express();
const PORT = process.env.PORT || 4000;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Active Browser Session singleton (can be expanded to multi-session pool in Phase 4)
let activeSession = null;

// ==========================================
// API Endpoints
// ==========================================

// 1. Engine & Session Status
app.get('/api/status', async (req, res) => {
  res.json({
    engine: 'AuraBrowser Web Engine (Phase 1)',
    status: 'online',
    hasActiveSession: !!(activeSession && activeSession.isActive),
    currentSession: activeSession && activeSession.isActive ? {
      profile: activeSession.profile.name,
      country: activeSession.country.name,
      timezone: activeSession.country.timezone,
      url: activeSession.page ? activeSession.page.url() : null
    } : null
  });
});

// 2. Available Profiles
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

// 3. Available Countries
app.get('/api/countries', (req, res) => {
  res.json({ countries: Object.values(COUNTRIES) });
});

// 4. Start New Anti-Detect Session
app.post('/api/session/start', async (req, res) => {
  try {
    const { profileId = 'poco_m3', countryCode = 'US' } = req.body;
    
    if (activeSession) {
      await activeSession.close();
    }

    activeSession = new BrowserSession();
    const result = await activeSession.start({ profileId, countryCode });

    res.json({
      success: true,
      message: 'Session initialized with anti-detect fingerprinting and residential proxy.',
      session: result
    });
  } catch (err) {
    console.error('Error starting session:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// 5. Navigate
app.post('/api/session/navigate', async (req, res) => {
  try {
    const { url } = req.body;
    if (!url) return res.status(400).json({ error: 'URL is required' });
    if (!activeSession || !activeSession.isActive) {
      return res.status(400).json({ error: 'No active session. Call /api/session/start first.' });
    }

    const navResult = await activeSession.navigate(url);
    res.json({ success: true, ...navResult });
  } catch (err) {
    console.error('Navigation error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// 6. Get Public IP
app.get('/api/session/ip', async (req, res) => {
  try {
    if (!activeSession || !activeSession.isActive) {
      return res.status(400).json({ error: 'No active session' });
    }
    const ipInfo = await activeSession.getPublicIpInfo();
    res.json({ success: true, ipInfo });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 7. Viewport Screenshot
app.get('/api/session/screenshot', async (req, res) => {
  try {
    if (!activeSession || !activeSession.isActive) {
      return res.status(400).json({ error: 'No active session' });
    }
    const imageBuffer = await activeSession.takeScreenshot({ encoding: 'binary' });
    res.set('Content-Type', 'image/jpeg');
    res.send(imageBuffer);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 8. Run Fingerprint Audit / Evaluate JS
app.post('/api/session/evaluate', async (req, res) => {
  try {
    const { script } = req.body;
    if (!script) return res.status(400).json({ error: 'script string is required' });
    if (!activeSession || !activeSession.isActive) {
      return res.status(400).json({ error: 'No active session' });
    }
    const result = await activeSession.evaluate(script);
    res.json({ success: true, result });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 9. Stop Session
app.post('/api/session/stop', async (req, res) => {
  try {
    if (activeSession) {
      await activeSession.close();
      activeSession = null;
    }
    res.json({ success: true, message: 'Session terminated' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// Embedded Quick-Test UI (Static HTML)
// ==========================================
app.get('/', (req, res) => {
  res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>AuraBrowser Web Engine — Phase 1 Test Terminal</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
    .card { background: #1e293b; border-radius: 12px; border: 1px solid #334155; padding: 20px; max-width: 900px; margin: 0 auto 20px auto; }
    h1 { color: #38bdf8; margin-top: 0; }
    .badge { display: inline-block; padding: 4px 10px; border-radius: 9999px; font-size: 13px; font-weight: 600; background: #0369a1; color: #e0f2fe; margin-bottom: 15px; }
    label { display: block; margin-top: 10px; font-weight: 500; color: #94a3b8; }
    select, input, button { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-top: 6px; border-radius: 8px; border: 1px solid #475569; background: #0f172a; color: #fff; font-size: 15px; }
    button { background: #2563eb; font-weight: 600; border: none; cursor: pointer; margin-top: 16px; transition: background 0.2s; }
    button:hover { background: #1d4ed8; }
    .row { display: flex; gap: 12px; }
    .col { flex: 1; }
    #log { background: #020617; border: 1px solid #1e293b; border-radius: 8px; padding: 12px; font-family: monospace; font-size: 13px; color: #34d399; min-height: 120px; max-height: 250px; overflow-y: auto; white-space: pre-wrap; margin-top: 15px; }
    #preview { margin-top: 20px; text-align: center; }
    #preview img { max-width: 100%; border-radius: 8px; border: 1px solid #475569; display: none; }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">🚀 AuraBrowser Web Engine (Phase 1)</div>
    <h1>Anti-Detect Cloud Session Launcher</h1>
    <p style="color: #94a3b8; margin-top: -5px;">Run isolated, hardware-spoofed browser sessions with residential proxy targeting directly from any device.</p>

    <div class="row">
      <div class="col">
        <label>Device Profile</label>
        <select id="profileSelect">
          <option value="poco_m3">Xiaomi POCO M3 (Snapdragon 662 • Adreno 610)</option>
          <option value="samsung_s24_ultra">Samsung Galaxy S24 Ultra (Snapdragon 8 Gen 3 • Adreno 750)</option>
          <option value="oneplus_nord_n30">OnePlus Nord N30 5G (Snapdragon 695 • Adreno 619)</option>
          <option value="iphone_15_pro_max">Apple iPhone 15 Pro Max (A17 Pro • Apple GPU)</option>
          <option value="windows_11_pc">Windows 11 Desktop (NVIDIA RTX 4070)</option>
        </select>
      </div>
      <div class="col">
        <label>Proxy Country Target</label>
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
      </div>
    </div>

    <button onclick="startSession()">1. Initialize Stealth Session</button>

    <hr style="border-color: #334155; margin: 24px 0;">

    <label>Target URL to Navigate</label>
    <div class="row">
      <div class="col" style="flex: 3;">
        <input type="text" id="targetUrl" value="https://api.ipify.org?format=json" placeholder="https://...">
      </div>
      <div class="col" style="flex: 1;">
        <button style="margin-top: 6px; background: #059669;" onclick="navigate()">2. Navigate & Verify</button>
      </div>
    </div>

    <div id="log">Logs will appear here...</div>

    <div id="preview">
      <img id="shot" alt="Viewport Preview">
    </div>
  </div>

  <script>
    function log(msg) {
      const el = document.getElementById('log');
      el.innerText += '\\n' + msg;
      el.scrollTop = el.scrollHeight;
    }

    async function startSession() {
      const profileId = document.getElementById('profileSelect').value;
      const countryCode = document.getElementById('countrySelect').value;
      log('Starting session with profile: ' + profileId + ' in country: ' + countryCode + '...');
      try {
        const res = await fetch('/api/session/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ profileId, countryCode })
        });
        const data = await res.json();
        log('Session started successfully: ' + JSON.stringify(data.session));
      } catch (e) {
        log('Error: ' + e.message);
      }
    }

    async function navigate() {
      const url = document.getElementById('targetUrl').value;
      log('Navigating to ' + url + '...');
      try {
        const res = await fetch('/api/session/navigate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url })
        });
        const data = await res.json();
        log('Navigated: ' + JSON.stringify(data));
        
        // Refresh screenshot
        const img = document.getElementById('shot');
        img.src = '/api/session/screenshot?t=' + Date.now();
        img.style.display = 'block';
      } catch (e) {
        log('Navigation Error: ' + e.message);
      }
    }
  </script>
</body>
</html>
  `);
});

// Start Server
app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`🚀 AuraBrowser Web Engine (Phase 1) running on port ${PORT}`);
  console.log(`🔗 Dashboard: http://localhost:${PORT}`);
  console.log(`====================================================`);
});
