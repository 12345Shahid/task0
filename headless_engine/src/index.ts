import dotenv from "dotenv";
dotenv.config();

import { SessionOrchestrator } from "./orchestration/sessionOrchestrator.js";

// Parse CLI flags
const args = process.argv.slice(2);
const isTest = args.includes("--test");

function getArgValue(flag: string, defaultValue: string): string {
  const index = args.indexOf(flag);
  if (index !== -1 && index + 1 < args.length) {
    return args[index + 1];
  }
  return defaultValue;
}

const targetUrl = getArgValue(
  "--url",
  process.env.TARGET_URL || "https://yieldmatrix-crypto-calculator.vercel.app"
);

const minConcurrency = isTest
  ? 1
  : parseInt(getArgValue("--min", process.env.MIN_CONCURRENCY || "3"), 10);

const maxConcurrency = isTest
  ? 1
  : parseInt(getArgValue("--max", process.env.MAX_CONCURRENCY || "5"), 10);

const targetSessions = isTest
  ? 1
  : parseInt(getArgValue("--target", process.env.TARGET_SESSIONS || "50"), 10);

const isHeadless = process.env.HEADLESS === "true" || process.env.HEADLESS === "1";

const orchestrator = new SessionOrchestrator({
  targetUrl,
  minConcurrency,
  maxConcurrency,
  totalTargetSessions: targetSessions,
  headless: isHeadless,
});

// Handle graceful shutdown
process.on("SIGINT", () => {
  console.log("\nReceived SIGINT. Shutting down gracefully...");
  orchestrator.stop();
});

process.on("SIGTERM", () => {
  console.log("\nReceived SIGTERM. Shutting down gracefully...");
  orchestrator.stop();
});

orchestrator.start().catch((err) => {
  console.error("Fatal orchestrator error:", err);
  process.exit(1);
});
