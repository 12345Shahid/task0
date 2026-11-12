const { WebSocketServer } = require('ws');
const url = require('url');

class StreamGateway {
  constructor(server, sessionManager, options = {}) {
    this.wss = new WebSocketServer({ server, path: '/stream', ...options });
    this.sessionManager = sessionManager;
    this.clientMap = new Map(); // ws -> { sessionId, lastFpsTime, frameCount, currentFps, bandwidthBytes }
    this.sessionStreams = new Set(); // Set of sessionIds that already have screencast attached

    this.init();
  }

  init() {
    this.wss.on('connection', (ws, req) => {
      const parsedUrl = url.parse(req.url, true);
      let targetSessionId = parsedUrl.query.sessionId;

      let session = null;
      if (targetSessionId) {
        session = this.sessionManager.getSession(targetSessionId);
      }
      if (!session) {
        session = this.sessionManager.getDefaultSession();
        if (session) {
          // Find the record id
          const sessions = this.sessionManager.listSessions();
          if (sessions.length > 0) targetSessionId = sessions[sessions.length - 1].id;
        }
      }

      const clientInfo = {
        sessionId: targetSessionId,
        lastFpsTime: Date.now(),
        frameCount: 0,
        currentFps: 0,
        bandwidthBytes: 0
      };
      this.clientMap.set(ws, clientInfo);

      console.log(`[StreamGateway] Client connected for session: ${targetSessionId || 'none'}. Total clients: ${this.clientMap.size}`);

      if (session && session.isActive) {
        ws.send(JSON.stringify({
          type: 'init',
          sessionId: targetSessionId,
          profile: session.profile.name,
          country: `${session.country.flag} ${session.country.name}`,
          viewport: session.profile.viewport,
          isMobile: !!(session.profile && session.profile.viewport && session.profile.viewport.isMobile),
          url: session.page ? session.page.url() : '',
          tabs: session.getTabsList(),
          bandwidthMB: '0.00'
        }));
        this.ensureScreencast(targetSessionId, session);
      } else {
        ws.send(JSON.stringify({
          type: 'status',
          message: 'No active session. Please create or start a session first.'
        }));
      }

      ws.on('message', async (rawMsg) => {
        try {
          const msg = JSON.parse(rawMsg.toString());
          await this.handleClientMessage(msg, ws);
        } catch (e) {
          console.error('[StreamGateway] Error handling client message:', e.message);
        }
      });

      ws.on('close', () => {
        this.clientMap.delete(ws);
        console.log(`[StreamGateway] Client disconnected. Remaining: ${this.clientMap.size}`);
      });
    });
  }

  ensureScreencast(sessionId, session) {
    if (!sessionId || !session || !session.isActive || this.sessionStreams.has(sessionId)) {
      return;
    }

    this.sessionStreams.add(sessionId);

    session.startScreencast((frame) => {
      const frameByteSize = frame.data.length * 0.75;
      const now = Date.now();

      // Broadcast only to clients watching this specific session
      for (const [clientWs, info] of this.clientMap.entries()) {
        if (info.sessionId === sessionId && clientWs.readyState === 1 /* OPEN */) {
          info.frameCount++;
          info.bandwidthBytes += frameByteSize;

          if (now - info.lastFpsTime >= 1000) {
            info.currentFps = info.frameCount;
            info.frameCount = 0;
            info.lastFpsTime = now;
          }

          const proxyMB = ((session.proxyBytesTransferred || 0) / (1024 * 1024)).toFixed(2);

          clientWs.send(JSON.stringify({
            type: 'frame',
            sessionId,
            data: frame.data,
            metadata: frame.metadata,
            fps: info.currentFps,
            proxyMB: proxyMB,
            bandwidthMB: proxyMB,
            timestamp: frame.timestamp
          }));
        }
      }
    }).catch(err => {
      this.sessionStreams.delete(sessionId);
      console.error(`[StreamGateway] Screencast error for ${sessionId}:`, err.message);
    });
  }

