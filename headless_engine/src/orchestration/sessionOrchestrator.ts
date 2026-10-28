import { BrowserManager } from "../core/browserManager.js";
import { getRandomDeviceProfile } from "../core/deviceProfilePool.js";
import { ProxyManager } from "../core/proxyManager.js";
import { TrafficSimulationWorker } from "../simulation/trafficSimulationWorker.js";

export interface OrchestratorOptions {
  targetUrl: string;
  totalTargetSessions: number;
  concurrency: number;
  headless: boolean;
}

export class SessionOrchestrator {
  private options: OrchestratorOptions;
  private completedSessions: number = 0;
  private successfulSessions: number = 0;
  private isRunning: boolean = false;
  private activeWorkers: number = 0;

  constructor(options: OrchestratorOptions) {
    this.options = options;
  }

  public async start(): Promise<void> {
    this.isRunning = true;
    console.log("==================================================================");
    console.log("🚀 ANTI-DETECT HEADLESS TRAFFIC ORCHESTRATOR STARTED");
    console.log(`🎯 Target URL: ${this.options.targetUrl}`);
    console.log(`👥 Concurrency: ${this.options.concurrency} concurrent workers`);
    console.log(`📊 Goal: ${this.options.totalTargetSessions} completed sessions`);
    console.log(`🛡️ Stealth: Xvfb Virtual Display + US Residential Proxy Rotation`);
    console.log("==================================================================\n");

    const workers: Promise<void>[] = [];
    for (let i = 1; i <= this.options.concurrency; i++) {
      workers.push(this.runWorkerLoop(i));
    }

    await Promise.all(workers);

    console.log("\n==================================================================");
    console.log("✅ CAMPAIGN TARGET REACHED!");
    console.log(`Total Sessions: ${this.completedSessions} | Successful: ${this.successfulSessions}`);
    console.log("==================================================================");
  }

  public stop(): void {
    this.isRunning = false;
    console.log("🛑 Stopping orchestrator gracefully...");
  }

  private async runWorkerLoop(workerId: number): Promise<void> {
    let sessionInThisWorker = 0;

    while (this.isRunning && this.completedSessions < this.options.totalTargetSessions) {
      this.activeWorkers++;
      sessionInThisWorker++;

      const profile = getRandomDeviceProfile();
      const proxy = ProxyManager.createSession(`w${workerId}-s${sessionInThisWorker}`);

      console.log(
        `[Worker #${workerId}] Starting session (${this.completedSessions + 1}/${this.options.totalTargetSessions}) | Identity: ${profile.name} | Session: ${proxy.sessionTag}`
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
          // Log key milestones
          if (phase.includes("Verified") || phase.includes("Complete") || phase.includes("Error")) {
            console.log(`  [Worker #${workerId}] [${phase}] ${detail}`);
          }
        });

        this.completedSessions++;
        if (result.success) {
          this.successfulSessions++;
        }

        console.log(
          `[Worker #${workerId}] Session finished (${result.durationSeconds}s) | Progress: ${this.completedSessions}/${this.options.totalTargetSessions} (${Math.round((this.completedSessions / this.options.totalTargetSessions) * 100)}%)\n`
        );
      } catch (err: any) {
        console.error(`[Worker #${workerId}] Error:`, err?.message || err);
      } finally {
        if (browser) {
          try {
            await browser.close();
          } catch (ignored) {}
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
