import { Page } from "puppeteer";
import { KinematicsEngine } from "./kinematicsEngine.js";
import { DeviceProfile } from "../core/deviceProfilePool.js";

export interface TrafficSource {
  name: string;
  referrer: string;
  utmParams: string;
}

export const TRAFFIC_SOURCES: TrafficSource[] = [
  {
    name: "Twitter / X",
    referrer: "https://t.co/",
    utmParams: "utm_source=twitter&utm_medium=social&utm_campaign=crypto_yield",
  },
  {
    name: "Reddit Community",
    referrer: "https://www.reddit.com/r/defi/",
    utmParams: "utm_source=reddit&utm_medium=community&utm_campaign=staking_calculator",
  },
  {
    name: "Substack Newsletter",
    referrer: "https://substack.com/",
    utmParams: "utm_source=newsletter&utm_medium=email&utm_campaign=fin_insights",
  },
  {
    name: "DuckDuckGo Organic",
    referrer: "https://duckduckgo.com/",
    utmParams: "utm_source=duckduckgo&utm_medium=organic",
  },
  {
    name: "Bing Organic Search",
    referrer: "https://www.bing.com/",
    utmParams: "utm_source=bing&utm_medium=organic",
  },
  {
    name: "Direct / Bookmarks",
    referrer: "",
    utmParams: "",
  },
];

export class TrafficSimulationWorker {
  private page: Page;
  private profile: DeviceProfile;
  private kinematics: KinematicsEngine;
  private baseUrl: string;

  constructor(page: Page, profile: DeviceProfile, targetUrl: string) {
    this.page = page;
    this.profile = profile;
    this.kinematics = new KinematicsEngine(page);
    this.baseUrl = targetUrl;
  }

  /**
   * Discovers bounding coordinates of active In-Page Push or Banner creatives
   */
  private async findAdCreativeTarget(): Promise<{ x: number; y: number; width: number; height: number; type: string } | null> {
    try {
      const candidates = await this.page.evaluate(() => {
        const found: Array<{ x: number; y: number; width: number; height: number; type: string }> = [];

        // 1. Search for In-Page Push notifications (floating widgets with high z-index)
        const allFloating = Array.from(document.querySelectorAll("div, aside, section, a, iframe"));
        for (const el of allFloating) {
          if (el.closest("#modal-overlay") || el.closest("header") || el.closest("footer")) {
            continue;
          }
          const style = window.getComputedStyle(el);
          const zIndex = parseInt(style.zIndex, 10);
          const isFixedOrAbsolute = style.position === "fixed" || style.position === "absolute";

          if (isFixedOrAbsolute && zIndex >= 100 && style.display !== "none" && style.visibility !== "hidden") {
            const rect = el.getBoundingClientRect();
            if (rect.width >= 120 && rect.width <= 600 && rect.height >= 40 && rect.height <= 300) {
              if (rect.top >= 0 && rect.left >= 0 && rect.top < window.innerHeight && rect.left < window.innerWidth) {
                found.push({
                  x: rect.left + rect.width / 2,
                  y: rect.top + rect.height / 2,
                  width: rect.width,
                  height: rect.height,
                  type: "In-Page Push Creative",
                });
              }
            }
          }
        }

        // 2. Search for Responsive Banner in #ad-slot-leaderboard
        const leaderboard = document.getElementById("ad-slot-leaderboard");
        if (leaderboard) {
          const iframe = leaderboard.querySelector("iframe");
          if (iframe) {
            const rect = iframe.getBoundingClientRect();
            if (rect.width > 80 && rect.height > 25) {
              found.push({
                x: rect.left + rect.width / 2,
                y: rect.top + rect.height / 2,
                width: rect.width,
                height: rect.height,
                type: "Leaderboard Banner (iframe)",
              });
            }
          } else {
            const rect = leaderboard.getBoundingClientRect();
            if (rect.width > 80 && rect.height > 25) {
              found.push({
                x: rect.left + rect.width / 2,
                y: rect.top + rect.height / 2,
                width: rect.width,
                height: rect.height,
                type: "Leaderboard Banner (slot)",
              });
            }
          }
        }

        // 3. Fallback: Any other iframe rendered by ad scripts
        const allIframes = Array.from(document.querySelectorAll("iframe"));
        for (const ifr of allIframes) {
          if (ifr.closest("#ad-slot-leaderboard")) continue;
          const rect = ifr.getBoundingClientRect();
          if (rect.width >= 120 && rect.height >= 30 && rect.top >= 0 && rect.top < window.innerHeight) {
            found.push({
              x: rect.left + rect.width / 2,
              y: rect.top + rect.height / 2,
              width: rect.width,
              height: rect.height,
              type: "HilltopAds Iframe Unit",
            });
          }
        }

        return found;
      });

      if (!candidates || candidates.length === 0) {
        return null;
      }

      // Randomly pick one format for organic variance across sessions
      return candidates[Math.floor(Math.random() * candidates.length)];
    } catch (e) {
      return null;
    }
  }

