const { spawn } = require('child_process');
const WebSocket = require('ws');
const path = require('path');

async function testPhase4MultiUser() {
  console.log('====================================================');
  console.log('🧪 Starting Phase 4 Multi-User Sandbox & Cloud Test');
  console.log('====================================================\n');

  // 1. Launch server on port 4003
  const serverPath = path.join(__dirname, '../server.js');
  const serverProcess = spawn('node', [serverPath], {
    env: { ...process.env, PORT: '4003' },
    stdio: 'pipe'
  });

  await new Promise(r => setTimeout(r, 1500));

  try {
    // 2. Test /health endpoint
    console.log('👉 [STEP 1] Testing /health monitoring endpoint...');
    const healthRes = await fetch('http://localhost:4003/health');
    const healthData = await healthRes.json();
    console.log('   ✅ Healthcheck response:', healthData.status, `(Uptime: ${healthData.uptime}s, Active Sessions: ${healthData.activeSessions})`);
    if (healthData.status !== 'healthy') {
      throw new Error('/health status is not healthy');
    }

    // 3. Spawn Session A (Poco M3, US Proxy)
    console.log('\n👉 [STEP 2] Spawning Sandbox Session A (Poco M3 • 🇺🇸 US)...');
    const resA = await fetch('http://localhost:4003/api/sessions/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileId: 'poco_m3', countryCode: 'US' })
    });
    const dataA = await resA.json();
    const sessionIdA = dataA.sessionId;
    console.log('   ✅ Session A spawned:', sessionIdA);

    // 4. Spawn Session B (Galaxy S24 Ultra, GB Proxy)
    console.log('\n👉 [STEP 3] Spawning Sandbox Session B (Galaxy S24 Ultra • 🇬🇧 GB)...');
    const resB = await fetch('http://localhost:4003/api/sessions/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileId: 'samsung_s24_ultra', countryCode: 'GB' })
    });
    const dataB = await resB.json();
    const sessionIdB = dataB.sessionId;
    console.log('   ✅ Session B spawned:', sessionIdB);

    // 5. Verify isolated session listing
    console.log('\n👉 [STEP 4] Verifying concurrent session isolation in GET /api/sessions...');
    const listRes = await fetch('http://localhost:4003/api/sessions');
    const listData = await listRes.json();
    console.log(`   ✅ Active sessions running concurrently: ${listData.sessions.length}`);
    for (const s of listData.sessions) {
      console.log(`      • [${s.id}] Profile: ${s.profile} | Geo: ${s.country}`);
    }
    if (listData.sessions.length !== 2) {
      throw new Error('Expected 2 concurrent active sessions');
    }

    // 6. Connect WebSocket client specifically to Session A
    console.log('\n👉 [STEP 5] Connecting WebSocket to Session A stream (/stream?sessionId=' + sessionIdA + ')...');
    const wsA = new WebSocket(`ws://localhost:4003/stream?sessionId=${sessionIdA}`);

    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Timeout on Session A stream')), 15000);

      wsA.on('open', () => {
        console.log('   ✅ WebSocket stream connected to Session A');
        wsA.send(JSON.stringify({ type: 'ping', time: Date.now() }));
      });

      wsA.on('message', (d) => {
        const msg = JSON.parse(d.toString());
        if (msg.type === 'init') {
          console.log(`   ✅ Received init for Session A: ${msg.profile} (${msg.country})`);
        }
        if (msg.type === 'frame') {
          console.log(`   🎬 Screencast frame arrived from Session A (${msg.data.length} bytes)`);
          clearTimeout(timeout);
          resolve();
        }
      });

      wsA.on('error', reject);
    });

    wsA.close();

    // 7. Terminate Session A and verify Session B remains intact
    console.log('\n👉 [STEP 6] Destroying Session A & verifying Session B stability...');
    await fetch(`http://localhost:4003/api/sessions/${sessionIdA}/stop`, { method: 'POST' });
    const verifyRes = await fetch('http://localhost:4003/api/sessions');
    const verifyData = await verifyRes.json();
    console.log(`   ✅ Remaining active sessions: ${verifyData.sessions.length}`);
    if (verifyData.sessions.length !== 1 || verifyData.sessions[0].id !== sessionIdB) {
      throw new Error('Session B state corrupted after Session A termination');
    }
    console.log(`   ✅ Session B (${sessionIdB}) running unharmed!`);

    // Clean up Session B
    await fetch(`http://localhost:4003/api/sessions/${sessionIdB}/stop`, { method: 'POST' });

    console.log('\n====================================================');
    console.log('🎉 PHASE 4 VERIFICATION COMPLETE: MULTI-USER SANDBOX FULLY OPERATIONAL!');
    console.log('====================================================');

  } finally {
    serverProcess.kill('SIGINT');
  }
}

testPhase4MultiUser().catch(e => {
  console.error('\n❌ Phase 4 test failed:', e);
  process.exit(1);
});
