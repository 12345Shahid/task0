/**
 * AuraBrowser Stealth Anti-Detect Fingerprint Injector (Hardened V55 Web Engine)
 * 
 * 100% Score Hardening:
 * 1. WebRTC STUN Leak Protection (Eliminates real IP leak)
 * 2. Mobile Plugin Array Masking (Eliminates desktop PDF plugin leak on mobile)
 * 3. Dynamic Chromium Engine Version Alignment (Eliminates version mismatch)
 * 4. Spec-Compliant WebGL Vendor & Renderer Parameter Alignment (Eliminates WebGL exception)
 * 5. Mathematical Timezone & Intl Locale Consistency (Pixelscan & BrowserScan green)
 */

function generateStealthScript(profile, country, noiseSeed = Math.random(), chromeVersion = '149.0.7827.22') {
  const tz = country.timezone || 'America/New_York';
  const lang = country.locale || 'en-US';
  const sw = profile.screen.width;
  const sh = profile.screen.height;
  const webglVendor = profile.webgl.vendor;
  const webglRenderer = profile.webgl.renderer;
  const spoofPlatform = profile.platform;
  const spoofVendor = profile.vendor;
  const isIos = profile.id.includes('iphone') || profile.id.includes('ipad');
  const isMobile = !!profile.isMobile;
  const profileName = profile.name;
  const clientHintsModel = profile.clientHints ? profile.clientHints.model : '';
  const spoofHwConcurrency = profile.hardwareConcurrency || 8;
  const spoofDevMemory = profile.deviceMemory || 8;
  
  const cFull = chromeVersion;
  const cMajor = chromeVersion.split('.')[0] || '149';
  const androidVer = profile.clientHints ? profile.clientHints.platformVersion : '12.0.0';
  const spoofLat = country.latitude || 40.7128;
  const spoofLon = country.longitude || -74.0060;

  return `(function() {
  if (window.__antidetect_installed__) return;
  try {
    Object.defineProperty(window, '__antidetect_installed__', {
      value: true, writable: false, configurable: false, enumerable: false
    });
  } catch(e) { window.__antidetect_installed__ = true; }

  try {
    var spoofTz = ${JSON.stringify(tz)};
    var spoofLang = ${JSON.stringify(lang)};
    var sw = ${sw};
    var sh = ${sh};
    var webglVendor = ${JSON.stringify(webglVendor)};
    var webglRenderer = ${JSON.stringify(webglRenderer)};
    var spoofPlatform = ${JSON.stringify(spoofPlatform)};
    var spoofVendor = ${JSON.stringify(spoofVendor)};
    var isIos = ${isIos};
    var isMobile = ${isMobile};
    var profileName = ${JSON.stringify(profileName)};
    var clientHintsModel = ${JSON.stringify(clientHintsModel)};
    var spoofHwConcurrency = ${spoofHwConcurrency};
    var spoofDevMemory = ${spoofDevMemory};
    var noiseSeed = ${noiseSeed};
    var cMajor = ${JSON.stringify(cMajor)};
    var cFull = ${JSON.stringify(cFull)};
    var androidVer = ${JSON.stringify(androidVer)};
    var spoofLat = ${spoofLat};
    var spoofLon = ${spoofLon};

    // Global Native Function Disguise (Intercepts Function.prototype.toString.call via WeakSet)
    var origFnToString = Function.prototype.toString;
    var nativeFns = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;
    var customFnToString = function toString() {
      if (typeof this !== 'function') {
        return origFnToString.apply(this, arguments);
      }
      try {
        if (nativeFns && nativeFns.has(this)) {
          var fnName = this.name || '';
          return 'function ' + fnName + '() { [native code] }';
        }
      } catch(e) {}
      try {
        return origFnToString.apply(this, arguments);
      } catch(e) {
        return Function.prototype.toString.apply(this, arguments);
      }
    };
    if (nativeFns) nativeFns.add(customFnToString);
    try { Object.defineProperty(customFnToString, 'name', { value: 'toString', configurable: true, enumerable: false, writable: false }); } catch(e) {}
    try { Object.defineProperty(customFnToString, 'length', { value: 0, configurable: true, enumerable: false, writable: false }); } catch(e) {}
    try { delete customFnToString.prototype; } catch(e) {}\n
    try {
      Object.defineProperty(Function.prototype, 'toString', {
        value: customFnToString,
        writable: true,
        configurable: true,
        enumerable: false
      });
    } catch(e) {}

    function makeNative(fn, name, length) {
      if (!fn) return fn;
      try {
        Object.defineProperty(fn, 'name', { value: name, configurable: true, enumerable: false, writable: false });
        Object.defineProperty(fn, 'length', { value: (typeof length === 'number' ? length : 0), configurable: true, enumerable: false, writable: false });
        delete fn.prototype;
      } catch(e) {}
      if (nativeFns) nativeFns.add(fn);
      return fn;
    }

    // ====================================================
    // 1. WebRTC Leak Protection (Eliminates Bangladesh IP Leak)
    // ====================================================
    try {
      var rtcTarget = window.RTCPeerConnection || window.webkitRTCPeerConnection;
      if (rtcTarget) {
        var origRTC = rtcTarget;
        var customRTC = function RTCPeerConnection(cfg, cstr) {
          var safeCfg = cfg ? Object.assign({}, cfg) : {};
          if (safeCfg.iceServers) safeCfg.iceServers = [];
          return new origRTC(safeCfg, cstr);
        };
        customRTC.prototype = origRTC.prototype;
        if (origRTC.generateCertificate) customRTC.generateCertificate = origRTC.generateCertificate;
        makeNative(customRTC, 'RTCPeerConnection', 0);
        window.RTCPeerConnection = customRTC;
        if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = customRTC;

        if (origRTC.prototype && origRTC.prototype.setConfiguration) {
          var origSetCfg = origRTC.prototype.setConfiguration;
          origRTC.prototype.setConfiguration = makeNative(function setConfiguration(c) {
            var sc = c ? Object.assign({}, c) : {};
            if (sc.iceServers) sc.iceServers = [];
            return origSetCfg.call(this, sc);
          }, 'setConfiguration', 1);
        }
      }
    } catch(e) {}

    // ====================================================
    // 2. Mobile Plugin Array Masking (Eliminates Desktop PDF Leak)
    // ====================================================
    try {
      if (isMobile) {
        var emptyPlugins = Object.create(PluginArray.prototype);
        Object.defineProperty(emptyPlugins, 'length', { value: 0, configurable: true, enumerable: true });
        Object.defineProperty(emptyPlugins, 'item', {
          value: makeNative(function(i) { return null; }, 'item', 1),
          writable: true, configurable: true, enumerable: false
        });
        Object.defineProperty(emptyPlugins, 'namedItem', {
          value: makeNative(function(n) { return null; }, 'namedItem', 1),
          writable: true, configurable: true, enumerable: false
        });
        Object.defineProperty(emptyPlugins, 'refresh', {
          value: makeNative(function() {}, 'refresh', 0),
          writable: true, configurable: true, enumerable: false
        });

        Object.defineProperty(Navigator.prototype, 'plugins', {
          get: makeNative(function() { return emptyPlugins; }, 'get plugins', 0),
          configurable: true, enumerable: true
        });

        var emptyMimes = Object.create(MimeTypeArray.prototype);
        Object.defineProperty(emptyMimes, 'length', { value: 0, configurable: true, enumerable: true });
        Object.defineProperty(emptyMimes, 'item', {
          value: makeNative(function(i) { return null; }, 'item', 1),
          writable: true, configurable: true, enumerable: false
        });
        Object.defineProperty(emptyMimes, 'namedItem', {
          value: makeNative(function(n) { return null; }, 'namedItem', 1),
          writable: true, configurable: true, enumerable: false
        });

        Object.defineProperty(Navigator.prototype, 'mimeTypes', {
          get: makeNative(function() { return emptyMimes; }, 'get mimeTypes', 0),
          configurable: true, enumerable: true
        });

        Object.defineProperty(Navigator.prototype, 'pdfViewerEnabled', {
          get: makeNative(function() { return false; }, 'get pdfViewerEnabled', 0),
          configurable: true, enumerable: true
        });
      }
    } catch(e) {}

    // ====================================================
    // 3. Remove Automation / navigator.webdriver Flags
    // ====================================================
    try {
      Object.defineProperty(Navigator.prototype, 'webdriver', {
        get: makeNative(function() { return undefined; }, 'get webdriver', 0),
        configurable: true,
        enumerable: true
      });
      delete window.cdc_adoQpoasnfa76pfcZLmcfl_;
    } catch(e) {}

    // ====================================================
    // 4. Hardware Concurrency & Device Memory
    // ====================================================
    try {
      Object.defineProperty(Navigator.prototype, 'hardwareConcurrency', {
        get: makeNative(function() { return spoofHwConcurrency; }, 'get hardwareConcurrency', 0),
        configurable: true, enumerable: true
      });
      Object.defineProperty(Navigator.prototype, 'deviceMemory', {
        get: makeNative(function() { return spoofDevMemory; }, 'get deviceMemory', 0),
        configurable: true, enumerable: true
      });
    } catch(e) {}

    // ====================================================
    // 5. Platform & Vendor
    // ====================================================
    try {
      Object.defineProperty(Navigator.prototype, 'platform', {
        get: makeNative(function() { return spoofPlatform; }, 'get platform', 0),
        configurable: true, enumerable: true
      });
      Object.defineProperty(Navigator.prototype, 'vendor', {
        get: makeNative(function() { return spoofVendor; }, 'get vendor', 0),
        configurable: true, enumerable: true
      });
    } catch(e) {}

    // ====================================================
    // 6. Languages & Intl Default Locale
    // ====================================================
    try {
      var langs = [spoofLang, spoofLang.split('-')[0]];
      Object.defineProperty(Navigator.prototype, 'languages', {
        get: makeNative(function() { return Object.freeze(langs.slice()); }, 'get languages', 0),
        configurable: true, enumerable: true
      });
      Object.defineProperty(Navigator.prototype, 'language', {
        get: makeNative(function() { return spoofLang; }, 'get language', 0),
        configurable: true, enumerable: true
      });
    } catch(e) {}

    // ====================================================
    // 7. Mathematical Timezone Consistency (Pixelscan All-Green)
    // ====================================================
    try {
      var OrigDateTimeFormat = Intl.DateTimeFormat;
      var WrappedDateTimeFormat = function(locales, options) {
        var targetLocale = locales || spoofLang;
        var opts = Object.assign({}, options);
        if (!opts.timeZone) {
          opts.timeZone = spoofTz;
        }
        var instance = new OrigDateTimeFormat(targetLocale, opts);
        var origResolved = instance.resolvedOptions;
        instance.resolvedOptions = makeNative(function resolvedOptions() {
          var ro = origResolved.call(this);
          ro.timeZone = spoofTz;
          ro.locale = spoofLang;
          return ro;
        }, 'resolvedOptions', 0);
        return instance;
      };
      WrappedDateTimeFormat.prototype = OrigDateTimeFormat.prototype;
      WrappedDateTimeFormat.supportedLocalesOf = OrigDateTimeFormat.supportedLocalesOf;
      makeNative(WrappedDateTimeFormat, 'DateTimeFormat', 0);
      Intl.DateTimeFormat = WrappedDateTimeFormat;

      var getTargetTimezoneOffset = function(dateObj) {
        try {
          var date = dateObj || new Date();
          var invdate = new Date(date.toLocaleString('en-US', { timeZone: spoofTz }));
          var diff = date.getTime() - invdate.getTime();
          return Math.round(diff / 60000);
        } catch(e) {
          return 300;
        }
      };

      var origGetTimezoneOffset = Date.prototype.getTimezoneOffset;
      Date.prototype.getTimezoneOffset = makeNative(function getTimezoneOffset() {
        return getTargetTimezoneOffset(this);
      }, 'getTimezoneOffset', 0);

      var origToTimeString = Date.prototype.toTimeString;
      Date.prototype.toTimeString = makeNative(function toTimeString() {
        try {
          var offset = getTargetTimezoneOffset(this);
          var sign = offset > 0 ? '-' : '+';
          var absOffset = Math.abs(offset);
          var hours = String(Math.floor(absOffset / 60)).padStart(2, '0');
          var mins = String(absOffset % 60).padStart(2, '0');
          var tzStr = 'GMT' + sign + hours + mins;
          var timeStr = this.toLocaleTimeString('en-US', { timeZone: spoofTz, hour12: false });
          return timeStr + ' ' + tzStr;
        } catch(e) {
          return origToTimeString.call(this);
        }
      }, 'toTimeString', 0);

      Date.prototype.getHours = makeNative(function getHours() {
        try {
          var str = this.toTimeString().split(' ')[0];
          return parseInt(str.split(':')[0], 10);
        } catch(e) { return 0; }
      }, 'getHours', 0);

      Date.prototype.getMinutes = makeNative(function getMinutes() {
        try {
          var str = this.toTimeString().split(' ')[0];
          return parseInt(str.split(':')[1], 10);
        } catch(e) { return 0; }
      }, 'getMinutes', 0);

      Date.prototype.getSeconds = makeNative(function getSeconds() {
        try {
          var str = this.toTimeString().split(' ')[0];
          return parseInt(str.split(':')[2], 10);
        } catch(e) { return 0; }
      }, 'getSeconds', 0);

      Date.prototype.toDateString = makeNative(function toDateString() {
        try {
          var d = new Date(this.toLocaleString('en-US', { timeZone: spoofTz }));
          var days = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
          var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
          return days[d.getDay()] + ' ' + months[d.getMonth()] + ' ' + String(d.getDate()).padStart(2, '0') + ' ' + d.getFullYear();
        } catch(e) {
          return 'Wed Nov 11 2026';
        }
      }, 'toDateString', 0);

      Date.prototype.toString = makeNative(function toString() {
        return this.toDateString() + ' ' + this.toTimeString();
      }, 'toString', 0);
    } catch(e) {}

    // ====================================================
    // 8. 100% Spec-Compliant WebGL Vendor & Renderer (Zero Exceptions)
    // ====================================================
    try {
      function patchGL(proto, isW2) {
        if (!proto) return;
        var origGetParameter = proto.getParameter;
        var origGetExtension = proto.getExtension;

        proto.getParameter = makeNative(function getParameter(param) {
          try {
            if (this && typeof this.isContextLost === 'function' && !this.isContextLost()) {
              if (param === 37445) return webglVendor; // UNMASKED_VENDOR_WEBGL
              if (param === 37446) return webglRenderer; // UNMASKED_RENDERER_WEBGL
              if (param === 7936) return 'WebKit'; // VENDOR
              if (param === 7937) return 'WebKit WebGL'; // RENDERER
              if (param === 7938) return isW2 ? 'WebGL 2.0 (OpenGL ES 3.0 Chromium)' : 'WebGL 1.0 (OpenGL ES 2.0 Chromium)'; // VERSION
              if (param === 35724) return isW2 ? 'WebGL GLSL ES 3.00 (OpenGL ES GLSL ES 3.00 Chromium)' : 'WebGL GLSL ES 1.0 (OpenGL ES GLSL 1.0 Chromium)'; // SHADING_LANGUAGE_VERSION
            }
          } catch(e) {}
          return origGetParameter.apply(this, arguments);
        }, 'getParameter', 1);

        proto.getExtension = makeNative(function getExtension(name) {
          if (name === 'WEBGL_debug_renderer_info') {
            return {
              UNMASKED_VENDOR_WEBGL: 37445,
              UNMASKED_RENDERER_WEBGL: 37446
            };
          }
          return origGetExtension.apply(this, arguments);
        }, 'getExtension', 1);
      }

      if (typeof WebGLRenderingContext !== 'undefined') patchGL(WebGLRenderingContext.prototype, false);
      if (typeof WebGL2RenderingContext !== 'undefined') patchGL(WebGL2RenderingContext.prototype, true);
    } catch(e) {}

    // ====================================================
    // 9. Chrome Client Hints (navigator.userAgentData)
    // ====================================================
    try {
      if (!isIos) {
        var brandsList = Object.freeze([
          Object.freeze({ brand: 'Chromium', version: cMajor }),
          Object.freeze({ brand: 'Google Chrome', version: cMajor }),
          Object.freeze({ brand: 'Not-A.Brand', version: '24' })
        ]);
        var fakeUAData = {
          brands: brandsList,
          mobile: isMobile,
          platform: 'Android',
          getHighEntropyValues: makeNative(function(hints) {
            return Promise.resolve({
              architecture: 'arm64',
              bitness: '64',
              brands: brandsList,
              fullVersionList: Object.freeze([
                Object.freeze({ brand: 'Chromium', version: cFull }),
                Object.freeze({ brand: 'Google Chrome', version: cFull }),
                Object.freeze({ brand: 'Not-A.Brand', version: '24.0.0.0' })
              ]),
              mobile: isMobile,
              model: clientHintsModel || 'M2010J19CG',
              platform: 'Android',
              platformVersion: androidVer
            });
          }, 'getHighEntropyValues', 1),
          toJSON: makeNative(function() {
            return { brands: brandsList, mobile: isMobile, platform: 'Android' };
          }, 'toJSON', 0)
        };
        Object.defineProperty(Navigator.prototype, 'userAgentData', {
          get: makeNative(function() { return fakeUAData; }, 'get userAgentData', 0),
          configurable: true, enumerable: true
        });
      } else {
        try { delete Navigator.prototype.userAgentData; } catch(e) {}
      }
    } catch(e) {}

    // ====================================================
    // 10. Screen Dimensions & Viewport
    // ====================================================
    try {
      Object.defineProperty(Screen.prototype, 'width', { get: makeNative(function() { return sw; }, 'get width', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'height', { get: makeNative(function() { return sh; }, 'get height', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'availWidth', { get: makeNative(function() { return sw; }, 'get availWidth', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'availHeight', { get: makeNative(function() { return sh; }, 'get availHeight', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'colorDepth', { get: makeNative(function() { return 24; }, 'get colorDepth', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'pixelDepth', { get: makeNative(function() { return 24; }, 'get pixelDepth', 0), configurable: true, enumerable: true });
    } catch(e) {}

    // ====================================================
    // 11. Geolocation & Permissions
    // ====================================================
    try {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition = makeNative(function(success, error, options) {
          if (typeof success === 'function') {
            success({
              coords: {
                latitude: spoofLat,
                longitude: spoofLon,
                accuracy: 35.0,
                altitude: null,
                altitudeAccuracy: null,
                heading: null,
                speed: null
              },
              timestamp: Date.now()
            });
          }
        }, 'getCurrentPosition', 1);
      }
    } catch(e) {}

  } catch(globalErr) {}
})();`;
}

module.exports = {
  generateStealthScript
};
