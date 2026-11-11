const { spawn } = require('child_process');
const WebSocket = require('ws');
const path = require('path');

async function testPhase3Dashboard() {
  console.log('====================================================');
  console.log('🧪 Starting Phase 3 Web Dashboard & Usability Test');
  console.log('====================================================\n');

  // 1. Launch server on port 4002
  const serverPath = path.join(__dirname, '../server.js');
  const serverProcess = spawn('node', [serverPath], {
    env: { ...process.env, PORT: '4002' },
    stdio: 'pipe'
  });

  await new Promise(r => setTimeout(r, 1500));

  try {
    // 2. Start session via HTTP API
    console.log('👉 [STEP 1] Starting session via POST /api/session/start...');
    const startRes = await fetch('http://localhost:4002/api/session/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ profileId: 'poco_m3', countryCode: 'US' })
    });
    const startData = await startRes.json();
    console.log('   ✅ Session started:', startData.session.profile, startData.session.country);

    // 3. Connect to WebSocket stream
    console.log('\n👉 [STEP 2] Connecting to WebSocket stream at ws://localhost:4002/stream...');
    const ws = new WebSocket('ws://localhost:4002/stream');

    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Timeout during dashboard test'));
      }, 25000);

      let step = 0;

      ws.on('open', () => {
        console.log('   ✅ WebSocket stream connected!');
      });

      ws.on('message', async (data) => {
        const msg = JSON.parse(data.toString());

        if (msg.type === 'init') {
          console.log('   ✅ Initial state received with tabs:', msg.tabs.length);
          // Step 1: Open second tab
          console.log('\n👉 [STEP 3] Testing Multi-Tab Management: Opening Tab 2...');
          ws.send(JSON.stringify({ type: 'new_tab', url: 'https://api.ipify.org?format=json' }));
          step = 1;
        }

        if (msg.type === 'tabs_updated') {
          if (step === 1 && msg.tabs.length === 2) {
            console.log(`   ✅ Tab 2 opened! Total open tabs: ${msg.tabs.length}`);
            console.log('   🔄 Switching active tab back to Tab 1...');
            ws.send(JSON.stringify({ type: 'switch_tab', tabId: msg.tabs[0].id }));
            step = 2;
          } else if (step === 2) {
            console.log('   ✅ Switched to Tab 1. Closing Tab 2...');
            const tab2 = msg.tabs[1] || msg.tabs.find(t => !t.active);
            if (tab2) {
              ws.send(JSON.stringify({ type: 'close_tab', tabId: tab2.id }));
              step = 3;
            }
          } else if (step === 3 && msg.tabs.length === 1) {
            console.log(`   ✅ Tab 2 closed cleanly! Remaining tabs: ${msg.tabs.length}`);

            // Step 4: Test Fast Text Paste
            console.log('\n👉 [STEP 4] Testing Fast Text Insertion via CDP...');
            ws.send(JSON.stringify({
              type: 'paste_text',
              text: 'https://cpajunkie.trcpa.link/click?camp=2383&pubid=9309'
            }));
            console.log('   ✅ Affiliate link text dispatched to remote focus');

            // Step 5: Test Identity & Cookie Wipe
            console.log('\n👉 [STEP 5] Testing One-Click Identity & Cookie Wipe...');
            ws.send(JSON.stringify({ type: 'clear_identity' }));
            step = 4;
          }
        }

        if (msg.type === 'notification' && step === 4) {
          console.log('   ✅ Confirmation received:', msg.message);
          clearTimeout(timeout);
          resolve();
        }
      });

      ws.on('error', reject);
    });

    console.log('\n====================================================');
    console.log('🎉 PHASE 3 VERIFICATION COMPLETE: ALL DASHBOARD FEATURES WORKING!');
    console.log('====================================================');

    ws.close();

  } finally {
    serverProcess.kill('SIGINT');
  }
}

testPhase3Dashboard().catch(e => {
  console.error('\n❌ Phase 3 test failed:', e);
  process.exit(1);
});
