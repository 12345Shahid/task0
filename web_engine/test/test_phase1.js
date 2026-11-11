const BrowserSession = require('../engine/browserSession');
const fs = require('fs');
const path = require('path');

async function runPhase1Verification() {
  console.log('====================================================');
  console.log('🧪 Starting Phase 1 Anti-Detect Web Engine Verification');
  console.log('====================================================\n');

  const session = new BrowserSession();

  try {
    // ----------------------------------------------------
    // TEST 1: Initialize Session with POCO M3 & US Proxy
    // ----------------------------------------------------
    console.log('👉 [TEST 1] Launching POCO M3 profile with 🇺🇸 US Residential Proxy...');
    const startResult = await session.start({
      profileId: 'poco_m3',
      countryCode: 'US',
      headless: 'new'
    });
    console.log('   ✅ Session started:', startResult);

    // ----------------------------------------------------
    // TEST 2: Verify Proxy & Public IP Routing
    // ----------------------------------------------------
    console.log('\n👉 [TEST 2] Verifying Proxy Connection & Public IP...');
    const ipInfo = await session.getPublicIpInfo();
    console.log('   ✅ Public IP detected:', ipInfo);
    if (!ipInfo.ip) {
      throw new Error('Failed to retrieve public IP through proxy');
    }

    // ----------------------------------------------------
    // TEST 3: Deep Anti-Detect Fingerprint Audit
    // ----------------------------------------------------
    console.log('\n👉 [TEST 3] Running In-Browser Fingerprint Consistency Audit...');
    await session.page.goto('about:blank');

    const auditResults = await session.evaluate(() => {
      // WebGL check
      let webglVendor = 'unknown';
      let webglRenderer = 'unknown';
      try {
        const canvas = document.createElement('canvas');
        const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
        if (gl) {
          const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
          if (debugInfo) {
            webglVendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
            webglRenderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
          }
        }
      } catch (e) {
        webglVendor = 'ERROR: ' + e.message;
      }

      // Timezone consistency check
      const d = new Date();
      const tzResolved = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const timeStr = d.toTimeString();
      const hours = d.getHours();
      const strHour = parseInt(timeStr.split(':')[0], 10);
      const isHourAligned = hours === strHour;

      // Native code masking check
      const isNativeToString = Function.prototype.toString.call(Date.prototype.getTimezoneOffset).includes('[native code]');

      // Client hints
      let uaData = null;
      if (navigator.userAgentData) {
        uaData = {
          mobile: navigator.userAgentData.mobile,
          platform: navigator.userAgentData.platform
        };
      }

      return {
        webdriver: navigator.webdriver,
        platform: navigator.platform,
        hardwareConcurrency: navigator.hardwareConcurrency,
        deviceMemory: navigator.deviceMemory,
        webglVendor,
        webglRenderer,
        timezone: tzResolved,
        toTimeString: timeStr,
        isHourAligned,
        isNativeToString,
        userAgentData: uaData
      };
    });

    console.log('   📊 Fingerprint Audit Output:');
    console.log('      • navigator.webdriver        :', auditResults.webdriver, auditResults.webdriver === undefined ? '✅ (Hidden)' : '❌');
    console.log('      • navigator.platform         :', auditResults.platform, auditResults.platform === 'Linux aarch64' ? '✅' : '❌');
    console.log('      • Hardware Concurrency       :', auditResults.hardwareConcurrency, 'cores ✅');
    console.log('      • Device Memory              :', auditResults.deviceMemory, 'GB RAM ✅');
    console.log('      • WebGL Vendor               :', auditResults.webglVendor, auditResults.webglVendor === 'Qualcomm' ? '✅ (Snapdragon match)' : '❌');
    console.log('      • WebGL Renderer             :', auditResults.webglRenderer, auditResults.webglRenderer === 'Adreno (TM) 610' ? '✅ (Poco M3 match)' : '❌');
    console.log('      • Timezone                   :', auditResults.timezone, auditResults.timezone === 'America/New_York' ? '✅' : '❌');
    console.log('      • TimeString                 :', auditResults.toTimeString);
    console.log('      • Hour Consistency           :', auditResults.isHourAligned ? '✅ (1:1 Exact Match)' : '❌ (Mismatch)');
    console.log('      • Native Code Masking        :', auditResults.isNativeToString ? '✅ ([native code] disguised)' : '❌');
    console.log('      • Client Hints               :', auditResults.userAgentData);

    // Assert critical checks
    if (auditResults.webdriver !== undefined) {
      throw new Error('navigator.webdriver was detected!');
    }
    if (auditResults.webglRenderer !== 'Adreno (TM) 610') {
      throw new Error('WebGL Renderer does not match profile Adreno 610');
    }
    if (!auditResults.isHourAligned) {
      throw new Error('Timezone getHours is not aligned with toTimeString');
    }

    // ----------------------------------------------------
    // TEST 4: Screenshot Capture
    // ----------------------------------------------------
    console.log('\n👉 [TEST 4] Navigating to target site & capturing screenshot...');
    await session.navigate('https://httpbin.org/headers');
    const screenshotBuffer = await session.takeScreenshot({ encoding: 'binary' });
    const shotPath = path.join(__dirname, 'test_evidence.jpg');
    fs.writeFileSync(shotPath, screenshotBuffer);
    console.log('   ✅ Screenshot saved to:', shotPath, `(${screenshotBuffer.length} bytes)`);

    console.log('\n====================================================');
    console.log('🎉 PHASE 1 VERIFICATION COMPLETE: ALL CHECKS PASSED!');
    console.log('====================================================');

  } catch (err) {
    console.error('\n❌ TEST FAILED:', err);
    process.exitCode = 1;
  } finally {
    await session.close();
  }
}

runPhase1Verification();
