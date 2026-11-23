package com.nini.moneyplus;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String LOCAL_URL = "file:///android_asset/index.html";
    private static final String REMOTE_URL = "https://84arie.github.io/moneyplus-nini/";
    private static final String PREFS = "moneyplus_bridge";
    private static final String KEY_DB = "bakiSelamatV6FullTest";
    private static final String KEY_LANG = "bsLangV6";

    private WebView webView;
    private SharedPreferences prefs;
    private boolean migrationStage = false;
    private boolean loadingFallback = false;
    private boolean remoteReady = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        webView = new WebView(this);
        webView.setVisibility(View.INVISIBLE);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (migrationStage && url.startsWith("file:///android_asset/")) {
                    captureStorageAndOpenRemote();
                    return;
                }

                if (url.startsWith(REMOTE_URL)) {
                    restoreNativeBackupIfNeeded();
                    return;
                }

                if (loadingFallback && url.startsWith("file:///android_asset/")) {
                    webView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !loadingFallback) {
                    loadingFallback = true;
                    remoteReady = false;
                    webView.loadUrl(LOCAL_URL);
                }
            }
        });

        boolean migrated = prefs.getBoolean("migration_done", false);

        if (!migrated) {
            migrationStage = true;
            webView.loadUrl(LOCAL_URL);
        } else {
            openRemote();
        }
    }

    private void captureStorageAndOpenRemote() {
        String js =
                "(function(){" +
                "return JSON.stringify({" +
                "db:localStorage.getItem('" + KEY_DB + "')||''," +
                "lang:localStorage.getItem('" + KEY_LANG + "')||''" +
                "});" +
                "})()";

        webView.evaluateJavascript(js, value -> {
            saveEvalResult(value);
            migrationStage = false;
            openRemote();
        });
    }

    private void saveEvalResult(String value) {
        try {
            String decoded = new JSONArray("[" + value + "]").getString(0);
            JSONObject o = new JSONObject(decoded);

            SharedPreferences.Editor e = prefs.edit();
            String db = o.optString("db", "");
            String lang = o.optString("lang", "");

            if (!db.isEmpty()) e.putString(KEY_DB, db);
            if (!lang.isEmpty()) e.putString(KEY_LANG, lang);
            e.apply();
        } catch (Exception ignored) {
        }
    }

    private void openRemote() {
        loadingFallback = false;
        remoteReady = false;
        String url = REMOTE_URL + "?app=1&v=" + System.currentTimeMillis();
        webView.loadUrl(url);
    }

    private void restoreNativeBackupIfNeeded() {
        String db = prefs.getString(KEY_DB, "");
        String lang = prefs.getString(KEY_LANG, "");

        String dbQuoted = JSONObject.quote(db);
        String langQuoted = JSONObject.quote(lang);

        String js =
                "(function(){" +
                "var changed=false;" +
                "var db=" + dbQuoted + ";" +
                "var lang=" + langQuoted + ";" +
                "if(db && !localStorage.getItem('" + KEY_DB + "')){" +
                    "localStorage.setItem('" + KEY_DB + "',db);changed=true;" +
                "}" +
                "if(lang && !localStorage.getItem('" + KEY_LANG + "')){" +
                    "localStorage.setItem('" + KEY_LANG + "',lang);changed=true;" +
                "}" +
                "return changed;" +
                "})()";

        webView.evaluateJavascript(js, changed -> {
            prefs.edit().putBoolean("migration_done", true).apply();

            if ("true".equals(changed)) {
                webView.reload();
            } else {
                remoteReady = true;
                webView.setVisibility(View.VISIBLE);
                captureCurrentRemoteStorage();
            }
        });
    }

    private void captureCurrentRemoteStorage() {
        if (!remoteReady) return;

        String js =
                "(function(){" +
                "return JSON.stringify({" +
                "db:localStorage.getItem('" + KEY_DB + "')||''," +
                "lang:localStorage.getItem('" + KEY_LANG + "')||''" +
                "});" +
                "})()";

        webView.evaluateJavascript(js, this::saveEvalResult);
    }

    @Override
    protected void onPause() {
        captureCurrentRemoteStorage();
        super.onPause();
    }

    @Override
    protected void onStop() {
        captureCurrentRemoteStorage();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
