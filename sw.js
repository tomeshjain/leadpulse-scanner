// Wispr Flow Service Worker for Low-Latency PWA Offline Execution
const CACHE_NAME = 'wisprflow-cache-v1';
const ASSETS = [
  './',
  './index.html',
  './css/app.css',
  './js/app.js',
  './js/storage.js',
  './js/speech.js',
  './js/flow-ai.js',
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
  // Pass Google Gemini AI API requests straight to network
  if (event.request.url.includes('generativelanguage.googleapis.com')) {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((response) => {
      return response || fetch(event.request).catch(() => caches.match('./index.html'));
    })
  );
});
