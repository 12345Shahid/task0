"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const dotenv_1 = __importDefault(require("dotenv"));
dotenv_1.default.config();
const sessionOrchestrator_js_1 = require("./orchestration/sessionOrchestrator.js");
// Parse CLI flags
const args = process.argv.slice(2);
const isTest = args.includes("--test");
function getArgValue(flag, defaultValue) {
    const index = args.indexOf(flag);
    if (index !== -1 && index + 1 < args.length) {
        return args[index + 1];
    }
    return defaultValue;
}
const targetUrl = getArgValue("--url", process.env.TARGET_URL || "https://yieldmatrix-crypto-calculator.vercel.app");
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
const orchestrator = new sessionOrchestrator_js_1.SessionOrchestrator({
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
