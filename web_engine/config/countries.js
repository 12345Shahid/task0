/**
 * Supported Target Countries for Proxy-Cheap Residential Network
 */
const COUNTRIES = {
  US: {
    code: 'US',
    name: 'United States',
    flag: '🇺🇸',
    timezone: 'America/New_York',
    locale: 'en-US',
    latitude: 40.7128,
    longitude: -74.0060
  },
  GB: {
    code: 'GB',
    name: 'United Kingdom',
    flag: '🇬🇧',
    timezone: 'Europe/London',
    locale: 'en-GB',
    latitude: 51.5074,
    longitude: -0.1278
  },
  AU: {
    code: 'AU',
    name: 'Australia',
    flag: '🇦🇺',
    timezone: 'Australia/Sydney',
    locale: 'en-AU',
    latitude: -33.8688,
    longitude: 151.2093
  },
  CA: {
    code: 'CA',
    name: 'Canada',
    flag: '🇨🇦',
    timezone: 'America/Toronto',
    locale: 'en-CA',
    latitude: 43.6532,
    longitude: -79.3832
  },
  DE: {
    code: 'DE',
    name: 'Germany',
    flag: '🇩🇪',
    timezone: 'Europe/Berlin',
    locale: 'de-DE',
    latitude: 52.5200,
    longitude: 13.4050
  },
  FR: {
    code: 'FR',
    name: 'France',
    flag: '🇫🇷',
    timezone: 'Europe/Paris',
    locale: 'fr-FR',
    latitude: 48.8566,
    longitude: 2.3522
  },
  IT: {
    code: 'IT',
    name: 'Italy',
    flag: '🇮🇹',
    timezone: 'Europe/Rome',
    locale: 'it-IT',
    latitude: 41.9028,
    longitude: 12.4964
  },
  ES: {
    code: 'ES',
    name: 'Spain',
    flag: '🇪🇸',
    timezone: 'Europe/Madrid',
    locale: 'es-ES',
    latitude: 40.4168,
    longitude: -3.7038
  },
  RU: {
    code: 'RU',
    name: 'Russia',
    flag: '🇷🇺',
    timezone: 'Europe/Moscow',
    locale: 'ru-RU',
    latitude: 55.7558,
    longitude: 37.6173
  },
  NL: {
    code: 'NL',
    name: 'Netherlands',
    flag: '🇳🇱',
    timezone: 'Europe/Amsterdam',
    locale: 'nl-NL',
    latitude: 52.3676,
    longitude: 4.9041
  },
  JP: {
    code: 'JP',
    name: 'Japan',
    flag: '🇯🇵',
    timezone: 'Asia/Tokyo',
    locale: 'ja-JP',
    latitude: 35.6762,
    longitude: 139.6503
  }
};

function getCountry(code) {
  if (!code) return COUNTRIES.US;
  const upper = code.toUpperCase();
  return COUNTRIES[upper] || COUNTRIES.US;
}

module.exports = {
  COUNTRIES,
  getCountry
};
