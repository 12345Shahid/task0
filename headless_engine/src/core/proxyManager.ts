import crypto from "crypto";

export interface ProxyConfig {
  host: string;
  port: number;
  username: string;
  password: string;
  sessionTag: string;
}

export class ProxyManager {
  private static defaultHost = process.env.PROXY_HOST || "thehub.proxy-cheap.com";
  private static defaultPort = parseInt(process.env.PROXY_PORT || "8080", 10);
  private static defaultUser = process.env.PROXY_USER || "Ltj8SfJpTEukyVn";
  private static defaultPass = process.env.PROXY_PASS || "0Pa7rRMzcehfeLR_country-US";

  /**
   * Creates a dedicated proxy session for a worker session
   */
  public static createSession(customTag?: string): ProxyConfig {
    const sessionTag = customTag || `sess-${crypto.randomBytes(4).toString("hex")}`;
    return {
      host: this.defaultHost,
      port: this.defaultPort,
      username: this.defaultUser,
      password: this.defaultPass,
      sessionTag: sessionTag,
    };
  }

  public static getProxyServerArg(config: ProxyConfig): string {
    return `--proxy-server=http://${config.host}:${config.port}`;
  }
}
