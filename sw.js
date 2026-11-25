// Bulletin de visite DPFC : en ligne, toujours la dernière version publiée ; hors ligne, la dernière version téléchargée.
// Le nom du cache change à chaque version : les anciennes copies sont effacées.
const C = 'dpfc-bulletin-2026.11.25.6';
const PAGES = ['./', './index.html', './essai.html', './manifest.webmanifest', './manifest-essai.webmanifest', './icone.svg', './icone-192.png', './icone-512.png', './icone-maskable-512.png', './apple-touch-icon.png', './favicon-32.png', './installer.html'];
self.addEventListener('install', e => { e.waitUntil(caches.open(C).then(c => Promise.all(PAGES.map(p => c.add(p).catch(() => null))))); self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== C).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url); if (e.request.method !== 'GET' || u.origin !== location.origin) return;
  // en ligne : version la plus récente ; hors ligne : copie enregistrée (la version d'essai retombe toujours sur elle-même)
  e.respondWith(fetch(e.request).then(r => { if (r.ok) { const cp = r.clone(); caches.open(C).then(c => c.put(e.request, cp)); } return r; })
    .catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match(/essai/i.test(u.pathname) ? './essai.html' : './index.html'))));
});
