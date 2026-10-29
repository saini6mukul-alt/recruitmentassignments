package com.mukul.mylivetv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Tabs: Home (categories + recommendations built from both sites), and the two
 * sites themselves. Channels opened from Home play in a separate player screen.
 * Every WebView goes through the same ad blocking and pop-up/redirect rules.
 */
public class MainActivity extends Activity implements CatalogLoader.Listener {

    private static final String HOME_URL = "file:///android_asset/home.html";
    private static final String[] TITLES = {"Home", "Live TV Hub", "StreamFlex", "Movies"};
    private static final String[] URLS = {
            HOME_URL,
            "https://allinonelivetv.pages.dev/",
            "https://streamflexsmm.in/Game/",
            "https://nxsha.space/"
    };

    /** Pop-up windows are only allowed to load when they point at one of these (or their subdomains). */
    private static final String[] CONTENT_DOMAINS = {
            "pages.dev", "allinonereborn2.online", "streamflexsmm.in", "nxsha.space", "jio.com", "sonyliv.com",
            "fancode.com", "youtube.com", "t.me", "telegram.me", "whatsapp.com"
    };

    private static final int COLOR_BG = Color.parseColor("#0D0F15");
    private static final int COLOR_BAR = Color.parseColor("#151821");
    private static final int COLOR_ACTIVE = Color.parseColor("#00E5FF");
    private static final int COLOR_IDLE = Color.parseColor("#8A93A6");

    private final WebView[] tabViews = new WebView[URLS.length];
    private final TextView[] tabs = new TextView[URLS.length];
    /** Site tabs load the first time they are opened, not at app start. */
    private final boolean[] tabLoaded = new boolean[URLS.length];
    private int current = 0;

    private FrameLayout root;
    private LinearLayout chrome;
    private ProgressBar progress;

    private LinearLayout player;
    private WebView playerView;
    private TextView playerTitle;

    private CatalogLoader catalog;
    private TextView playerBack;
    private boolean isTv;
    private String tvNavJs;
    private String tvCoverJs;
    private String siteFixJs;
    private LinearLayout tabBar;
    private LinearLayout playerBar;
    private boolean coverOn;

    // Fullscreen video state
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int savedOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(false);

        isTv = detectTv();
        tvNavJs = CatalogLoader.readAsset(this, "tvnav.js");
        siteFixJs = CatalogLoader.readAsset(this, "sitefix.js");
        if (isTv) tvCoverJs = CatalogLoader.readAsset(this, "tvcover.js");

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        FrameLayout pages = new FrameLayout(this);
        for (int i = 0; i < URLS.length; i++) {
            tabViews[i] = createWebView(true);
            pages.addView(tabViews[i], matchParent());
        }
        tabViews[0].addJavascriptInterface(new HomeBridge(), "App");
        // On a TV the tab bar sits on top, where "up" from the page naturally leads.
        LinearLayout.LayoutParams barParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        tabBar = createTabBar();
        if (isTv) chrome.addView(tabBar, barParams);
        chrome.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (!isTv) chrome.addView(tabBar, barParams);
        root.addView(chrome, matchParent());

        player = createPlayer();
        player.setVisibility(View.GONE);
        root.addView(player, matchParent());

