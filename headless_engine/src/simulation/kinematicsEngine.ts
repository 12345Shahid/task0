import { Page } from "puppeteer";

export class KinematicsEngine {
  private page: Page;
  private currentMouseX: number;
  private currentMouseY: number;

  constructor(page: Page) {
    this.page = page;
    this.currentMouseX = 150 + Math.random() * 250;
    this.currentMouseY = 150 + Math.random() * 250;
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
   * Dispatches human-like organic mouse movement along a Bézier curve followed by a realistic click
   */
  public async humanClick(targetX: number, targetY: number): Promise<void> {
    const startX = this.currentMouseX;
    const startY = this.currentMouseY;

    // Slight organic offset around target center (human never clicks dead center)
    const destX = targetX + (Math.random() - 0.5) * 8;
    const destY = targetY + (Math.random() - 0.5) * 8;

    // Intermediate Bézier control point with organic lateral curve deviation
    const controlX = (startX + destX) / 2 + (Math.random() - 0.5) * 100;
    const controlY = (startY + destY) / 2 + (Math.random() - 0.5) * 100;

    const steps = 18 + Math.floor(Math.random() * 8); // 18-25 steps
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      // Quadratic Bézier: B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
      const x = Math.pow(1 - t, 2) * startX + 2 * (1 - t) * t * controlX + Math.pow(t, 2) * destX;
      const y = Math.pow(1 - t, 2) * startY + 2 * (1 - t) * t * controlY + Math.pow(t, 2) * destY;

      await this.page.mouse.move(x, y);
      await this.sleep(12 + Math.random() * 12);
    }

    this.currentMouseX = destX;
    this.currentMouseY = destY;

    // Natural ocular micro-hesitation before depressing button (human visual confirmation)
    await this.sleep(140 + Math.random() * 160);

    // Mouse button down
    await this.page.mouse.down({ button: "left" });

    // Physical switch actuation hold duration (65ms to 125ms)
    await this.sleep(65 + Math.random() * 60);

    // Mouse button release
    await this.page.mouse.up({ button: "left" });

    // Post-click settling delay
    await this.sleep(250 + Math.random() * 200);
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
      const currentMouse = { x: this.currentMouseX, y: this.currentMouseY };
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

      this.currentMouseX = targetX;
      this.currentMouseY = targetY;

      return true;
    } catch (e) {
      return false;
    }
  }
}
