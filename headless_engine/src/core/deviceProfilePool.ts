export interface DeviceProfile {
  id: string;
  name: string;
  userAgent: string;
  viewport: {
    width: number;
    height: number;
    deviceScaleFactor: number;
    isMobile: boolean;
    hasTouch: boolean;
    isLandscape: boolean;
  };
  clientHints: {
    brands: Array<{ brand: string; version: string }>;
    mobile: boolean;
    platform: string;
    platformVersion: string;
    architecture: string;
    model: string;
  };
  webgl: {
    vendor: string;
    renderer: string;
  };
  hardwareConcurrency: number;
  deviceMemory: number;
  timezone: string;
}

export const DEVICE_PROFILES: DeviceProfile[] = [
  {
    id: "pixel_8_pro",
    name: "Google Pixel 8 Pro",
    userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36",
    viewport: {
      width: 412,
      height: 915,
      deviceScaleFactor: 2.625,
      isMobile: true,
      hasTouch: true,
      isLandscape: false,
    },
    clientHints: {
      brands: [
        { brand: "Chromium", version: "128" },
        { brand: "Google Chrome", version: "128" },
        { brand: "Not=A?Brand", version: "24" },
      ],
      mobile: true,
      platform: "Android",
      platformVersion: "14.0.0",
      architecture: "arm64",
      model: "Pixel 8 Pro",
    },
    webgl: {
      vendor: "ARM",
      renderer: "Mali-G715 Immortalis",
    },
    hardwareConcurrency: 8,
    deviceMemory: 8,
    timezone: "America/Los_Angeles",
  },
  {
    id: "galaxy_s24_ultra",
    name: "Samsung Galaxy S24 Ultra",
    userAgent: "Mozilla/5.0 (Linux; Android 14; SM-S928U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36",
    viewport: {
      width: 412,
      height: 915,
      deviceScaleFactor: 3.0,
      isMobile: true,
      hasTouch: true,
      isLandscape: false,
    },
    clientHints: {
      brands: [
        { brand: "Chromium", version: "128" },
        { brand: "Google Chrome", version: "128" },
        { brand: "Not=A?Brand", version: "24" },
      ],
      mobile: true,
      platform: "Android",
      platformVersion: "14.0.0",
      architecture: "arm64",
      model: "SM-S928U",
    },
    webgl: {
      vendor: "Qualcomm",
      renderer: "Adreno (TM) 750",
    },
    hardwareConcurrency: 8,
    deviceMemory: 12,
    timezone: "America/Los_Angeles",
  },
  {
    id: "oneplus_12",
    name: "OnePlus 12",
    userAgent: "Mozilla/5.0 (Linux; Android 14; CPH2583) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.88 Mobile Safari/537.36",
    viewport: {
      width: 412,
      height: 919,
      deviceScaleFactor: 2.75,
      isMobile: true,
      hasTouch: true,
      isLandscape: false,
    },
    clientHints: {
      brands: [
        { brand: "Chromium", version: "128" },
        { brand: "Google Chrome", version: "128" },
        { brand: "Not=A?Brand", version: "24" },
      ],
      mobile: true,
      platform: "Android",
      platformVersion: "14.0.0",
      architecture: "arm64",
      model: "CPH2583",
    },
    webgl: {
      vendor: "Qualcomm",
      renderer: "Adreno (TM) 750",
    },
    hardwareConcurrency: 8,
    deviceMemory: 12,
    timezone: "America/Los_Angeles",
  },
  {
    id: "mac_desktop_chrome",
    name: "MacBook Pro Desktop Chrome",
    userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    viewport: {
      width: 1440,
      height: 900,
      deviceScaleFactor: 2.0,
      isMobile: false,
      hasTouch: false,
      isLandscape: true,
    },
    clientHints: {
      brands: [
        { brand: "Chromium", version: "128" },
        { brand: "Google Chrome", version: "128" },
        { brand: "Not=A?Brand", version: "24" },
      ],
      mobile: false,
      platform: "macOS",
      platformVersion: "14.5.0",
      architecture: "arm64",
      model: "",
    },
    webgl: {
      vendor: "Apple",
      renderer: "Apple M2 Pro",
    },
    hardwareConcurrency: 10,
    deviceMemory: 16,
    timezone: "America/Los_Angeles",
  },
  {
    id: "windows_desktop_chrome",
    name: "Windows 11 Desktop Chrome",
    userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    viewport: {
      width: 1920,
      height: 1080,
      deviceScaleFactor: 1.0,
      isMobile: false,
      hasTouch: false,
      isLandscape: true,
    },
    clientHints: {
      brands: [
        { brand: "Chromium", version: "128" },
        { brand: "Google Chrome", version: "128" },
        { brand: "Not=A?Brand", version: "24" },
      ],
      mobile: false,
      platform: "Windows",
      platformVersion: "15.0.0",
      architecture: "x86",
      model: "",
    },
    webgl: {
      vendor: "Google Inc. (NVIDIA)",
      renderer: "ANGLE (NVIDIA, NVIDIA GeForce RTX 3070 Direct3D11 vs_5_0 ps_5_0, D3D11)",
    },
    hardwareConcurrency: 12,
    deviceMemory: 16,
    timezone: "America/New_York",
  },
];

export function getRandomDeviceProfile(): DeviceProfile {
  const index = Math.floor(Math.random() * DEVICE_PROFILES.length);
  return DEVICE_PROFILES[index];
}
