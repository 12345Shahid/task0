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
    isRunning = false;
    activeWorkers = 0;
    constructor(options) {
        this.options = options;
    }
    async start() {
        this.isRunning = true;
        console.log("==================================================================");
        console.log("🚀 ANTI-DETECT HEADLESS TRAFFIC ORCHESTRATOR STARTED");
        console.log(`🎯 Target URL: ${this.options.targetUrl}`);
        console.log(`👥 Concurrency: ${this.options.concurrency} concurrent workers`);
        console.log(`📊 Goal: ${this.options.totalTargetSessions} completed sessions`);
        console.log(`🛡️ Stealth: Xvfb Virtual Display + US Residential Proxy Rotation`);
        console.log("==================================================================\n");
        const workers = [];
        for (let i = 1; i <= this.options.concurrency; i++) {
            workers.push(this.runWorkerLoop(i));
        }
        await Promise.all(workers);
        console.log("\n==================================================================");
        console.log("✅ CAMPAIGN TARGET REACHED!");
        console.log(`Total Sessions: ${this.completedSessions} | Successful: ${this.successfulSessions}`);
        console.log("==================================================================");
    }
    stop() {
        this.isRunning = false;
        console.log("🛑 Stopping orchestrator gracefully...");
    }
    async runWorkerLoop(workerId) {
        let sessionInThisWorker = 0;
        while (this.isRunning && this.completedSessions < this.options.totalTargetSessions) {
            this.activeWorkers++;
            sessionInThisWorker++;
            const profile = (0, deviceProfilePool_js_1.getRandomDeviceProfile)();
            const proxy = proxyManager_js_1.ProxyManager.createSession(`w${workerId}-s${sessionInThisWorker}`);
            console.log(`[Worker #${workerId}] Starting session (${this.completedSessions + 1}/${this.options.totalTargetSessions}) | Identity: ${profile.name} | Session: ${proxy.sessionTag}`);
            let browser = null;
            try {
                const { browser: b, page } = await browserManager_js_1.BrowserManager.launchStealthBrowser(profile, proxy, this.options.headless);
                browser = b;
                const worker = new trafficSimulationWorker_js_1.TrafficSimulationWorker(page, profile, this.options.targetUrl);
                const result = await worker.executeSession((phase, detail) => {
                    // Log key milestones
                    if (phase.includes("Verified") || phase.includes("Complete") || phase.includes("Error")) {
                        console.log(`  [Worker #${workerId}] [${phase}] ${detail}`);
                    }
                });
                this.completedSessions++;
                if (result.success) {
                    this.successfulSessions++;
                }
                console.log(`[Worker #${workerId}] Session finished (${result.durationSeconds}s) | Progress: ${this.completedSessions}/${this.options.totalTargetSessions} (${Math.round((this.completedSessions / this.options.totalTargetSessions) * 100)}%)\n`);
            }
            catch (err) {
                console.error(`[Worker #${workerId}] Error:`, err?.message || err);
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
            // Inter-session human pause between 12 to 24 seconds per worker
            if (this.isRunning && this.completedSessions < this.options.totalTargetSessions) {
                const pauseSeconds = 12 + Math.floor(Math.random() * 12);
                await new Promise((r) => setTimeout(r, pauseSeconds * 1000));
            }
        }
    }
}
exports.SessionOrchestrator = SessionOrchestrator;