        // Thin loading bar drawn over whichever screen is showing.
        progress = createProgressBar();
        root.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));

        setContentView(root);

        catalog = new CatalogLoader(this, root, this);

        if (savedInstanceState != null) current = savedInstanceState.getInt("current", 0);
        for (int i = 1; i < URLS.length; i++) {
            Bundle state = savedInstanceState == null ? null : savedInstanceState.getBundle("web" + i);
            if (state != null && !state.isEmpty() && tabViews[i].restoreState(state) != null) tabLoaded[i] = true;
        }
        tabViews[0].loadUrl(URLS[0]);
        tabLoaded[0] = true;
        selectTab(current);
        if (isTv) tabViews[current].requestFocus();
        catalog.refreshIfStale();
    }

    private boolean detectTv() {
        UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION)
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    // ------------------------------------------------------------------ WebViews

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(final boolean showsProgress) {
        final WebView web = new WebView(this);
        web.setBackgroundColor(COLOR_BG);
        configure(web);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new TvBridge(), "MLTv");
        web.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // Coming back from the tab bar: put the remote highlight back on the page.
                if (hasFocus) web.evaluateJavascript("window.__mlNav && __mlNav.focusIn()", null);
            }
        });

        web.setWebViewClient(new BlockingClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (showsProgress && isShowing(view)) progress.setVisibility(View.VISIBLE);
                if (coverOn && isShowing(view)) setCoverUi(false); // a new page starts uncovered
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(tvNavJs, null);
                if (!url.startsWith("file:")) view.evaluateJavascript(siteFixJs, null);
                if (tvCoverJs != null && !url.startsWith("file:")) view.evaluateJavascript(tvCoverJs, null);
                if (showsProgress && isShowing(view)) progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage(view, request.getUrl().toString());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (isShowing(view)) {
                    progress.setProgress(newProgress);
                    progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Pop-unders open new windows without a tap; drop those outright. For a tapped
                // link that wants a new window, peek at its URL and open it in place if it is content.
                if (!isUserGesture) return false;
                final WebView parent = view;
                WebView probe = new WebView(view.getContext());
                probe.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if (isContentDomain(uri) && !AdBlocker.isBlocked(uri)) parent.loadUrl(uri.toString());
                        v.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(probe);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
        return web;
    }

    @SuppressLint("SetJavaScriptEnabled")
    static void configure(WebView web) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);
    }

    /** Ad blocking + redirect rules shared by every WebView in the app. */
    static class BlockingClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return AdBlocker.isBlocked(request.getUrl()) ? AdBlocker.emptyResponse() : null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(view, request.getUrl(), request.hasGesture());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(AdBlocker.HIDE_AD_SLOTS_JS, null);
        }
    }

    /**
     * Returns true when the navigation is handled here (blocked or sent to another app).
     * Ad hosts and app/intent redirects (the ones that used to throw the user into Chrome)
     * are blocked; only a few chat/store links the user tapped open outside the app.
     */
    static boolean handleNavigation(WebView view, Uri uri, boolean userTapped) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            if (AdBlocker.isBlocked(uri)) return true;
            if (isExternalAppLink(uri) && userTapped) {
                openExternally(view, uri);
                return true;
            }
            return false;
        }
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")
                || scheme.equals("javascript") || scheme.equals("file")) {
            return false;
        }
        if (userTapped && (scheme.equals("tg") || scheme.equals("whatsapp") || scheme.equals("mailto")
                || scheme.equals("tel"))) {
            openExternally(view, uri);
        }
        // intent:, market:, googlechrome: and the rest are ad redirects: swallow them.
        return true;
    }

    private static boolean isExternalAppLink(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("t.me") || host.equals("telegram.me") || host.endsWith("whatsapp.com")
                || host.endsWith("instagram.com") || host.endsWith("facebook.com")
                || host.endsWith("youtube.com") || host.equals("youtu.be");
    }

    private static void openExternally(WebView view, Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            view.getContext().startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Nothing installed to handle it; stay put.
        }
    }

    private static boolean isContentDomain(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        for (String d : CONTENT_DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private boolean isShowing(WebView view) {
        if (player.getVisibility() == View.VISIBLE) return view == playerView;
        return view == tabViews[current];
    }

    private void showOfflinePage(WebView view, String failedUrl) {
        if (failedUrl.startsWith("file:")) return;
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#0D0F15;color:#E8EDF2;font-family:sans-serif;text-align:center'>"
                + "<div><div style='font-size:48px'>&#128225;</div>"
                + "<h3>Internet connection check karein</h3>"
                + "<p style='color:#8A93A6'>Page load nahi ho paya.</p>"
                + "<a href='" + TextUtils.htmlEncode(failedUrl) + "' style='display:inline-block;margin-top:12px;"
                + "padding:12px 28px;background:#00E5FF;color:#0D0F15;border-radius:24px;text-decoration:none;"
                + "font-weight:bold'>Dobara try karein</a></div></body></html>";
        view.loadDataWithBaseURL(failedUrl, html, "text/html", "UTF-8", failedUrl);
    }

    // ------------------------------------------------------------------ Home bridge + catalog

    /** Lets the remote-navigation script hand focus back to the app's own buttons. */
    private class TvBridge {
        @JavascriptInterface
        public void focusTabs() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (player.getVisibility() == View.VISIBLE) playerBack.requestFocus();
                    else tabs[current].requestFocus();
                }
            });
        }

        @JavascriptInterface
        public void coverChanged(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setCoverUi(on);
                }
            });
        }
    }

    /** Exposed to the local Home page only (file:///android_asset/home.html). */
    private class HomeBridge {
        @JavascriptInterface
        public void open(final String url, final String title) {
            final Uri uri = Uri.parse(url);
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openPlayer(url, title);
                }
            });
        }

        @JavascriptInterface
        public String getCatalog() {
            return catalog.cachedJson();
        }

        @JavascriptInterface
        public void refresh() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    catalog.refresh();
                }
            });
        }
    }

    @Override
    public void onCatalogChanged(String json) {
        tabViews[0].evaluateJavascript("window.onCatalog && window.onCatalog(" + quote(json) + ")", null);
    }

    @Override
    public void onCatalogLoading(boolean loading) {
        tabViews[0].evaluateJavascript("window.onCatalogStatus && window.onCatalogStatus(" + loading + ")", null);
    }

    private static String quote(String s) {
        return org.json.JSONObject.quote(s);
    }

    // ------------------------------------------------------------------ Player screen

    private LinearLayout createPlayer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(COLOR_BG);

        LinearLayout bar = new LinearLayout(this);
        playerBar = bar;
        bar.setBackgroundColor(COLOR_BAR);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(this);
        playerBack = back;
        makeFocusable(back);
        back.setText("←");
        back.setTextSize(24);
        back.setTextColor(COLOR_ACTIVE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closePlayer();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));

        playerTitle = new TextView(this);
        playerTitle.setTextColor(Color.WHITE);
        playerTitle.setTextSize(16);
        playerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        playerTitle.setSingleLine(true);
        playerTitle.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(playerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reload = new TextView(this);
        makeFocusable(reload);
        reload.setText("↻");
        reload.setTextSize(22);
        reload.setTextColor(COLOR_IDLE);
        reload.setGravity(Gravity.CENTER);
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerView.reload();
            }
        });
        bar.addView(reload, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));

        if (isTv) {
            // Back to full-cover video after leaving it with the arrow keys.
            TextView full = new TextView(this);
            makeFocusable(full);
            full.setText("\u26F6");
            full.setTextSize(22);
            full.setTextColor(COLOR_IDLE);
            full.setGravity(Gravity.CENTER);
            full.setContentDescription("Full screen");
            full.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playerView.requestFocus();
                    playerView.evaluateJavascript("window.__mlCover && __mlCover.enter()", null);
                }
            });
            bar.addView(full, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));
        }

        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        playerView = createWebView(true);
        box.addView(playerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void openPlayer(String url, String title) {
        playerTitle.setText(title);
        playerView.clearHistory();
        playerView.loadUrl(url);
        player.setVisibility(View.VISIBLE);
        chrome.setVisibility(View.GONE);
        playerView.requestFocus();
        catalog.pause();
        updateActiveWebViews();
    }

    /** The player's history can start with the about:blank left by the previous closePlayer(). */
    private boolean playerCanGoBack() {
        android.webkit.WebBackForwardList list = playerView.copyBackForwardList();
        int prev = list.getCurrentIndex() - 1;
        return prev >= 0 && !"about:blank".equals(list.getItemAtIndex(prev).getUrl());
    }

    private void closePlayer() {
        setCoverUi(false);
        playerView.stopLoading();
        playerView.loadUrl("about:blank");
        player.setVisibility(View.GONE);
        chrome.setVisibility(View.VISIBLE);
        tabViews[current].requestFocus();
        updateActiveWebViews();
        tabViews[0].evaluateJavascript("window.onHomeShown && onHomeShown()", null);
        catalog.resume();
    }

    /**
     * Only the WebView on screen keeps running; the rest are paused so a playing
     * channel doesn't compete with hidden pages for the TV's CPU.
     */
    private void updateActiveWebViews() {
        WebView active = visibleWebView();
        for (WebView w : tabViews) {
            if (w == active) w.onResume(); else w.onPause();
        }
        if (playerView == active) playerView.onResume(); else playerView.onPause();
    }

    // ------------------------------------------------------------------ Tabs

    private ProgressBar createProgressBar() {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        ClipDrawable fill = new ClipDrawable(new ColorDrawable(COLOR_ACTIVE), Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{new ColorDrawable(COLOR_BG), fill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);
        return bar;
    }

    private LinearLayout createTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(COLOR_BAR);

        for (int i = 0; i < TITLES.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            makeFocusable(tab);
            tab.setText(TITLES[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(14);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (index == current && index != 0) {
                        tabViews[index].loadUrl(URLS[index]); // tap active tab again = go to its home page
                        tabLoaded[index] = true;
                    } else {
                        selectTab(index);
                    }
                }
            });
            tabs[i] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        TextView reload = new TextView(this);
        makeFocusable(reload);
        reload.setText("↻");
        reload.setGravity(Gravity.CENTER);
        reload.setTextSize(22);
        reload.setTextColor(COLOR_IDLE);
        reload.setContentDescription("Reload");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabViews[current].reload();
            }
        });
        bar.addView(reload, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        return bar;
    }

    private void selectTab(int index) {
        if (coverOn) setCoverUi(false);
        current = index;
        for (int i = 0; i < tabViews.length; i++) {
            boolean active = i == index;
            tabViews[i].setVisibility(active ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(active ? COLOR_ACTIVE : COLOR_IDLE);
        }
        if (!tabLoaded[index]) {
            tabLoaded[index] = true;
            tabViews[index].loadUrl(URLS[index]);
        }
        int p = tabViews[index].getProgress();
        progress.setProgress(p);
        progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
        if (player != null) updateActiveWebViews();
    }

    // ------------------------------------------------------------------ Fullscreen video

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        savedOrientation = getRequestedOrientation();

        root.addView(view, matchParent());
        setSystemBarsHidden(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;

        setSystemBarsHidden(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(savedOrientation);
    }

    @SuppressWarnings("deprecation")
    private void setSystemBarsHidden(boolean hidden) {
        View decor = getWindow().getDecorView();
        if (hidden) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ------------------------------------------------------------------ TV remote

    /** Remote-friendly buttons: reachable with the D-pad and clearly highlighted when focused. */
    private void makeFocusable(TextView view) {
        view.setFocusable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(Color.parseColor("#3300E5FF")));
        bg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#2200E5FF")));
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        view.setBackground(bg);
    }

    /** While a video covers the screen, the app's own bars and the system bars get out of the way. */
    private void setCoverUi(boolean on) {
        coverOn = on;
        int bars = on ? View.GONE : View.VISIBLE;
        playerBar.setVisibility(bars);
        tabBar.setVisibility(bars);
        if (customView == null) setSystemBarsHidden(on);
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            visibleWebView().requestFocus();
        } else if (customView == null) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private WebView visibleWebView() {
        return player.getVisibility() == View.VISIBLE ? playerView : tabViews[current];
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean playKey = code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE
                || (customView != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER));
        if (code == KeyEvent.KEYCODE_SEARCH && customView == null) {
            // The remote's Search/mic button jumps straight to the Home search box.
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (player.getVisibility() == View.VISIBLE) closePlayer();
                selectTab(0);
                tabViews[0].requestFocus();
                tabViews[0].evaluateJavascript("window.scrollTo(0,0); window.focusSearch && focusSearch()", null);
            }
            return true;
        }
        if (playKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                visibleWebView().evaluateJavascript("window.__mlNav && __mlNav.togglePlay()", null);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ------------------------------------------------------------------ Lifecycle

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (player.getVisibility() == View.VISIBLE) {
            if (playerCanGoBack()) playerView.goBack(); else closePlayer();
        } else if (tabViews[current].canGoBack()) {
            tabViews[current].goBack();
        } else if (current != 0) {
            selectTab(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current", current);
        for (int i = 1; i < tabViews.length; i++) {
            if (!tabLoaded[i]) continue;
            Bundle state = new Bundle();
            tabViews[i].saveState(state);
            outState.putBundle("web" + i, state);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView w : tabViews) w.onPause();
        playerView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveWebViews();
    }

    @Override
    protected void onDestroy() {
        catalog.cancel();
        for (WebView w : tabViews) w.destroy();
        playerView.destroy();
        super.onDestroy();
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
