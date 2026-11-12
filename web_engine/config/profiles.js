/**
 * Curated Anti-Detect Hardware & Device Profiles
 */
const PROFILES = {
  poco_m3: {
    id: 'poco_m3',
    name: 'Xiaomi POCO M3',
    isMobile: true,
    hasTouch: true,
    platform: 'Linux armv8l',
    vendor: 'Google Inc.',
    deviceMemory: 4,
    hardwareConcurrency: 8,
    viewport: {
      width: 412,
      height: 915,
      deviceScaleFactor: 2.625,
      isMobile: true,
      hasTouch: true,
      isLandscape: false
    },
    screen: {
      width: 412,
      height: 915,
      availWidth: 412,
      availHeight: 915,
      colorDepth: 24,
      pixelDepth: 24
    },
    webgl: {
      vendor: 'Qualcomm',
      renderer: 'Adreno (TM) 610'
    },
    clientHints: {
      brands: [
        { brand: 'Chromium', version: '124' },
        { brand: 'Google Chrome', version: '124' },
        { brand: 'Not-A.Brand', version: '99' }
      ],
      mobile: true,
      platform: 'Android',
      architecture: 'arm',
      bitness: '64',
      model: 'M2010J19CG',
      platformVersion: '12.0.0'
    },
    userAgent: 'Mozilla/5.0 (Linux; Android 12; M2010J19CG) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36'
  },

  samsung_s24_ultra: {
    id: 'samsung_s24_ultra',
    name: 'Samsung Galaxy S24 Ultra',
    isMobile: true,
    hasTouch: true,
    platform: 'Linux armv8l',
    vendor: 'Google Inc.',
    deviceMemory: 12,
    hardwareConcurrency: 8,
    viewport: {
      width: 412,
      height: 915,
      deviceScaleFactor: 3.5,
      isMobile: true,
      hasTouch: true,
      isLandscape: false
    },
    screen: {
      width: 412,
      height: 915,
      availWidth: 412,
      availHeight: 915,
      colorDepth: 24,
      pixelDepth: 24
    },
    webgl: {
      vendor: 'Qualcomm',
      renderer: 'Adreno (TM) 750'
    },
    clientHints: {
      brands: [
        { brand: 'Chromium', version: '124' },
        { brand: 'Google Chrome', version: '124' },
        { brand: 'Not-A.Brand', version: '99' }
      ],
      mobile: true,
      platform: 'Android',
      architecture: 'arm',
      bitness: '64',
      model: 'SM-S928B',
      platformVersion: '14.0.0'
    },
    userAgent: 'Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36'
  },

  oneplus_nord_n30: {
    id: 'oneplus_nord_n30',
    name: 'OnePlus Nord N30 5G',
    isMobile: true,
    hasTouch: true,
    platform: 'Linux armv8l',
    vendor: 'Google Inc.',
    deviceMemory: 8,
    hardwareConcurrency: 8,
    viewport: {
      width: 412,
      height: 915,
      deviceScaleFactor: 2.625,
      isMobile: true,
      hasTouch: true,
      isLandscape: false
    },
    screen: {
      width: 412,
      height: 915,
      availWidth: 412,
      availHeight: 915,
      colorDepth: 24,
      pixelDepth: 24
    },
    webgl: {
      vendor: 'Qualcomm',
      renderer: 'Adreno (TM) 619'
    },
    clientHints: {
      brands: [
        { brand: 'Chromium', version: '124' },
        { brand: 'Google Chrome', version: '124' },
        { brand: 'Not-A.Brand', version: '99' }
      ],
      mobile: true,
      platform: 'Android',
      architecture: 'arm',
      bitness: '64',
      model: 'CPH2513',
      platformVersion: '13.0.0'
    },
    userAgent: 'Mozilla/5.0 (Linux; Android 13; CPH2513) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36'
  },

  iphone_15_pro_max: {
    id: 'iphone_15_pro_max',
    name: 'Apple iPhone 15 Pro Max',
    isMobile: true,
    hasTouch: true,
    platform: 'iPhone',
    vendor: 'Apple Computer, Inc.',
    deviceMemory: 8,
    hardwareConcurrency: 6,
    viewport: {
      width: 430,
      height: 932,
      deviceScaleFactor: 3.0,
      isMobile: true,
      hasTouch: true,
      isLandscape: false
    },
    screen: {
      width: 430,
      height: 932,
      availWidth: 430,
      availHeight: 932,
      colorDepth: 32,
      pixelDepth: 32
    },
    webgl: {
      vendor: 'Apple Inc.',
      renderer: 'Apple GPU'
    },
    clientHints: null, // iOS Safari has no navigator.userAgentData
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1'
  },

  windows_11_pc: {
    id: 'windows_11_pc',
    name: 'Windows 11 Desktop (RTX 4070)',
    isMobile: false,
    hasTouch: false,
    platform: 'Win32',
    vendor: 'Google Inc.',
    deviceMemory: 16,
    hardwareConcurrency: 16,
    viewport: {
      width: 1920,
      height: 1080,
      deviceScaleFactor: 1.0,
      isMobile: false,
      hasTouch: false,
      isLandscape: true
    },
    screen: {
      width: 1920,
      height: 1080,
      availWidth: 1920,
      availHeight: 1040,
      colorDepth: 24,
      pixelDepth: 24
    },
    webgl: {
      vendor: 'Google Inc. (NVIDIA)',
      renderer: 'ANGLE (NVIDIA, NVIDIA GeForce RTX 4070 Direct3D11 vs_5_0 ps_5_0, D3D11)'
    },
    clientHints: {
      brands: [
        { brand: 'Chromium', version: '124' },
        { brand: 'Google Chrome', version: '124' },
        { brand: 'Not-A.Brand', version: '99' }
      ],
      mobile: false,
      platform: 'Windows',
      architecture: 'x86',
      bitness: '64',
      model: '',
      platformVersion: '15.0.0'
    },
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
  }
};

function getProfile(id) {
  if (!id) return PROFILES.poco_m3;
  return PROFILES[id] || PROFILES.poco_m3;
}

module.exports = {
  PROFILES,
  getProfile
};
