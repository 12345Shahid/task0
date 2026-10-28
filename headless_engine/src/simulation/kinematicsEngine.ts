import { Page } from "puppeteer";

export class KinematicsEngine {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /**
   * Helper delay with randomized jitter
   */
  public async sleep(ms: number, jitterPercent: number = 0.2): Promise<void> {
    const delta = ms * jitterPercent;
    const finalMs = ms - delta + Math.random() * (delta * 2);
    return new Promise((resolve) => setTimeout(resolve, Math.max(10, finalMs)));
  }

  /**
   * Dispatches human-like smooth scroll with ease-in/ease-out deceleration
   */
  public async smoothScroll(targetY: number, durationMs: number = 1200): Promise<void> {
    await this.page.evaluate(
      async ({ targetY, durationMs }) => {
        const startY = window.scrollY;
        const distance = targetY - startY;
        const startTime = performance.now();

        return new Promise<void>((resolve) => {
          function step(currentTime: number) {
            const elapsed = currentTime - startTime;
            const progress = Math.min(elapsed / durationMs, 1);

            // Cubic Ease-Out curve: f(t) = 1 - (1 - t)^3
            const easeOut = 1 - Math.pow(1 - progress, 3);
            const currentY = startY + distance * easeOut;

            window.scrollTo(0, currentY);

            if (progress < 1) {
              requestAnimationFrame(step);
            } else {
              resolve();
            }
          }
          requestAnimationFrame(step);
        });
      },
      { targetY, durationMs }
    );

    // Micro-pause after scroll settling
    await this.sleep(300 + Math.random() * 400);
  }

  /**
   * Simulates a human reading a paragraph with micro-tremors and natural pauses
   */
  public async simulateParagraphReading(seconds: number): Promise<void> {
    const intervals = Math.max(1, Math.floor(seconds / 2));
    for (let i = 0; i < intervals; i++) {
      // Small micro-scroll or slight cursor drift
      const microDrift = (Math.random() - 0.4) * 35;
      await this.page.evaluate((drift) => {
        window.scrollBy({ top: drift, behavior: "smooth" });
      }, microDrift);

      await this.sleep(1800 + Math.random() * 800);
    }
  }

  /**
   * Dispatches an organic cursor movement along a Bézier curve towards an element
   */
  public async moveMouseToElement(selector: string): Promise<boolean> {
    try {
      const element = await this.page.$(selector);
      if (!element) return false;

      const box = await element.boundingBox();
      if (!box) return false;

      // Target center with slight natural offset
      const targetX = box.x + box.width * (0.3 + Math.random() * 0.4);
      const targetY = box.y + box.height * (0.3 + Math.random() * 0.4);

      // Generate intermediate control points for Bézier curve
      const currentMouse = { x: box.x - 100, y: box.y - 100 };
      const controlX = (currentMouse.x + targetX) / 2 + (Math.random() - 0.5) * 80;
      const controlY = (currentMouse.y + targetY) / 2 + (Math.random() - 0.5) * 80;

      const steps = 15;
      for (let t = 0; t <= 1; t += 1 / steps) {
        // Quadratic Bézier: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
        const x = Math.pow(1 - t, 2) * currentMouse.x + 2 * (1 - t) * t * controlX + Math.pow(t, 2) * targetX;
        const y = Math.pow(1 - t, 2) * currentMouse.y + 2 * (1 - t) * t * controlY + Math.pow(t, 2) * targetY;

        await this.page.mouse.move(x, y);
        await this.sleep(15 + Math.random() * 10);
      }

      return true;
    } catch (e) {
      return false;
    }
  }
}