  async handleClientMessage(msg, ws) {
    const clientInfo = this.clientMap.get(ws);
    if (!clientInfo) return;

    // Check if client wants to switch watched session
    if (msg.type === 'watch_session') {
      const newSessionId = msg.sessionId;
      const newSession = this.sessionManager.getSession(newSessionId);
      if (newSession && newSession.isActive) {
        clientInfo.sessionId = newSessionId;
        ws.send(JSON.stringify({
          type: 'init',
          sessionId: newSessionId,
          profile: newSession.profile.name,
          country: `${newSession.country.flag} ${newSession.country.name}`,
          viewport: newSession.profile.viewport,
          isMobile: !!(newSession.profile && newSession.profile.viewport && newSession.profile.viewport.isMobile),
          url: newSession.page ? newSession.page.url() : '',
          tabs: newSession.getTabsList(),
          bandwidthMB: (clientInfo.bandwidthBytes / (1024 * 1024)).toFixed(2)
        }));
        this.ensureScreencast(newSessionId, newSession);
      }
      return;
    }

    const session = this.sessionManager.getSession(clientInfo.sessionId);
    if (!session || !session.isActive) return;

    switch (msg.type) {
      case 'mouse':
        await session.dispatchMouseEvent({
          type: msg.event,
          x: msg.x,
          y: msg.y,
          button: msg.button || 'left',
          clickCount: msg.clickCount || 1
        });
        break;

      case 'touch':
        await session.dispatchTouchEvent({
          type: msg.event,
          touchPoints: msg.touches || (msg.x !== undefined && msg.y !== undefined ? [{ x: msg.x, y: msg.y }] : [])
        });
        break;

      case 'wheel':
        await session.scroll({
          x: msg.x,
          y: msg.y,
          deltaX: msg.deltaX || 0,
          deltaY: msg.deltaY || 0
        });
        break;

      case 'scroll_by':
        await session.scroll({
          x: msg.x,
          y: msg.y,
          deltaX: msg.deltaX || 0,
          deltaY: msg.deltaY || 0
        });
        break;

      case 'key':
        await session.dispatchKeyEvent({
          type: msg.event,
          key: msg.key,
          code: msg.code,
          text: msg.text,
          windowsVirtualKeyCode: msg.keyCode
        });
        break;

      case 'paste_text':
        if (msg.text) {
          await session.pasteText(msg.text);
        }
        break;

      case 'clear_identity':
        await session.clearIdentity();
        ws.send(JSON.stringify({ type: 'notification', message: '🧹 Identity & Cookies wiped clean!' }));
        break;

      case 'new_tab':
        await session.newTab(msg.url || null);
        this.broadcastTabsUpdate(clientInfo.sessionId, session);
        break;

      case 'switch_tab':
        await session.switchTab(msg.tabId);
        this.broadcastTabsUpdate(clientInfo.sessionId, session);
        break;

      case 'close_tab':
        await session.closeTab(msg.tabId);
        this.broadcastTabsUpdate(clientInfo.sessionId, session);
        break;

      case 'navigate':
        if (msg.url) {
          try {
            await session.navigate(msg.url);
            this.broadcastNavUpdate(clientInfo.sessionId, session);
          } catch (e) {
            ws.send(JSON.stringify({ type: 'error', message: 'Navigation failed: ' + e.message }));
          }
        }
        break;

      case 'reload':
        await session.reload();
        this.broadcastNavUpdate(clientInfo.sessionId, session);
        break;

      case 'back':
        await session.goBack();
        this.broadcastNavUpdate(clientInfo.sessionId, session);
        break;

      case 'forward':
        await session.goForward();
        this.broadcastNavUpdate(clientInfo.sessionId, session);
        break;

      case 'ping':
        ws.send(JSON.stringify({ type: 'pong', clientTime: msg.time, serverTime: Date.now() }));
        break;
    }
  }

  broadcastTabsUpdate(sessionId, session) {
    if (!session) return;
    const tabs = session.getTabsList();
    const packet = JSON.stringify({ type: 'tabs_updated', sessionId, tabs });
    for (const [clientWs, info] of this.clientMap.entries()) {
      if (info.sessionId === sessionId && clientWs.readyState === 1) {
        clientWs.send(packet);
      }
    }
    this.broadcastNavUpdate(sessionId, session);
  }

  broadcastNavUpdate(sessionId, session) {
    if (!session || !session.page) return;
    const url = session.page.url();
    const packet = JSON.stringify({ type: 'url_changed', sessionId, url });
    for (const [clientWs, info] of this.clientMap.entries()) {
      if (info.sessionId === sessionId && clientWs.readyState === 1) {
        clientWs.send(packet);
      }
    }
  }
}

module.exports = StreamGateway;
