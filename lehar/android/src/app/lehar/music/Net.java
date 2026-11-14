package app.lehar.music;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

/** Background HTTP for the UI (no CORS on the native side). */
public final class Net {
    public interface Callback { void done(int status, String body); }

    public static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private Net() {}

    public static void request(String url, String method, String headersJson, String body, Callback cb) {
        POOL.execute(() -> {
            int status = 0;
            String text = "";
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setRequestMethod(method == null || method.isEmpty() ? "GET" : method);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept-Language", "en-IN,en;q=0.9,hi;q=0.8");
                if (url.startsWith("https://music.youtube.com/")) {
                    c.setRequestProperty("Origin", "https://music.youtube.com");
                    c.setRequestProperty("Referer", "https://music.youtube.com/");
                }
                if (headersJson != null && !headersJson.isEmpty()) {
                    JSONObject h = new JSONObject(headersJson);
                    for (Iterator<String> it = h.keys(); it.hasNext(); ) {
                        String k = it.next();
                        c.setRequestProperty(k, h.getString(k));
                    }
                }
                if (body != null && !body.isEmpty()) {
                    c.setDoOutput(true);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
                }
                status = c.getResponseCode();
                InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                text = in == null ? "" : readAll(in);
            } catch (Exception e) {
                status = 0;
                text = "";
            } finally {
                if (c != null) c.disconnect();
            }
            cb.done(status, text);
        });
    }

    public static byte[] download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", UA);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
