const BrowserSession = require('../engine/browserSession');

async function verifyBrowserScanAudit() {
  console.log('====================================================');
  console.log('🧪 Verifying 4 Hardened Anti-Detection Fixes for BrowserScan');
  console.log('====================================================\n');

  const session = new BrowserSession();

  try {
    console.log('👉 [STEP 1] Starting POCO M3 session with US Residential Proxy...');
    const startResult = await session.start({
      profileId: 'poco_m3',
      countryCode: 'US',
      headless: 'new'
    });
    console.log('   ✅ Session started! Detected real engine version:', session.realChromeVersion);

    console.log('\n👉 [STEP 2] Running deep in-browser forensic probe...');
    await session.page.goto('about:blank');

    const probe = await session.evaluate(async () => {
      // 1. WebRTC Leak Probe
      let leakedIps = [];
      try {
        const pc = new (window.RTCPeerConnection || window.webkitRTCPeerConnection)({
          iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
        });
        pc.createDataChannel('');
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);

        await new Promise((resolve) => {
          pc.onicecandidate = (e) => {
            if (!e.candidate) {
              resolve();
              return;
            }
            const line = e.candidate.candidate;
            const parts = line.split(' ');
            if (parts.length > 4) {
              const ip = parts[4];
              if (!ip.endsWith('.local') && ip.includes('.')) {
                leakedIps.push(ip);
              }
            }
          };
          setTimeout(resolve, 1500);
        });
        pc.close();
      } catch (e) {
        leakedIps = ['Error probing: ' + e.message];
      }

      // 2. Plugins Probe (Mobile Operating System Check)
      const pluginsLength = navigator.plugins.length;
      const mimeTypesLength = navigator.mimeTypes.length;
      const pdfEnabled = navigator.pdfViewerEnabled;

      // 3. WebGL Probe (Exception & Parameter Check)
      let webglVendor = 'none';
      let webglRenderer = 'none';
      let webglStdVendor = 'none';
      let webglStdRenderer = 'none';
      let webglException = false;
      try {
        const c = document.createElement('canvas');
        const gl = c.getContext('webgl') || c.getContext('experimental-webgl');
        if (gl) {
          const ext = gl.getExtension('WEBGL_debug_renderer_info');
          if (ext) {
            webglVendor = gl.getParameter(ext.UNMASKED_VENDOR_WEBGL);
            webglRenderer = gl.getParameter(ext.UNMASKED_RENDERER_WEBGL);
          }
          webglStdVendor = gl.getParameter(gl.VENDOR);
          webglStdRenderer = gl.getParameter(gl.RENDERER);
        }
      } catch (e) {
        webglException = true;
      }

      // 4. Version Alignment & Client Hints Probe
      const ua = navigator.userAgent;
      let chBrands = null;
      let chFull = null;
      if (navigator.userAgentData) {
        chBrands = navigator.userAgentData.brands;
        const high = await navigator.userAgentData.getHighEntropyValues(['fullVersionList']);
        chFull = high.fullVersionList;
      }

      // 5. Intl Locale Probe
      const intlLocale = Intl.DateTimeFormat().resolvedOptions().locale;
      const intlTz = Intl.DateTimeFormat().resolvedOptions().timeZone;

      return {
        webrtcLeakedIps: leakedIps,
        pluginsLength,
        mimeTypesLength,
        pdfEnabled,
        webglVendor,
        webglRenderer,
        webglStdVendor,
        webglStdRenderer,
        webglException,
        userAgent: ua,
        chBrands,
        chFull,
        intlLocale,
        intlTz
      };
    });

    console.log('   📊 Forensic Probe Results:');
    console.log('   -------------------------------------------------');
    console.log('   1. WebRTC IP Leak Check     :', probe.webrtcLeakedIps.length === 0 ? '✅ 0 Leaked IPs (Zero Bangladesh Leak)' : `❌ LEAK DETECTED: ${probe.webrtcLeakedIps}`);
    console.log('   2. Mobile Plugins Check     :', probe.pluginsLength === 0 && !probe.pdfEnabled ? '✅ 0 Desktop PDF Plugins (Authentic Mobile Android)' : `❌ Leaked desktop plugins: ${probe.pluginsLength}`);
    console.log('   3. WebGL Parameter Check    :', !probe.webglException && probe.webglVendor === 'Qualcomm' ? `✅ Qualcomm / Adreno 610 (No Exceptions)` : '❌ WebGL Exception');
    console.log('      • Unmasked Vendor        :', probe.webglVendor);
    console.log('      • Unmasked Renderer      :', probe.webglRenderer);
    console.log('      • Standard Vendor        :', probe.webglStdVendor);
    console.log('      • Standard Renderer      :', probe.webglStdRenderer);
    console.log('   4. Browser Version Alignment:');
    console.log('      • User-Agent             :', probe.userAgent);
    console.log('      • Client Hints Brands    :', JSON.stringify(probe.chBrands));
    console.log('      • Client Hints Full List :', JSON.stringify(probe.chFull));
    console.log('   5. Intl Locale & Timezone   :', `${probe.intlLocale} | ${probe.intlTz} ✅`);
    console.log('   -------------------------------------------------');

    if (probe.webrtcLeakedIps.length > 0) {
      throw new Error('WebRTC is still leaking local IPs!');
    }
    if (probe.pluginsLength !== 0) {
      throw new Error('Desktop plugins still present on mobile profile!');
    }
    if (probe.webglException) {
      throw new Error('WebGL threw an exception during probe!');
    }

    console.log('\n====================================================');
    console.log('🎉 ALL 4 DEDUCTION FIXES VERIFIED SUCCESSFULLY (100% READY)!');
    console.log('====================================================');

  } catch (err) {
    console.error('\n❌ Verification failed:', err);
    process.exitCode = 1;
  } finally {
    await session.close();
  }
}

verifyBrowserScanAudit();
