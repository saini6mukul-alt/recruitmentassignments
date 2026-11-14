package app.lehar.music;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;

/**
 * Background-safe WebView (carried over from NAUX v1.4).
 * Chromium pauses video media when its window becomes hidden. Lehar plays music through the
 * YouTube video player, so we always report the window as VISIBLE to keep playback alive.
 */
public class BgWebView extends WebView {
    public BgWebView(Context context) {
        super(context);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(View.VISIBLE);
    }
}
