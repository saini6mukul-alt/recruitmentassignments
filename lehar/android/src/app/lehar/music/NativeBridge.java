package app.lehar.music;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** window.LeharNative — the JS ↔ Android contract (spec §3). */
public class NativeBridge {
    private final MainActivity activity;
    private final WebView web;

    NativeBridge(MainActivity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @JavascriptInterface
    public void request(String id, String url, String method, String headersJson, String body) {
        Net.request(url, method, headersJson, body, (status, text) ->
                web.post(() -> web.evaluateJavascript(
                        "window.__lehar&&window.__lehar.resolve(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(text) + ")", null)));
    }

    @JavascriptInterface
    public void setNowPlaying(String json) {
        try {
            PlaybackService.nowPlaying(activity.getApplicationContext(), new JSONObject(json));
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public void setPosition(double ms) {
        PlaybackService.position((long) ms);
    }

    @JavascriptInterface
    public void stopService() {
        PlaybackService.stop(activity.getApplicationContext());
    }

    @JavascriptInterface
    public void toast(String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    /** Writes a backup file to Downloads. Returns true on success. */
    @JavascriptInterface
    public boolean saveFile(String name, String content) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream os = activity.getContentResolver().openOutputStream(uri)) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                java.io.File dir = activity.getExternalFilesDir(null);
                try (OutputStream os = new java.io.FileOutputStream(new java.io.File(dir, name))) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String version() {
        return "1.0";
    }
}
