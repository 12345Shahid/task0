const puppeteer = require('puppeteer');
const { getProfile } = require('../config/profiles');
const { getCountry } = require('../config/countries');
const { generateStealthScript } = require('../stealth/fingerprintInjector');

class BrowserSession {
  constructor() {
    this.browser = null;
    this.page = null;
    this.cdpSession = null;
    this.profile = null;
    this.country = null;
    this.proxyConfig = {
      host: 'thehub.proxy-cheap.com:8080',
      user: 'Ltj8SfJpTEukyVn',
      basePass: '0Pa7rRMzcehfeLR'
    };
    this.isActive = false;
  }

  /**
   * Start a new anti-detect browser session
   */
  async start({ profileId = 'poco_m3', countryCode = 'US', headless = 'new' } = {}) {
    if (this.isActive && this.browser) {
      await this.close();
    }

    this.profile = getProfile(profileId);
    this.country = getCountry(countryCode);

    const proxyPass = `${this.proxyConfig.basePass}_country-${this.country.code}`;
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
      `--window-size=${this.profile.viewport.width},${this.profile.viewport.height}`
    ];

    if (this.profile.isMobile) {
      launchArgs.push('--enable-touch-drag-drop');
    }

    this.browser = await puppeteer.launch({
      headless: headless,
      args: launchArgs,
      ignoreDefaultArgs: ['--enable-automation']
    });

    const pages = await this.browser.pages();
    this.page = pages.length > 0 ? pages[0] : await this.browser.newPage();

    // Authenticate with Proxy-Cheap
    await this.page.authenticate({
      username: this.proxyConfig.user,
      password: proxyPass
    });

    // CDP Session for deep low-level overrides
    this.cdpSession = await this.page.createCDPSession();

    // Override User-Agent & Client Hints via CDP
    const cdpUaPayload = {
      userAgent: this.profile.userAgent,
      acceptLanguage: `${this.country.locale},${this.country.locale.split('-')[0]};q=0.9`,
      platform: this.profile.platform
    };

    if (this.profile.clientHints) {
      cdpUaPayload.userAgentMetadata = {
        brands: this.profile.clientHints.brands,
        fullVersionList: [
          { brand: 'Chromium', version: '124.0.6367.113' },
          { brand: 'Google Chrome', version: '124.0.6367.113' },
          { brand: 'Not-A.Brand', version: '99.0.0.0' }
        ],
        mobile: this.profile.clientHints.mobile,
        model: this.profile.clientHints.model,
        platform: this.profile.clientHints.platform,
        platformVersion: this.profile.clientHints.platformVersion,
        architecture: this.profile.clientHints.architecture,
        bitness: this.profile.clientHints.bitness
      };
    }

    await this.cdpSession.send('Network.setUserAgentOverride', cdpUaPayload);

    // Set Viewport & Emulation
    await this.page.setViewport(this.profile.viewport);

    if (this.profile.hasTouch) {
      await this.cdpSession.send('Emulation.setTouchEmulationEnabled', {
        enabled: true,
        maxTouchPoints: 5
      });
    }

    // Set Timezone override via CDP
    try {
      await this.cdpSession.send('Emulation.setTimezoneOverride', {
        timezoneId: this.country.timezone
      });
    } catch (e) {
      console.warn('CDP timezone override fallback:', e.message);
    }

    // Inject our Battle-Tested Anti-Detect Script before any document evaluates
    const stealthScript = generateStealthScript(this.profile, this.country);
    await this.page.evaluateOnNewDocument(stealthScript);

    this.isActive = true;

    return {
      success: true,
      profile: this.profile.name,
      country: `${this.country.flag} ${this.country.name} (${this.country.code})`,
      timezone: this.country.timezone
    };
  }

  /**
   * Navigate to a URL
   */
  async navigate(url, { waitUntil = 'networkidle2', timeout = 30000 } = {}) {
    if (!this.isActive || !this.page) {
      throw new Error('Browser session is not active');
    }
    const response = await this.page.goto(url, { waitUntil, timeout });
    return {
      url: this.page.url(),
      status: response ? response.status() : 200,
      title: await this.page.title()
    };
  }

  /**
   * Get current public IP & Geo location as seen by external services
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
    this.isActive = false;
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
