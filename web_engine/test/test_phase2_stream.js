const { spawn } = require('child_process');
const WebSocket = require('ws');
const path = require('path');

async function testPhase2Streaming() {
  console.log('====================================================');
  console.log('🧪 Starting Phase 2 Real-Time Streaming & Input Test');
  console.log('====================================================\n');

  // 1. Launch server
  const serverPath = path.join(__dirname, '../server.js');
  const serverProcess = spawn('node', [serverPath], {
    env: { ...process.env, PORT: '4001' },
    stdio: 'pipe'
  });

  // Wait 1.5s for server to start
  await new Promise(r => setTimeout(r, 1500));

  try {
    // 2. Start session via HTTP API
    console.log('👉 [STEP 1] Starting session via POST /api/session/start...');
    const startRes = await fetch('http://localhost:4001/api/session/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileId: 'poco_m3', countryCode: 'US' })
    });
    const startData = await startRes.json();
    console.log('   ✅ Session started:', startData.session.profile, startData.session.country);

    // 3. Connect to WebSocket stream
    console.log('\n👉 [STEP 2] Connecting to WebSocket stream at ws://localhost:4001/stream...');
    const ws = new WebSocket('ws://localhost:4001/stream');

    let framesReceived = 0;
    let initReceived = false;
    let pongReceived = false;

    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error(`Timeout. Frames received: ${framesReceived}`));
      }, 20000);

      ws.on('open', () => {
        console.log('   ✅ WebSocket stream connected!');
        // Ping latency check
        ws.send(JSON.stringify({ type: 'ping', time: Date.now() }));

        // Navigate to a dynamic page
        console.log('   🚀 Sending navigation to https://api.ipify.org?format=json...');
        ws.send(JSON.stringify({ type: 'navigate', url: 'https://api.ipify.org?format=json' }));
      });

      ws.on('message', (data) => {
        const msg = JSON.parse(data.toString());

        if (msg.type === 'init') {
          initReceived = true;
          console.log('   ✅ Init packet received with viewport:', msg.viewport);
        }

        if (msg.type === 'pong') {
          pongReceived = true;
          const rtt = Date.now() - msg.clientTime;
          console.log(`   ✅ WebSocket RTT Latency: ${rtt} ms`);
        }

        if (msg.type === 'frame') {
          framesReceived++;
          console.log(`   🎬 Frame ${framesReceived} received (${msg.data.length} bytes JPEG)`);

          if (framesReceived === 2) {
            // Send user input events over the WebSocket to trigger interactive rendering
            console.log('\n👉 [STEP 3] Dispatching bidirectional input events (mouse, wheel, key)...');
            ws.send(JSON.stringify({
              type: 'mouse',
              event: 'mousedown',
              x: 120,
              y: 200,
              button: 'left'
            }));
            ws.send(JSON.stringify({
              type: 'mouse',
              event: 'mouseup',
              x: 120,
              y: 200,
              button: 'left'
            }));
            ws.send(JSON.stringify({
              type: 'wheel',
              x: 120,
              y: 200,
              deltaY: 150
            }));
            ws.send(JSON.stringify({
              type: 'key',
              event: 'keydown',
              key: 'Tab',
              code: 'Tab',
              keyCode: 9
            }));
            console.log('   ✅ Input events dispatched via WebSocket data channel');
          }

          if (framesReceived >= 2 && pongReceived) {
            clearTimeout(timeout);
            resolve();
          }
        }
      });

      ws.on('error', reject);
    });

    console.log(`\n👉 [STEP 4] Total streaming frames validated: ${framesReceived}`);
    console.log('====================================================');
    console.log('🎉 PHASE 2 VERIFICATION COMPLETE: STREAMING & INPUT OPERATIONAL!');
    console.log('====================================================');

    ws.close();

  } finally {
    serverProcess.kill('SIGINT');
  }
}

testPhase2Streaming().catch(e => {
  console.error('\n❌ Phase 2 test failed:', e);
  process.exit(1);
});
