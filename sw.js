// Bulletin de visite DPFC : les pages restent disponibles sans connexion (dernière version téléchargée).
const C = 'dpfc-bulletin-v2';
const PAGES = ['./', './index.html', './essai.html', './manifest.webmanifest', './manifest-essai.webmanifest', './icone.svg'];
self.addEventListener('install', e => { e.waitUntil(caches.open(C).then(c => c.addAll(PAGES))); self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== C).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url); if (e.request.method !== 'GET' || u.origin !== location.origin) return;
  // en ligne : version la plus récente ; hors ligne : copie enregistrée (la version d'essai retombe toujours sur elle-même)
  e.respondWith(fetch(e.request).then(r => { if (r.ok) { const cp = r.clone(); caches.open(C).then(c => c.put(e.request, cp)); } return r; })
    .catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match(/essai/i.test(u.pathname) ? './essai.html' : './index.html'))));
});
