package ci.pnapas.bulletinvisite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/* Application « Bulletin de visite » : ouvre le site de l'application dans une fenêtre dédiée.
   Le site se met à jour seul et reste utilisable sans connexion après la première ouverture.
   Les bulletins Word, PDF et les exports sont enregistrés dans le dossier « Téléchargements ». */
public class MainActivity extends Activity {
    static final String SITE = "https://pnapas-ci.github.io/Bulletin-Visite-DPFC/";
    static final String HOTE = "pnapas-ci.github.io";
    static final int CHOIX_FICHIER = 41;
    private WebView vue;
    private ValueCallback<Uri[]> rappelFichier;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        vue = new WebView(this);
        setContentView(vue);
        WebSettings s = vue.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " DPFCApp/1");
        s.setCacheMode(enLigne() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(vue, false);
        vue.addJavascriptInterface(new Pont(), "AndroidPont");

        vue.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                if (("https".equals(sch) && HOTE.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/Bulletin-Visite-DPFC")) || "file".equals(sch)) return false;
                ouvrirAilleurs(u);
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadUrl("file:///android_asset/hors-ligne.html");
            }
        });
        vue.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> rappel, FileChooserParams p) {
                if (rappelFichier != null) rappelFichier.onReceiveValue(null);
                rappelFichier = rappel;
                try { startActivityForResult(p.createIntent(), CHOIX_FICHIER); }
                catch (ActivityNotFoundException ex) { rappelFichier = null; return false; }
                return true;
            }
        });
        vue.setDownloadListener((url, ua, cd, mime, taille) -> {
            if (url != null && url.startsWith("http")) ouvrirAilleurs(Uri.parse(url));
        });
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        if (etat != null) vue.restoreState(etat); else vue.loadUrl(SITE);
    }

    private boolean enLigne() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) { return true; }
    }

    private void ouvrirAilleurs(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { Toast.makeText(this, "Aucune application pour ouvrir ce lien.", Toast.LENGTH_LONG).show(); }
    }

    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        if (code == CHOIX_FICHIER && rappelFichier != null) {
            rappelFichier.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultat, donnees));
            rappelFichier = null;
            return;
        }
        super.onActivityResult(code, resultat, donnees);
    }

    @Override
    protected void onSaveInstanceState(Bundle etat) { super.onSaveInstanceState(etat); vue.saveState(etat); }

    @Override
    public void onBackPressed() { if (vue.canGoBack()) vue.goBack(); else super.onBackPressed(); }

    /* Enregistrement des fichiers produits par l'application (Word, PDF, CSV, sauvegarde) */
    class Pont {
        @JavascriptInterface
        public void enregistrer(String nom, String type, String base64) {
            final String nomFichier = (nom == null || nom.trim().isEmpty()) ? "bulletin" : nom.replaceAll("[\\\\/:*?\"<>|]", "_");
            final String mime = (type == null || type.isEmpty()) ? "application/octet-stream" : type;
            try {
                byte[] octets = Base64.decode(base64, Base64.DEFAULT);
                Uri uri = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, nomFichier);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("dossier Téléchargements inaccessible");
                    try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(octets); }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, nomFichier);
                    try (FileOutputStream o = new FileOutputStream(f)) { o.write(octets); }
                }
                final Uri ouvrir = uri;
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Enregistré dans Téléchargements : " + nomFichier, Toast.LENGTH_LONG).show();
                    if (ouvrir != null) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(ouvrir, mime);
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(i, "Ouvrir " + nomFichier));
                        } catch (Exception ignore) { }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }
}
