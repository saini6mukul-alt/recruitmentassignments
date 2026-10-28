package com.mukul.mylivetv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the Home catalog by opening each source page in a hidden WebView and
 * reading its channel list with assets/extract.js. Results are cached, so Home
 * shows the last good list instantly and refreshes in the background.
 */
final class CatalogLoader {

    interface Listener {
        void onCatalogChanged(String json);

        void onCatalogLoading(boolean loading);
    }

    /** Source id (as used by extract.js and home.html) → page that lists its channels. */
    private static final String[][] SOURCES = {
            {"jtv", "https://jjtvxweb.pages.dev/"},
            {"sony", "https://allinonereborn2.online/sony/"},
            {"sf", "https://streamflexsmm.in/Game/"}
    };
    private static final long TIMEOUT_MS = 45_000;
    private static final int MAX_PAYLOAD_CHARS = 2_000_000;

    private final Activity activity;
    private final ViewGroup host;
    private final Listener listener;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, WebView> running = new HashMap<>();
    private final Map<String, Runnable> timeouts = new HashMap<>();
    private String extractJs;

    CatalogLoader(Activity activity, ViewGroup host, Listener listener) {
        this.activity = activity;
        this.host = host;
        this.listener = listener;
        this.prefs = activity.getSharedPreferences("catalog", Activity.MODE_PRIVATE);
    }

    /** {"jtv": {"ts": 123, "items": [...]}, ...} for every source that has loaded at least once. */
    String cachedJson() {
        JSONObject out = new JSONObject();
        for (String[] source : SOURCES) {
            String raw = prefs.getString(source[0], null);
            if (raw == null) continue;
            try {
                out.put(source[0], new JSONObject(raw));
            } catch (JSONException ignored) {
                // Corrupt entry; it is replaced on the next successful load.
            }
        }
        return out.toString();
    }

    void refresh() {
        if (!running.isEmpty()) return;
        if (extractJs == null) extractJs = readAsset("extract.js");
        listener.onCatalogLoading(true);
        for (String[] source : SOURCES) start(source[0], source[1]);
    }

    void cancel() {
        for (WebView web : running.values()) destroy(web);
        running.clear();
        timeouts.clear();
        main.removeCallbacksAndMessages(null);
    }

    private void start(final String id, String url) {
        final WebView web = new WebView(activity);
        MainActivity.configure(web);
        web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setBlockNetworkImage(true);
        web.addJavascriptInterface(new Bridge(), "MLBridge");
        web.setWebViewClient(new MainActivity.BlockingClient() {
            private boolean injected;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // A hidden page must never navigate anywhere else.
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                if (injected || !running.containsKey(id)) return;
                injected = true;
                view.evaluateJavascript(extractJs + "\n;window.__mlExtract('" + id + "');", null);
            }
        });

        // Laid out but invisible: some pages only render their lists once attached.
        web.setVisibility(View.INVISIBLE);
        host.addView(web, 0, new FrameLayout.LayoutParams(1080, 1920));
        running.put(id, web);
        web.loadUrl(url);

        Runnable timeout = new Runnable() {
            @Override
            public void run() {
                finish(id, null);
            }
        };
        timeouts.put(id, timeout);
        main.postDelayed(timeout, TIMEOUT_MS);
    }

    private void finish(String id, String itemsJson) {
        WebView web = running.remove(id);
        if (web == null) return;
        Runnable timeout = timeouts.remove(id);
        if (timeout != null) main.removeCallbacks(timeout);
        destroy(web);

        if (itemsJson != null) {
            try {
                JSONArray items = new JSONArray(itemsJson);
                if (items.length() > 0) {
                    JSONObject entry = new JSONObject();
                    entry.put("ts", System.currentTimeMillis());
                    entry.put("items", items);
                    prefs.edit().putString(id, entry.toString()).apply();
                    listener.onCatalogChanged(cachedJson());
                }
            } catch (JSONException ignored) {
                // Keep the previous cached list for this source.
            }
        }
        if (running.isEmpty()) listener.onCatalogLoading(false);
    }

    private void destroy(WebView web) {
        host.removeView(web);
        web.stopLoading();
        web.destroy();
    }

    private class Bridge {
        @JavascriptInterface
        public void deliver(final String id, final String json) {
            if (json == null || json.length() > MAX_PAYLOAD_CHARS) return;
            main.post(new Runnable() {
                @Override
                public void run() {
                    finish(id, json);
                }
            });
        }
    }

    private String readAsset(String name) {
        try (InputStream in = activity.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new IllegalStateException("Missing asset " + name, e);
        }
    }
}
