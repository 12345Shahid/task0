/**
 * AuraBrowser Stealth Anti-Detect Fingerprint Injector (Node.js / CDP Port)
 * 
 * Ports the proven 100% BrowserScan & Pixelscan All-Green injection
 * from FingerprintManager.java for Web & Cloud-streamed Chromium engines.
 */

function generateStealthScript(profile, country, noiseSeed = Math.random()) {
  const tz = country.timezone || 'America/New_York';
  const lang = country.locale || 'en-US';
  const sw = profile.screen.width;
  const sh = profile.screen.height;
  const webglVendor = profile.webgl.vendor;
  const webglRenderer = profile.webgl.renderer;
  const spoofPlatform = profile.platform;
  const spoofVendor = profile.vendor;
  const isIos = profile.id.includes('iphone') || profile.id.includes('ipad');
  const profileName = profile.name;
  const clientHintsModel = profile.clientHints ? profile.clientHints.model : '';
  const spoofHwConcurrency = profile.hardwareConcurrency || 8;
  const spoofDevMemory = profile.deviceMemory || 8;
  const cMajor = '124';
  const cFull = '124.0.6367.113';
  const androidVer = profile.clientHints ? profile.clientHints.platformVersion : '14.0.0';
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

    // 1. Remove automation / navigator.webdriver flags
    try {
      Object.defineProperty(Navigator.prototype, 'webdriver', {
        get: makeNative(function() { return undefined; }, 'get webdriver', 0),
        configurable: true,
        enumerable: true
      });
      delete window.cdc_adoQpoasnfa76pfcZLmcfl_;
    } catch(e) {}

    // 2. Hardware Concurrency & Device Memory
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

    // 3. Platform & Vendor
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

    // 4. Languages
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

    // 5. Mathematical Timezone Consistency (Pixelscan All-Green)
    try {
      var OrigDateTimeFormat = Intl.DateTimeFormat;
      var WrappedDateTimeFormat = function(locales, options) {
        var opts = Object.assign({}, options);
        if (!opts.timeZone) {
          opts.timeZone = spoofTz;
        }
        return new OrigDateTimeFormat(locales, opts);
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
          return 300; // default -05:00
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

      // Align getHours, getMinutes to match toTimeString exactly
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
          return 'Mon Nov 09 2026';
        }
      }, 'toDateString', 0);

      Date.prototype.toString = makeNative(function toString() {
        return this.toDateString() + ' ' + this.toTimeString();
      }, 'toString', 0);
    } catch(e) {}

    // 6. WebGL Unmasked Vendor & Renderer
    try {
      function patchGL(proto) {
        if (!proto) return;
        var origGetParameter = proto.getParameter;
        proto.getParameter = makeNative(function getParameter(param) {
          if (param === 37445) return webglVendor; // UNMASKED_VENDOR_WEBGL
          if (param === 37446) return webglRenderer; // UNMASKED_RENDERER_WEBGL
          return origGetParameter.apply(this, arguments);
        }, 'getParameter', 1);
      }
      if (typeof WebGLRenderingContext !== 'undefined') patchGL(WebGLRenderingContext.prototype);
      if (typeof WebGL2RenderingContext !== 'undefined') patchGL(WebGL2RenderingContext.prototype);
    } catch(e) {}

    // 7. Chrome Client Hints (navigator.userAgentData)
    try {
      if (!isIos) {
        var brandsList = Object.freeze([
          Object.freeze({ brand: 'Chromium', version: cMajor }),
          Object.freeze({ brand: 'Google Chrome', version: cMajor }),
          Object.freeze({ brand: 'Not-A.Brand', version: '99' })
        ]);
        var fakeUAData = {
          brands: brandsList,
          mobile: true,
          platform: 'Android',
          getHighEntropyValues: makeNative(function(hints) {
            return Promise.resolve({
              architecture: 'arm64',
              bitness: '64',
              brands: brandsList,
              fullVersionList: Object.freeze([
                Object.freeze({ brand: 'Chromium', version: cFull }),
                Object.freeze({ brand: 'Google Chrome', version: cFull }),
                Object.freeze({ brand: 'Not-A.Brand', version: '99.0.0.0' })
              ]),
              mobile: true,
              model: clientHintsModel || 'M2010J19CG',
              platform: 'Android',
              platformVersion: androidVer
            });
          }, 'getHighEntropyValues', 1),
          toJSON: makeNative(function() {
            return { brands: brandsList, mobile: true, platform: 'Android' };
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

    // 8. Screen Dimensions & Viewport
    try {
      Object.defineProperty(Screen.prototype, 'width', { get: makeNative(function() { return sw; }, 'get width', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'height', { get: makeNative(function() { return sh; }, 'get height', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'availWidth', { get: makeNative(function() { return sw; }, 'get availWidth', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'availHeight', { get: makeNative(function() { return sh; }, 'get availHeight', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'colorDepth', { get: makeNative(function() { return 24; }, 'get colorDepth', 0), configurable: true, enumerable: true });
      Object.defineProperty(Screen.prototype, 'pixelDepth', { get: makeNative(function() { return 24; }, 'get pixelDepth', 0), configurable: true, enumerable: true });
    } catch(e) {}

    // 9. Geolocation & Permissions
    try {
      if (navigator.geolocation) {
        var origGetCurrentPosition = navigator.geolocation.getCurrentPosition;
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
