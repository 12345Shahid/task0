const puppeteer = require('puppeteer');
const http = require('http');
const { getProfile } = require('../config/profiles');
const { getCountry } = require('../config/countries');
const { generateStealthScript } = require('../stealth/fingerprintInjector');

class BrowserSession {
  constructor() {
    this.browser = null;
    this.tabs = []; // Array of { id, page, cdpSession, title, url }
    this.activeTabId = null;
    this.page = null;
    this.cdpSession = null;
    this.profile = null;
    this.country = null;
    this.noiseSeed = Math.random();
    this.proxyConfig = {
      host: 'thehub.proxy-cheap.com:8080',
      user: 'Ltj8SfJpTEukyVn',
      basePass: '0Pa7rRMzcehfeLR'
    };
    this.proxyBytesTransferred = 0;
    this.isActive = false;
    this.isScreencasting = false;
    this.onFrameCallback = null;
    this.tabCounter = 1;
    this._isDispatchingTouch = false;
    this._pendingTouchMove = null;
  }

  /**
   * Fast geo & timezone lookup for the residential proxy exit IP
   */
  detectProxyGeo(countryCode = 'US') {
    return new Promise((resolve) => {
      const targetPass = countryCode === 'US'
        ? `${this.proxyConfig.basePass}_country-US_region-NY`
        : `${this.proxyConfig.basePass}_country-${countryCode}`;
      const auth = Buffer.from(`${this.proxyConfig.user}:${targetPass}`).toString('base64');
      const req = http.request({
        host: 'thehub.proxy-cheap.com',
        port: 8080,
        path: 'http://ip-api.com/json/?fields=status,country,countryCode,regionName,city,timezone,lat,lon,query',
        headers: {
          'Proxy-Authorization': 'Basic ' + auth,
          'User-Agent': 'curl/7.68.0'
        },
        timeout: 4500
      }, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
          try {
            const parsed = JSON.parse(data);
            if (parsed && parsed.status === 'success') {
              resolve(parsed);
              return;
            }
          } catch (e) {}
          resolve(null);
        });
      });
      req.on('error', () => resolve(null));
      req.on('timeout', () => { req.destroy(); resolve(null); });
      req.end();
    });
  }

  /**
   * Start a new anti-detect browser session
   */
  async start({ profileId = 'poco_m3', countryCode = 'US', headless = 'new' } = {}) {
    if (this.isActive && this.browser) {
      await this.close();
    }

    this.profile = getProfile(profileId);
    this.country = { ...getCountry(countryCode) };
    this.noiseSeed = Math.random();
    this.proxyBytesTransferred = 0;

    // Dynamically synchronize IP geolocation & timezone from residential proxy exit node
    try {
      const geoInfo = await this.detectProxyGeo(this.country.code);
      if (geoInfo && geoInfo.timezone) {
        console.log(`[BrowserSession] Synchronized proxy IP ${geoInfo.query} (${geoInfo.regionName || ''}, ${geoInfo.city || ''}) timezone -> ${geoInfo.timezone}`);
        this.country.timezone = geoInfo.timezone;
        if (geoInfo.lat && geoInfo.lon) {
          this.country.latitude = geoInfo.lat;
          this.country.longitude = geoInfo.lon;
        }
      }
    } catch (e) {
      console.warn('[BrowserSession] Proxy geo detection skipped:', e.message);
    }

    const proxyPass = this.country.code === 'US'
      ? `${this.proxyConfig.basePass}_country-US_region-NY`
      : `${this.proxyConfig.basePass}_country-${this.country.code}`;
    const proxyServer = `http://${this.proxyConfig.host}`;

    const launchArgs = [
      `--proxy-server=${proxyServer}`,
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-accelerated-2d-canvas',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-blink-features=AutomationControlled',
      '--force-webrtc-ip-handling-policy=disable_non_proxied_udp',
      '--enforce-webrtc-ip-permission-check',
      '--disable-webrtc-multiple-routes',
      '--disable-webrtc-hw-decoding',
      '--disable-webrtc-hw-encoding',
      `--lang=${this.country.locale || 'en-US'}`,
      `--accept-lang=${this.country.locale || 'en-US'},en;q=0.9`,
      `--window-size=${this.profile.viewport.width},${this.profile.viewport.height}`
    ];

    if (this.profile.isMobile) {
      launchArgs.push('--enable-touch-drag-drop');
    }

    this.browser = await puppeteer.launch({
      headless: headless,
      args: launchArgs,
      env: { ...process.env, TZ: this.country.timezone || 'America/New_York' },
      protocolTimeout: 120000,
      ignoreDefaultArgs: ['--enable-automation']
    });

    // Detect actual underlying engine version to eliminate version mismatch
    const rawVersion = await this.browser.version();
    const chromeVerMatch = rawVersion.match(/Chrome\/([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)/);
    this.realChromeVersion = chromeVerMatch ? chromeVerMatch[1] : '149.0.7827.22';
    this.chromeMajor = this.realChromeVersion.split('.')[0] || '149';

    this.tabs = [];
    this.tabCounter = 1;

    const initialPages = await this.browser.pages();
    const firstPage = initialPages.length > 0 ? initialPages[0] : await this.browser.newPage();
    const firstTab = await this.setupPage(firstPage, `Tab 1: ${this.profile.name}`);
    this.tabs.push(firstTab);
    this.activeTabId = firstTab.id;
    this.page = firstTab.page;
    this.cdpSession = firstTab.cdpSession;

    this.isActive = true;

    return {
      success: true,
      profile: this.profile.name,
      country: `${this.country.flag} ${this.country.name} (${this.country.code})`,
      timezone: this.country.timezone,
      viewport: this.profile.viewport,
      tabs: this.getTabsList()
    };
  }

  /**
   * Set up page with proxy auth, CDP overrides, and anti-detect script
   */
  async setupPage(page, title = 'New Tab') {
    const tabId = 'tab_' + (this.tabCounter++);

    // Proxy authentication
    const proxyPass = this.country.code === 'US'
      ? `${this.proxyConfig.basePass}_country-US_region-NY`
      : `${this.proxyConfig.basePass}_country-${this.country.code}`;
    await page.authenticate({
      username: this.proxyConfig.user,
      password: proxyPass
    });

    const cdp = await page.createCDPSession();

    // Override User-Agent & Client Hints (Aligned 1:1 with real Chromium version)
    const alignedUa = this.profile.userAgent.replace(/Chrome\/[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+/, `Chrome/${this.realChromeVersion}`);

    const cdpUaPayload = {
      userAgent: alignedUa,
      acceptLanguage: `${this.country.locale},${this.country.locale.split('-')[0]};q=0.9`,
      platform: this.profile.platform
    };

    if (this.profile.clientHints) {
      cdpUaPayload.userAgentMetadata = {
        brands: [
          { brand: 'Chromium', version: this.chromeMajor },
          { brand: 'Google Chrome', version: this.chromeMajor },
          { brand: 'Not-A.Brand', version: '24' }
        ],
        fullVersionList: [
          { brand: 'Chromium', version: this.realChromeVersion },
          { brand: 'Google Chrome', version: this.realChromeVersion },
          { brand: 'Not-A.Brand', version: '24.0.0.0' }
        ],
        mobile: this.profile.clientHints.mobile,
        model: this.profile.clientHints.model,
        platform: this.profile.clientHints.platform,
        platformVersion: this.profile.clientHints.platformVersion,
        architecture: this.profile.clientHints.architecture,
        bitness: this.profile.clientHints.bitness
      };
    }

    await cdp.send('Network.setUserAgentOverride', cdpUaPayload);

    // Track actual Residential Proxy Network Bytes
    try {
      await cdp.send('Network.enable');
      cdp.on('Network.dataReceived', (params) => {
        this.proxyBytesTransferred = (this.proxyBytesTransferred || 0) + (params.encodedDataLength || 0);
      });
    } catch(e) {}

    // Viewport & Emulation
    await page.setViewport(this.profile.viewport);

    if (this.profile.hasTouch) {
      await cdp.send('Emulation.setTouchEmulationEnabled', {
        enabled: true,
        maxTouchPoints: 5
      });
    }

    // Timezone override
    try {
      await cdp.send('Emulation.setTimezoneOverride', {
        timezoneId: this.country.timezone
      });
    } catch (e) {}

    // Inject stealth script
    const stealthScript = generateStealthScript(this.profile, this.country, this.noiseSeed, this.realChromeVersion);
    await page.evaluateOnNewDocument(stealthScript);

    const tabObj = {
      id: tabId,
      page,
      cdpSession: cdp,
      title: title,
      url: page.url()
    };

    page.on('load', async () => {
      try {
        tabObj.title = (await page.title()) || 'Page';
        tabObj.url = page.url();
      } catch (e) {}
    });

    return tabObj;
  }

  /**
   * Open a new browser tab
   */
  async newTab(url = null) {
    if (!this.isActive || !this.browser) {
      throw new Error('Session is not active');
    }

    const newPage = await this.browser.newPage();
    const tabObj = await this.setupPage(newPage, `Tab ${this.tabCounter}: ${this.profile.name}`);
    this.tabs.push(tabObj);

    if (url) {
      await newPage.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      tabObj.url = newPage.url();
      try { tabObj.title = (await newPage.title()) || tabObj.title; } catch (e) {}
    }

    await this.switchTab(tabObj.id);
    return this.getTabsList();
  }

  /**
   * Switch active tab
   */
  async switchTab(tabId) {
    const targetTab = this.tabs.find(t => t.id === tabId);
    if (!targetTab) return this.getTabsList();

    const wasScreencasting = this.isScreencasting;
    if (wasScreencasting) {
      await this.stopScreencast();
    }

    this.activeTabId = targetTab.id;
    this.page = targetTab.page;
    this.cdpSession = targetTab.cdpSession;

    try {
      await this.page.bringToFront();
    } catch (e) {}

    if (wasScreencasting && this.onFrameCallback) {
      await this.startScreencast(this.onFrameCallback);
    }

    return this.getTabsList();
  }

  /**
   * Close a tab
   */
  async closeTab(tabId) {
    if (this.tabs.length <= 1) {
      // Don't close the only remaining tab; navigate to about:blank instead
      if (this.page) {
        await this.page.goto('about:blank');
      }
      return this.getTabsList();
    }

    const tabIndex = this.tabs.findIndex(t => t.id === tabId);
    if (tabIndex === -1) return this.getTabsList();

    const tabToClose = this.tabs[tabIndex];

    if (this.activeTabId === tabId) {
      const nextIndex = tabIndex === 0 ? 1 : tabIndex - 1;
      await this.switchTab(this.tabs[nextIndex].id);
    }

    try {
      await tabToClose.page.close();
    } catch (e) {}

    this.tabs.splice(tabIndex, 1);
    return this.getTabsList();
  }

  /**
   * Get list of tabs
   */
  getTabsList() {
    return this.tabs.map(t => ({
      id: t.id,
      title: t.title || 'Tab',
      url: t.page ? t.page.url() : '',
      active: t.id === this.activeTabId
    }));
  }

  /**
   * Fast Clipboard / Direct Text Insertion via CDP
   */
  async pasteText(text) {
    if (!this.isActive || !this.cdpSession || !text) return;
    await this.cdpSession.send('Input.insertText', { text });
  }

  /**
   * Clear Identity / Wipe Cookies, Storage & Rotate Seed
   */
  async clearIdentity() {
    if (!this.isActive || !this.cdpSession) return;
    try {
      await this.cdpSession.send('Network.clearBrowserCookies');
      await this.cdpSession.send('Network.clearBrowserCache');
    } catch (e) {}

    this.noiseSeed = Math.random();

    // Re-inject stealth script with fresh seed across all open tabs
    for (const t of this.tabs) {
      try {
        const script = generateStealthScript(this.profile, this.country, this.noiseSeed, this.realChromeVersion);
        await t.page.evaluateOnNewDocument(script);
      } catch (e) {}
    }

    return {
      success: true,
      message: 'Cookies and cache wiped, identity noise seed rotated.'
    };
  }

  /**
   * Start Low-Latency Screencast via CDP
   */
  async startScreencast(onFrame) {
    if (!this.isActive || !this.cdpSession) {
      throw new Error('Session is not active');
    }
    this.onFrameCallback = onFrame;
    this.isScreencasting = true;

    this.cdpSession.on('Page.screencastFrame', async ({ data, metadata, sessionId }) => {
      try {
        await this.cdpSession.send('Page.screencastFrameAck', { sessionId });
      } catch (e) {}

      if (this.onFrameCallback) {
        this.onFrameCallback({
          data,
          metadata,
          timestamp: Date.now()
        });
      }
    });

    await this.cdpSession.send('Page.startScreencast', {
      format: 'jpeg',
      quality: 68,
      maxWidth: this.profile.viewport.width,
      maxHeight: this.profile.viewport.height,
      everyNthFrame: 2
    });
  }

  /**
   * Stop Screencast
   */
  async stopScreencast() {
    if (this.isScreencasting && this.cdpSession) {
      try {
        await this.cdpSession.send('Page.stopScreencast');
      } catch (e) {}
      this.isScreencasting = false;
    }
  }

  /**
   * Dispatch Mouse Events via CDP
   */
  async dispatchMouseEvent({ type = 'mouseMoved', x = 0, y = 0, button = 'none', clickCount = 0, deltaX = 0, deltaY = 0 } = {}) {
    if (!this.cdpSession) return;
    const cdpTypeMap = {
      'mousedown': 'mousePressed',
      'mouseup': 'mouseReleased',
      'mousemove': 'mouseMoved',
      'wheel': 'mouseWheel'
    };
    const cdpType = cdpTypeMap[type.toLowerCase()] || type;
    const params = {
      type: cdpType,
      x: Math.round(x),
      y: Math.round(y),
      button: button === 'left' ? 'left' : (button === 'right' ? 'right' : (button === 'middle' ? 'middle' : 'none')),
      clickCount: clickCount || (cdpType === 'mousePressed' || cdpType === 'mouseReleased' ? 1 : 0)
    };

    if (cdpType === 'mouseWheel') {
      params.deltaX = deltaX || 0;
      params.deltaY = deltaY || 0;
    }

    await this.cdpSession.send('Input.dispatchMouseEvent', params);
  }

  /**
   * Dispatch Touch Events via CDP with Move Coalescing to prevent queue starvation
   */
  async dispatchTouchEvent({ type = 'touchStart', touchPoints = [] } = {}) {
    if (!this.cdpSession) return;
    const cdpTypeMap = {
      'touchstart': 'touchStart',
      'touchmove': 'touchMove',
      'touchend': 'touchEnd',
      'touchcancel': 'touchCancel'
    };
    const cdpType = cdpTypeMap[type.toLowerCase()] || type;

    // For touchStart, touchEnd, touchCancel: always send immediately
    if (cdpType !== 'touchMove') {
      try {
        await this.cdpSession.send('Input.dispatchTouchEvent', {
          type: cdpType,
          touchPoints: touchPoints.map(tp => ({
            x: Math.round(tp.x),
            y: Math.round(tp.y),
            radiusX: tp.radiusX || 1,
            radiusY: tp.radiusY || 1,
            force: tp.force || 1.0,
            id: tp.id || 0
          }))
        });
      } catch (e) {}
      return;
    }

    // For touchMove: coalesce to ensure only 1 in-flight command at a time
    this._pendingTouchMove = touchPoints;
    if (this._isDispatchingTouch) return;

    this._isDispatchingTouch = true;
    while (this._pendingTouchMove && this.cdpSession) {
      const pts = this._pendingTouchMove;
      this._pendingTouchMove = null;
      try {
        await this.cdpSession.send('Input.dispatchTouchEvent', {
          type: 'touchMove',
          touchPoints: pts.map(tp => ({
            x: Math.round(tp.x),
            y: Math.round(tp.y),
            radiusX: tp.radiusX || 1,
            radiusY: tp.radiusY || 1,
            force: tp.force || 1.0,
            id: tp.id || 0
          }))
        });
      } catch (e) {}
    }
    this._isDispatchingTouch = false;
  }

  /**
   * Dispatch Key Events via CDP
   */
  async dispatchKeyEvent({ type = 'keyDown', key = '', code = '', text = '', windowsVirtualKeyCode = 0 } = {}) {
    if (!this.cdpSession) return;
    const cdpTypeMap = {
      'keydown': 'rawKeyDown',
      'keyup': 'keyUp',
      'keypress': 'char'
    };
    const cdpType = cdpTypeMap[type.toLowerCase()] || type;

    await this.cdpSession.send('Input.dispatchKeyEvent', {
      type: cdpType,
      key,
      code,
      text: (cdpType === 'rawKeyDown' || cdpType === 'keyDown') ? text : undefined,
      unmodifiedText: text,
      windowsVirtualKeyCode
    });

    if ((cdpType === 'rawKeyDown' || cdpType === 'keyDown') && text && text.length === 1) {
      await this.cdpSession.send('Input.dispatchKeyEvent', {
        type: 'char',
        text,
        unmodifiedText: text
      });
    }
  }

  /**
   * Universal Touch & Wheel Scroll Gesture
   * Instant, smooth scrolling via mouseWheel and direct DOM fallback
   */
  async scroll({ x = 200, y = 400, deltaX = 0, deltaY = 0 } = {}) {
    if (!this.cdpSession) return;
    const scrollX = Math.round(x || (this.profile && this.profile.viewport ? this.profile.viewport.width / 2 : 200));
    const scrollY = Math.round(y || (this.profile && this.profile.viewport ? this.profile.viewport.height / 2 : 400));
    const dX = Math.round(deltaX || 0);
    const dY = Math.round(deltaY || 0);

    try {
      await this.cdpSession.send('Input.dispatchMouseEvent', {
        type: 'mouseWheel',
        x: scrollX,
        y: scrollY,
        deltaX: dX,
        deltaY: dY
      });
    } catch (err) {
      // Direct DOM scroll fallback
      try {
        if (this.page) {
          await this.page.evaluate((sx, sy) => {
            window.scrollBy({ left: sx, top: sy, behavior: 'instant' });
          }, dX, dY);
        }
      } catch (e) {}
    }
  }

  /**
   * Navigate to a URL
   */
  async navigate(url, { waitUntil = 'domcontentloaded', timeout = 30000 } = {}) {
    if (!this.isActive || !this.page) {
      throw new Error('Browser session is not active');
    }
    const response = await this.page.goto(url, { waitUntil, timeout });
    const currentTab = this.tabs.find(t => t.id === this.activeTabId);
    if (currentTab) {
      currentTab.url = this.page.url();
      try { currentTab.title = (await this.page.title()) || currentTab.title; } catch (e) {}
    }
    return {
      url: this.page.url(),
      status: response ? response.status() : 200,
      title: await this.page.title(),
      tabs: this.getTabsList()
    };
  }

  /**
   * Reload current page
   */
  async reload() {
    if (!this.isActive || !this.page) return;
    await this.page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  }

  /**
   * History Navigation
   */
  async goBack() {
    if (!this.isActive || !this.page) return;
    await this.page.goBack({ waitUntil: 'domcontentloaded', timeout: 15000 });
  }

  async goForward() {
    if (!this.isActive || !this.page) return;
    await this.page.goForward({ waitUntil: 'domcontentloaded', timeout: 15000 });
  }

  /**
   * Get current public IP & Geo location
   */
  async getPublicIpInfo() {
    if (!this.isActive || !this.page) {
      throw new Error('Browser session is not active');
    }
    await this.page.goto('https://api.ipify.org?format=json', { waitUntil: 'domcontentloaded', timeout: 15000 });
    const content = await this.page.evaluate(() => document.body.innerText);
    try {
      return JSON.parse(content);
    } catch (e) {
      return { raw: content };
    }
  }

  /**
   * Capture a base64 or buffer screenshot
   */
  async takeScreenshot({ encoding = 'base64', fullPage = false } = {}) {
    if (!this.isActive || !this.page) {
      throw new Error('Browser session is not active');
    }
    return await this.page.screenshot({ encoding, fullPage, type: 'jpeg', quality: 85 });
  }

  /**
   * Run arbitrary JS inside the session
   */
  async evaluate(fnOrStr) {
    if (!this.isActive || !this.page) {
      throw new Error('Browser session is not active');
    }
    return await this.page.evaluate(fnOrStr);
  }

  /**
   * Close and destroy the browser session
   */
  async close() {
    await this.stopScreencast();
    this.isActive = false;
    this.tabs = [];
    if (this.browser) {
      try {
        await this.browser.close();
      } catch (e) {}
      this.browser = null;
      this.page = null;
      this.cdpSession = null;
    }
  }
}

module.exports = BrowserSession;
