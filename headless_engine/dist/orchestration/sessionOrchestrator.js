"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionOrchestrator = void 0;
const browserManager_js_1 = require("../core/browserManager.js");
const deviceProfilePool_js_1 = require("../core/deviceProfilePool.js");
const proxyManager_js_1 = require("../core/proxyManager.js");
const trafficSimulationWorker_js_1 = require("../simulation/trafficSimulationWorker.js");
class SessionOrchestrator {
    options;
    completedSessions = 0;
    successfulSessions = 0;
    convertedClicks = 0;
    clickSessions = new Set();
    isRunning = false;
    activeWorkers = 0;
    workerCounter = 0;
    constructor(options) {
        this.options = options;
        // Schedule ~5%-6% natural human CTR across the target sessions
        const targetClicks = Math.max(1, Math.round(this.options.totalTargetSessions * 0.06));
        const segmentSize = Math.floor(this.options.totalTargetSessions / targetClicks);
        for (let i = 0; i < targetClicks; i++) {
            const min = Math.max(3, i * segmentSize + 3);
            const max = Math.min(this.options.totalTargetSessions - 2, (i + 1) * segmentSize - 1);
            const chosen = Math.floor(min + Math.random() * (max - min + 1));
            this.clickSessions.add(chosen);
        }
    }
    async start() {
        this.isRunning = true;
        console.log("==================================================================");
        console.log("🚀 ANTI-DETECT HEADLESS TRAFFIC ORCHESTRATOR STARTED");
        console.log(`🎯 Target URL: ${this.options.targetUrl}`);
        console.log(`👥 Dynamic Concurrency: Randomized ${this.options.minConcurrency} to ${this.options.maxConcurrency} concurrent workers`);
        console.log(`📊 Goal: ${this.options.totalTargetSessions} completed sessions`);
        console.log(`🎯 Organic CTR Click Engine: ${this.clickSessions.size} human ad clicks scheduled for sessions: [${Array.from(this.clickSessions).join(", ")}] (~${((this.clickSessions.size / this.options.totalTargetSessions) * 100).toFixed(1)}% CTR)`);
        console.log(`🛡️ Stealth: Xvfb Virtual Display + US Residential Proxy Rotation`);
        console.log("==================================================================\n");
        const activePromises = new Set();
        // Master pool scheduling loop
        while (this.isRunning && (this.completedSessions + this.activeWorkers) < this.options.totalTargetSessions) {
            // Pick dynamic target concurrency between min and max (e.g. 3 to 5)
            const targetConcurrency = this.options.minConcurrency +
                Math.floor(Math.random() * (this.options.maxConcurrency - this.options.minConcurrency + 1));
            while (this.isRunning &&
                this.activeWorkers < targetConcurrency &&
                (this.completedSessions + this.activeWorkers) < this.options.totalTargetSessions) {
                this.workerCounter++;
                const workerId = this.workerCounter;
                const p = this.executeSingleSession(workerId).finally(() => {
                    activePromises.delete(p);
                });
                activePromises.add(p);
                // Stagger worker launches by 4 to 10 seconds for realistic organic spread
                const staggerDelay = 4000 + Math.floor(Math.random() * 6000);
                await new Promise((r) => setTimeout(r, staggerDelay));
            }
            // Check loop every 2 seconds
            await new Promise((r) => setTimeout(r, 2000));
        }
        // Wait for all remaining active sessions to complete
        await Promise.all(Array.from(activePromises));
        console.log("\n==================================================================");
        console.log("✅ CAMPAIGN TARGET REACHED!");
        console.log(`Total Sessions: ${this.completedSessions} | Successful: ${this.successfulSessions}`);
        console.log(`Total Ad Clicks Converted: ${this.convertedClicks} | Effective CTR: ${((this.convertedClicks / Math.max(1, this.completedSessions)) * 100).toFixed(2)}%`);
        console.log("==================================================================");
    }
    stop() {
        this.isRunning = false;
        console.log("🛑 Stopping orchestrator gracefully...");
    }
    async executeSingleSession(workerId) {
        this.activeWorkers++;
        const profile = (0, deviceProfilePool_js_1.getRandomDeviceProfile)();
        const proxy = proxyManager_js_1.ProxyManager.createSession(`w${workerId}`);
        const shouldClick = this.clickSessions.has(workerId);
        console.log(`[Worker #${workerId}] Started (Active: ${this.activeWorkers}) | Target: ${this.completedSessions + 1}/${this.options.totalTargetSessions} | Identity: ${profile.name}${shouldClick ? " 🔥 [CLICK SCHEDULED]" : ""}`);
        let browser = null;
        try {
            const { browser: b, page } = await browserManager_js_1.BrowserManager.launchStealthBrowser(profile, proxy, this.options.headless);
            browser = b;
            const worker = new trafficSimulationWorker_js_1.TrafficSimulationWorker(page, profile, this.options.targetUrl);
            const result = await worker.executeSession((phase, detail) => {
                if (phase.includes("Verified") ||
                    phase.includes("Complete") ||
                    phase.includes("Error") ||
                    phase.includes("Click") ||
                    phase.includes("Landing")) {
                    console.log(`  [Worker #${workerId}] [${phase}] ${detail}`);
                }
            }, shouldClick);
            this.completedSessions++;
            if (result.success) {
                this.successfulSessions++;
            }
            if (result.clickConverted) {
                this.convertedClicks++;
                console.log(`💰 [Worker #${workerId}] CONVERTED AD CLICK REGISTERED! (Total Clicks: ${this.convertedClicks}/${this.clickSessions.size} | CTR: ${((this.convertedClicks / this.completedSessions) * 100).toFixed(1)}%)`);
            }
            console.log(`[Worker #${workerId}] Finished (${result.durationSeconds}s) | Progress: ${this.completedSessions}/${this.options.totalTargetSessions} (${Math.round((this.completedSessions / this.options.totalTargetSessions) * 100)}%)\n`);
        }
        catch (err) {
            console.error(`[Worker #${workerId}] Error:`, err?.message || err);
            this.completedSessions++; // Increment so campaign progresses
        }
        finally {
            if (browser) {
                try {
                    await browser.close();
                }
                catch (ignored) { }
            }
            this.activeWorkers--;
        }
    }
}
exports.SessionOrchestrator = SessionOrchestrator;
