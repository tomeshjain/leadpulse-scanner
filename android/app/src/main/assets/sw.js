// LeadPulse Service Worker for Android PWA offline support
const CACHE_NAME = 'leadpulse-cache-v2';
const ASSETS = [
  './',
  './index.html',
  './css/app.css',
  './js/app.js',
  './js/storage.js',
  './js/gemini.js',
  './js/camera.js',
  './js/vcard.js',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-maskable-192.png',
  './icon-maskable-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  // Let external API calls to Google Gemini pass through
  if (event.request.url.includes('generativelanguage.googleapis.com')) {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((response) => {
      return response || fetch(event.request).catch(() => caches.match('./index.html'));
    })
  );
});