  /**
   * Executes the full 5-Layer human browsing journey with optional ad click conversion
   */
  public async executeSession(
    onProgress?: (phase: string, detail: string) => void,
    shouldClick: boolean = false
  ): Promise<{ success: boolean; durationSeconds: number; clickConverted: boolean }> {
    const startTime = Date.now();

    try {
      // ── LAYER 1: Arrival & Referrer ──────────────────────────────────────
      const source = TRAFFIC_SOURCES[Math.floor(Math.random() * TRAFFIC_SOURCES.length)];
      let fullUrl = this.baseUrl;
      if (source.utmParams) {
        fullUrl += (fullUrl.includes("?") ? "&" : "?") + source.utmParams;
      }

      onProgress?.("Layer 1: Arrival", `Arriving from ${source.name} -> ${fullUrl}`);

      if (source.referrer) {
        await this.page.setExtraHTTPHeaders({
          referer: source.referrer,
        });
      }

      await this.page.goto(fullUrl, {
        waitUntil: "domcontentloaded",
        timeout: 45000,
      });

      // ── LAYER 1b: Creative Settlement ────────────────────────────────────
      onProgress?.("Layer 1: Ad Creative Settlement", "Waiting for HilltopAds banner and in-page push scripts...");
      await this.kinematics.sleep(2500);

      // Verify ad script in DOM
      const adLoaded = await this.page.evaluate(() => {
        const hasHilltop = document.querySelector('script[src*="fond-appointment"]') !== null;
        const hasLeaderboard = document.getElementById("ad-slot-leaderboard") !== null;
        const hasMonetag = document.querySelector('script[src*="quge5.com"]') !== null;
        return hasHilltop || hasLeaderboard || hasMonetag;
      });

      // Capture screenshot for visual proof
      try {
        const fs = await import("fs");
        const path = await import("path");
        const dir = path.resolve(process.cwd(), "screenshots");
        if (!fs.existsSync(dir)) {
          fs.mkdirSync(dir, { recursive: true });
        }
        await this.page.screenshot({
          path: path.join(dir, "latest_session.png"),
        });
      } catch (ignored) {}

      onProgress?.(
        "Layer 1: Verified",
        adLoaded
          ? `Ad creatives confirmed in DOM (visual screenshot saved)`
          : "DOM loaded, settling viewability"
      );

      // ── LAYER 2: Hero Hesitation ─────────────────────────────────────────
      const heroDwell = 7 + Math.floor(Math.random() * 6); // 7 to 12 seconds
      onProgress?.("Layer 2: Hero Hesitation", `Reading hero section & APY calculator (${heroDwell}s)`);
      await this.kinematics.sleep(heroDwell * 1000);

      // ── LAYER 3: Content Exploration & Scrolling ────────────────────────
      onProgress?.("Layer 3: Content Exploration", "Simulating organic reading progression down the page");

      const totalHeight = await this.page.evaluate(() => document.body.scrollHeight);
      const targetScrollDepth = 0.35 + Math.random() * 0.35; // 35% to 70% depth
      const targetScrollY = Math.floor(totalHeight * targetScrollDepth);

      let currentScrollY = 0;
      const scrollSteps = 4 + Math.floor(Math.random() * 3);
      const stepIncrement = targetScrollY / scrollSteps;

      for (let i = 1; i <= scrollSteps; i++) {
        currentScrollY += stepIncrement;
        await this.kinematics.smoothScroll(currentScrollY, 1100 + Math.random() * 400);

        // Read paragraph dwell
        const readSeconds = 4 + Math.floor(Math.random() * 7);
        onProgress?.(
          "Layer 3: Reading Text",
          `Scroll depth ${Math.floor((currentScrollY / totalHeight) * 100)}% (reading ${readSeconds}s)`
        );
        await this.kinematics.simulateParagraphReading(readSeconds);

        // 25% chance of realistic reverse scroll (re-reading prior paragraph)
        if (Math.random() < 0.25 && currentScrollY > 200) {
          const reverseY = Math.max(0, currentScrollY - 150);
          await this.kinematics.smoothScroll(reverseY, 900);
          await this.kinematics.sleep(1500);
        }
      }

      // ── LAYER 4: Viewability Verification & Ad Engagement ────────────────
      onProgress?.("Layer 4: Viewability Verification", "Holding viewability window for ad creative impressions");
      await this.kinematics.sleep(3000 + Math.random() * 2000);

      let clickConverted = false;
      if (shouldClick) {
        onProgress?.("Layer 4: Click Simulation", "Locating ad creative coordinates for organic click engagement...");

        let target = await this.findAdCreativeTarget();
        if (!target) {
          // If banner scrolled past, smooth scroll back up to hero banner
          await this.kinematics.smoothScroll(0, 1000);
          await this.kinematics.sleep(1500);
          target = await this.findAdCreativeTarget();
        }

        if (target) {
          onProgress?.(
            "Layer 4: Click Simulation",
            `Ad creative detected [${target.type}] at (${Math.round(target.x)}, ${Math.round(target.y)}). Initiating Bézier path...`
          );

          // If target is out of current viewport, scroll it smoothly into view
          if (target.y < 30 || target.y > (this.profile.viewport.height - 30)) {
            await this.kinematics.smoothScroll(Math.max(0, target.y - 120), 800);
            await this.kinematics.sleep(600);
            target = (await this.findAdCreativeTarget()) || target;
          }

          // Listen for targetcreated (advertiser landing page tab/window)
          const browser = this.page.browser();
          const targetCreatedPromise = new Promise<any>((resolve) => {
            const timeout = setTimeout(() => resolve(null), 12000);
            const handler = async (t: any) => {
              if (t.type() === "page") {
                browser.off("targetcreated", handler);
                clearTimeout(timeout);
                resolve(t);
              }
            };
            browser.on("targetcreated", handler);
          });

          // Dispatch organic Bézier movement and human click
          await this.kinematics.humanClick(target.x, target.y);

          // Wait for advertiser landing page target
          const newTarget = await targetCreatedPromise;

          if (newTarget) {
            try {
              const newPage = await newTarget.page();
              if (newPage) {
                const landingUrl = newTarget.url() || "advertiser landing page";
                onProgress?.(
                  "Layer 4: Click Converted",
                  `Advertiser landing opened (${landingUrl.slice(0, 60)}...). Simulating dwell...`
                );
                await newPage.bringToFront();
                await newPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => {});

                // Human dwelling on advertiser page (12 to 16 seconds)
                const dwellTime = 12 + Math.floor(Math.random() * 5);
                onProgress?.("Layer 4: Landing Dwell", `Engaging with advertiser landing page (${dwellTime}s)`);

                // Natural reading scroll on advertiser landing page
                await newPage.evaluate(() => {
                  window.scrollBy({ top: 250 + Math.random() * 150, behavior: "smooth" });
                }).catch(() => {});

                await this.kinematics.sleep(dwellTime * 1000);

                // Cleanly close landing page tab
                await newPage.close().catch(() => {});
                clickConverted = true;
                onProgress?.("Layer 4: Click Complete", "Advertiser landing page dwell finished and tab closed");
              }
            } catch (adTabErr: any) {
              clickConverted = true;
              onProgress?.("Layer 4: Click Note", `Handled advertiser window: ${adTabErr?.message || adTabErr}`);
            }
          } else {
            // Click occurred inline or navigated current tab
            clickConverted = true;
            onProgress?.("Layer 4: Click Converted", "Direct click registered on creative element. Dwelling post-click...");
            await this.kinematics.sleep(6000 + Math.random() * 3000);
          }
        } else {
          onProgress?.("Layer 4: Click Simulation", "No clickable ad creative resolved for this session. Resuming natural browsing.");
        }
      }

      // ── LAYER 5: Natural Exit ────────────────────────────────────────────
      const totalSeconds = Math.round((Date.now() - startTime) / 1000);
      onProgress?.("Layer 5: Complete", `Session finished organically. Total dwell: ${totalSeconds}s`);

      return { success: true, durationSeconds: totalSeconds, clickConverted };
    } catch (error: any) {
      onProgress?.("Error", `Session failed: ${error?.message || error}`);
      return { success: false, durationSeconds: Math.round((Date.now() - startTime) / 1000), clickConverted: false };
    }
  }
}
