"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TrafficSimulationWorker = exports.TRAFFIC_SOURCES = void 0;
const kinematicsEngine_js_1 = require("./kinematicsEngine.js");
exports.TRAFFIC_SOURCES = [
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
class TrafficSimulationWorker {
    page;
    profile;
    kinematics;
    baseUrl;
    constructor(page, profile, targetUrl) {
        this.page = page;
        this.profile = profile;
        this.kinematics = new kinematicsEngine_js_1.KinematicsEngine(page);
        this.baseUrl = targetUrl;
    }
    /**
     * Executes the full 5-Layer human browsing journey
     */
    async executeSession(onProgress) {
        const startTime = Date.now();
        try {
            // ── LAYER 1: Arrival & Referrer ──────────────────────────────────────
            const source = exports.TRAFFIC_SOURCES[Math.floor(Math.random() * exports.TRAFFIC_SOURCES.length)];
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
                const hasAdScript = document.querySelector('script[src*="fond-appointment"]') !== null;
                const hasLeaderboard = document.getElementById("ad-slot-leaderboard") !== null;
                return hasAdScript || hasLeaderboard;
            });
            onProgress?.("Layer 1: Verified", adLoaded ? "Ad creatives confirmed in DOM" : "DOM loaded, settling viewability");
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
                onProgress?.("Layer 3: Reading Text", `Scroll depth ${Math.floor((currentScrollY / totalHeight) * 100)}% (reading ${readSeconds}s)`);
                await this.kinematics.simulateParagraphReading(readSeconds);
                // 25% chance of realistic reverse scroll (re-reading prior paragraph)
                if (Math.random() < 0.25 && currentScrollY > 200) {
                    const reverseY = Math.max(0, currentScrollY - 150);
                    await this.kinematics.smoothScroll(reverseY, 900);
                    await this.kinematics.sleep(1500);
                }
            }
            // ── LAYER 4: Viewability Verification ────────────────────────────────
            onProgress?.("Layer 4: Viewability Verification", "Holding viewability window for ad creative impressions");
            await this.kinematics.sleep(3000 + Math.random() * 2000);
            // ── LAYER 5: Natural Exit ────────────────────────────────────────────
            const totalSeconds = Math.round((Date.now() - startTime) / 1000);
            onProgress?.("Layer 5: Complete", `Session finished organically. Total dwell: ${totalSeconds}s`);
            return { success: true, durationSeconds: totalSeconds };
        }
        catch (error) {
            onProgress?.("Error", `Session failed: ${error?.message || error}`);
            return { success: false, durationSeconds: Math.round((Date.now() - startTime) / 1000) };
        }
    }
}
exports.TrafficSimulationWorker = TrafficSimulationWorker;
