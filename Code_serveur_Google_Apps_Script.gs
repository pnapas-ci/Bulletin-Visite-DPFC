/**
 * Serveur de la plateforme « Bulletin de visite DPFC provisoire » (Google Apps Script).
 * Stocke les documents CHIFFRÉS envoyés par l’application (une ligne par document).
 * Aucune donnée lisible n’arrive ici : noms, notes et contacts sont chiffrés sur les téléphones.
 * Installation : voir le guide d’utilisation (chapitre « Serveur de la plateforme »).
 */
var SHEET = 'documents', TRASH = 'corbeille', LOG = 'journal';
function sheet_(name, head) {
  var ss = SpreadsheetApp.getActive(); var sh = ss.getSheetByName(name);
  if (!sh) { sh = ss.insertSheet(name); sh.appendRow(head); sh.setFrozenRows(1); }
  return sh;
}
function docs_() { return sheet_(SHEET, ['chemin', 'collection', 'id', 'contenu', 'maj']); }
function out_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
function findRow_(sh, path) {
  var last = sh.getLastRow(); if (last < 2) return 0;
  var c = sh.getRange(2, 1, last - 1, 1).createTextFinder(path).matchEntireCell(true).findNext();
  return c ? c.getRow() : 0;
}
function doGet() { return out_({ ok: true, service: 'Bulletin de visite DPFC provisoire', heure: new Date().toISOString() }); }
function doPost(e) {
  try {
    var b = JSON.parse(e.postData.contents);
    var sh = docs_();
    if (b.op === 'ping') return out_({ ok: true });
    if (b.op === 'get') { var r = findRow_(sh, b.path); return out_({ ok: true, doc: r ? JSON.parse(sh.getRange(r, 4).getValue()) : null }); }
    if (b.op === 'query') {
      var last = sh.getLastRow(); if (last < 2) return out_({ ok: true, docs: [] });
      var re = new RegExp('^' + String(b.coll).replace(/[.+?^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '[^/]+') + '$');
      var vals = sh.getRange(2, 2, last - 1, 3).getValues(); var res = [];
      for (var k = 0; k < vals.length; k++) {
        if (!re.test(vals[k][0])) continue;
        var d = JSON.parse(vals[k][2]); var ok = true;
        (b.where || []).forEach(function (w) { if (d[w[0]] !== w[1]) ok = false; });
        if (ok) res.push({ id: vals[k][1], coll: vals[k][0], data: d });
      }
      return out_({ ok: true, docs: res });
    }
    if (b.op === 'set' || b.op === 'del') {
      var lock = LockService.getScriptLock(); lock.waitLock(25000);
      try {
        var r2 = findRow_(sh, b.path); var parts = String(b.path).split('/'); var id = parts.pop(); var coll = parts.join('/');
        var now = new Date().toISOString();
        if (b.op === 'set') {
          var json = JSON.stringify(b.data); if (json.length > 49000) return out_({ ok: false, code: 'invalid_argument', error: 'document trop grand' });
          if (r2) sh.getRange(r2, 4, 1, 2).setValues([[json, now]]); else sh.appendRow([b.path, coll, id, json, now]);
          sheet_(LOG, ['heure', 'operation', 'chemin', 'contenu']).appendRow([now, 'set', b.path, json]);
        } else if (r2) { // jamais d’effacement définitif : le document part dans la corbeille
          var row = sh.getRange(r2, 1, 1, 5).getValues()[0];
          sheet_(TRASH, ['chemin', 'collection', 'id', 'contenu', 'maj', 'supprime_le']).appendRow(row.concat([now]));
          sh.deleteRow(r2); sheet_(LOG, ['heure', 'operation', 'chemin', 'contenu']).appendRow([now, 'del', b.path, '']);
        }
      } finally { lock.releaseLock(); }
      return out_({ ok: true });
    }
    return out_({ ok: false, code: 'invalid_argument', error: 'opération inconnue' });
  } catch (err) { return out_({ ok: false, code: 'unavailable', error: String(err) }); }
}
