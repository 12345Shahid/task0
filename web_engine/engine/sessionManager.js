const { v4: uuidv4 } = require('crypto');
const BrowserSession = require('./browserSession');

class SessionManager {
  constructor({ maxSessions = 10, idleTimeoutMs = 30 * 60 * 1000 } = {}) {
    this.sessions = new Map(); // sessionId -> { session, createdAt, lastActiveAt, timer }
    this.maxSessions = maxSessions;
    this.idleTimeoutMs = idleTimeoutMs;
  }

  /**
   * Create a new isolated session
   */
  async createSession({ profileId = 'poco_m3', countryCode = 'US', headless = 'new' } = {}) {
    if (this.sessions.size >= this.maxSessions) {
      // Find oldest session to terminate
      const oldestId = Array.from(this.sessions.keys())[0];
      await this.destroySession(oldestId);
    }

    const sessionId = 'sess_' + Date.now().toString(36) + '_' + Math.random().toString(36).substring(2, 7);
    const session = new BrowserSession();

    const startResult = await session.start({ profileId, countryCode, headless });

    const sessionRecord = {
      id: sessionId,
      session,
      createdAt: Date.now(),
      lastActiveAt: Date.now(),
      meta: startResult
    };

    // Auto-timeout timer
    this.resetIdleTimer(sessionRecord);

    this.sessions.set(sessionId, sessionRecord);
    return sessionRecord;
  }

  /**
   * Get an existing session
   */
  getSession(sessionId) {
    const record = this.sessions.get(sessionId);
    if (record) {
      record.lastActiveAt = Date.now();
      this.resetIdleTimer(record);
      return record.session;
    }
    return null;
  }

  /**
   * Get default / latest active session (convenience fallback)
   */
  getDefaultSession() {
    if (this.sessions.size === 0) return null;
    const latest = Array.from(this.sessions.values()).pop();
    latest.lastActiveAt = Date.now();
    return latest.session;
  }

  /**
   * List all active sessions
   */
  listSessions() {
    return Array.from(this.sessions.values()).map(r => ({
      id: r.id,
      profile: r.session.profile ? r.session.profile.name : 'Unknown',
      country: r.session.country ? `${r.session.country.flag} ${r.session.country.name}` : 'Unknown',
      tabsCount: r.session.tabs.length,
      createdAt: r.createdAt,
      lastActiveAt: r.lastActiveAt,
      url: r.session.page ? r.session.page.url() : ''
    }));
  }

  /**
   * Destroy an isolated session
   */
  async destroySession(sessionId) {
    const record = this.sessions.get(sessionId);
    if (!record) return false;

    if (record.timer) {
      clearTimeout(record.timer);
    }

    try {
      await record.session.close();
    } catch (e) {
      console.warn(`[SessionManager] Error closing session ${sessionId}:`, e.message);
    }

    this.sessions.delete(sessionId);
    console.log(`[SessionManager] Session ${sessionId} destroyed. Active: ${this.sessions.size}`);
    return true;
  }

  /**
   * Destroy all sessions (server shutdown)
   */
  async destroyAll() {
    for (const id of this.sessions.keys()) {
      await this.destroySession(id);
    }
  }

  resetIdleTimer(record) {
    if (record.timer) clearTimeout(record.timer);
    record.timer = setTimeout(async () => {
      console.log(`[SessionManager] Session ${record.id} timed out due to inactivity.`);
      await this.destroySession(record.id);
    }, this.idleTimeoutMs);
  }
}

module.exports = SessionManager;
