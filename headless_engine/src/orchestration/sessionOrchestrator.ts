import { BrowserManager } from "../core/browserManager.js";
import { getRandomDeviceProfile } from "../core/deviceProfilePool.js";
import { ProxyManager } from "../core/proxyManager.js";
import { TrafficSimulationWorker } from "../simulation/trafficSimulationWorker.js";

export interface OrchestratorOptions {
  targetUrl: string;
  totalTargetSessions: number;
  minConcurrency: number;
  maxConcurrency: number;
  headless: boolean;
}

export class SessionOrchestrator {
  private options: OrchestratorOptions;
  private completedSessions: number = 0;
  private successfulSessions: number = 0;
  private isRunning: boolean = false;
  private activeWorkers: number = 0;
  private workerCounter: number = 0;

  constructor(options: OrchestratorOptions) {
    this.options = options;
  }

  public async start(): Promise<void> {
    this.isRunning = true;
    console.log("==================================================================");
    console.log("🚀 ANTI-DETECT HEADLESS TRAFFIC ORCHESTRATOR STARTED");
    console.log(`🎯 Target URL: ${this.options.targetUrl}`);
    console.log(`👥 Dynamic Concurrency: Randomized ${this.options.minConcurrency} to ${this.options.maxConcurrency} concurrent workers`);
    console.log(`📊 Goal: ${this.options.totalTargetSessions} completed sessions`);
    console.log(`🛡️ Stealth: Xvfb Virtual Display + US Residential Proxy Rotation`);
    console.log("==================================================================\n");

    const activePromises = new Set<Promise<void>>();

    // Master pool scheduling loop
    while (this.isRunning && (this.completedSessions + this.activeWorkers) < this.options.totalTargetSessions) {
      // Pick dynamic target concurrency between min and max (e.g. 3 to 5)
      const targetConcurrency =
        this.options.minConcurrency +
        Math.floor(Math.random() * (this.options.maxConcurrency - this.options.minConcurrency + 1));

      while (
        this.isRunning &&
        this.activeWorkers < targetConcurrency &&
        (this.completedSessions + this.activeWorkers) < this.options.totalTargetSessions
      ) {
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
    console.log("==================================================================");
  }

  public stop(): void {
    this.isRunning = false;
    console.log("🛑 Stopping orchestrator gracefully...");
  }

  private async executeSingleSession(workerId: number): Promise<void> {
    this.activeWorkers++;
    const profile = getRandomDeviceProfile();
    const proxy = ProxyManager.createSession(`w${workerId}`);

    console.log(
      `[Worker #${workerId}] Started (Active: ${this.activeWorkers}) | Target: ${this.completedSessions + 1}/${this.options.totalTargetSessions} | Identity: ${profile.name}`
    );

    let browser: any = null;
    try {
      const { browser: b, page } = await BrowserManager.launchStealthBrowser(
        profile,
        proxy,
        this.options.headless
      );
      browser = b;

      const worker = new TrafficSimulationWorker(page, profile, this.options.targetUrl);
      const result = await worker.executeSession((phase, detail) => {
        if (phase.includes("Verified") || phase.includes("Complete") || phase.includes("Error")) {
          console.log(`  [Worker #${workerId}] [${phase}] ${detail}`);
        }
      });

      this.completedSessions++;
      if (result.success) {
        this.successfulSessions++;
      }

      console.log(
        `[Worker #${workerId}] Finished (${result.durationSeconds}s) | Progress: ${this.completedSessions}/${this.options.totalTargetSessions} (${Math.round((this.completedSessions / this.options.totalTargetSessions) * 100)}%)\n`
      );
    } catch (err: any) {
      console.error(`[Worker #${workerId}] Error:`, err?.message || err);
      this.completedSessions++; // Increment so campaign progresses
    } finally {
      if (browser) {
        try {
          await browser.close();
        } catch (ignored) {}
      }
      this.activeWorkers--;
    }
  }
}
