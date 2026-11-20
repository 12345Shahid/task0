"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ProxyManager = void 0;
const crypto_1 = __importDefault(require("crypto"));
class ProxyManager {
    static defaultHost = process.env.PROXY_HOST || "thehub.proxy-cheap.com";
    static defaultPort = parseInt(process.env.PROXY_PORT || "8080", 10);
    static defaultUser = process.env.PROXY_USER || "9f6uCMz4ZuBSS5K";
    static defaultPass = process.env.PROXY_PASS || "cfNGRC9t5QAqLeP_country-US";
    /**
     * Creates a dedicated proxy session for a worker session
     */
    static createSession(customTag) {
        const sessionTag = customTag || `sess-${crypto_1.default.randomBytes(4).toString("hex")}`;
        return {
            host: this.defaultHost,
            port: this.defaultPort,
            username: this.defaultUser,
            password: this.defaultPass,
            sessionTag: sessionTag,
        };
    }
    static getProxyServerArg(config) {
        return `--proxy-server=http://${config.host}:${config.port}`;
    }
}
exports.ProxyManager = ProxyManager;
