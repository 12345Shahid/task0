import puppeteer from "puppeteer-extra";
import StealthPlugin from "puppeteer-extra-plugin-stealth";
import { Browser, Page } from "puppeteer";
import { DeviceProfile } from "./deviceProfilePool.js";
import { ProxyConfig } from "./proxyManager.js";

// Register the stealth plugin to strip webdriver and standard automation leaks
puppeteer.use(StealthPlugin());

export class BrowserManager {
  /**
   * Spawns an anti-detect browser instance with authentic hardware fingerprinting
   */
  public static async launchStealthBrowser(
    profile: DeviceProfile,
    proxy: ProxyConfig,
    headless: boolean = false
  ): Promise<{ browser: Browser; page: Page }> {
    const chromeArgs = [
      `--proxy-server=http://${proxy.host}:${proxy.port}`,
      `--window-size=${profile.viewport.width},${profile.viewport.height}`,
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-dev-shm-usage",
      "--disable-accelerated-2d-canvas",
      "--no-first-run",
      "--no-zygote",
      "--disable-blink-features=AutomationControlled",
      "--disable-features=IsolateOrigins,site-per-process",
      "--lang=en-US,en",
      "--enable-features=NetworkService,NetworkServiceInProcess",
    ];

    // Check if running on Linux with Xvfb or Chrome installed at specific path
    const executablePath = process.env.CHROME_BIN || undefined;

    const browser = await puppeteer.launch({
      headless: headless ? "shell" : false,
      executablePath,
      args: chromeArgs,
      ignoreDefaultArgs: ["--enable-automation"],
      defaultViewport: null,
    });

    const page = await browser.newPage();

    // Authenticate with the US Residential Proxy
    await page.authenticate({
      username: proxy.username,
      password: proxy.password,
    });

    // Set viewport matching the selected physical device
    await page.setViewport(profile.viewport);

    // Set authentic User-Agent
    await page.setUserAgent(profile.userAgent);

    // Apply Timezone and Locale emulation
    await page.emulateTimezone(profile.timezone || "America/Los_Angeles");

    // Inject low-level stealth overrides before any script runs
    await page.evaluateOnNewDocument(
      (prof) => {
        // 1. Remove automation / webdriver signatures completely
        Object.defineProperty(navigator, "webdriver", {
          get: () => undefined,
        });

        // 2. Hardware specs
        Object.defineProperty(navigator, "hardwareConcurrency", {
          get: () => prof.hardwareConcurrency,
        });
        Object.defineProperty(navigator, "deviceMemory", {
          get: () => prof.deviceMemory,
        });
        Object.defineProperty(navigator, "languages", {
          get: () => ["en-US", "en"],
        });

        // 3. WebGL Spoofing (Prevent SwiftShader detection)
        const getParameter = WebGLRenderingContext.prototype.getParameter;
        WebGLRenderingContext.prototype.getParameter = function (param: number) {
          if (param === 37445) return prof.webgl.vendor; // UNMASKED_VENDOR_WEBGL
          if (param === 37446) return prof.webgl.renderer; // UNMASKED_RENDERER_WEBGL
          return getParameter.apply(this, [param]);
        };

        const getParameter2 = WebGL2RenderingContext.prototype.getParameter;
        WebGL2RenderingContext.prototype.getParameter = function (param: number) {
          if (param === 37445) return prof.webgl.vendor;
          if (param === 37446) return prof.webgl.renderer;
          return getParameter2.apply(this, [param]);
        };

        // 4. Subtle Canvas Noise Injection (Prevents cross-session fingerprint correlation)
        const noise = Math.random() * 0.0001;
        const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
        HTMLCanvasElement.prototype.toDataURL = function (type) {
          const ctx = this.getContext("2d");
          if (ctx) {
            try {
              const imgData = ctx.getImageData(0, 0, Math.min(2, this.width), Math.min(2, this.height));
              if (imgData.data.length > 0) {
                imgData.data[0] = Math.min(255, Math.max(0, imgData.data[0] + (noise > 0.00005 ? 1 : 0)));
                ctx.putImageData(imgData, 0, 0);
              }
            } catch (ignored) {}
          }
          return originalToDataURL.apply(this, arguments as any);
        };
      },
      profile
    );

    return { browser, page };
  }
}
