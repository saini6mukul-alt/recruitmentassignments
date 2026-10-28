package com.mukul.mylivetv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Two-tab WebView app. Each tab keeps its own WebView alive, so switching
 * tabs does not reload the page or stop navigation history.
 */
public class MainActivity extends Activity {

    private static final String[] TITLES = {"Live TV Hub", "StreamFlex TV"};
    private static final String[] URLS = {
            "https://allinonelivetv.pages.dev/",
            "https://streamflexsmm.in/Game/"
    };

    private static final int COLOR_BG = Color.parseColor("#0D0F15");
    private static final int COLOR_BAR = Color.parseColor("#151821");
    private static final int COLOR_ACTIVE = Color.parseColor("#00E5FF");
    private static final int COLOR_IDLE = Color.parseColor("#8A93A6");

    private final WebView[] webViews = new WebView[URLS.length];
    private final TextView[] tabs = new TextView[URLS.length];
    private int current = 0;

    private FrameLayout root;
    private LinearLayout chrome;
    private ProgressBar progress;

    // Fullscreen video state
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int savedOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);

        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        ClipDrawable fill = new ClipDrawable(new ColorDrawable(COLOR_ACTIVE), Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable bar = new LayerDrawable(new android.graphics.drawable.Drawable[]{new ColorDrawable(COLOR_BG), fill});
        bar.setId(0, android.R.id.background);
        bar.setId(1, android.R.id.progress);
        progress.setProgressDrawable(bar);
        chrome.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout pages = new FrameLayout(this);
        for (int i = 0; i < URLS.length; i++) {
            webViews[i] = createWebView(i);
            webViews[i].setVisibility(i == current ? View.VISIBLE : View.GONE);
            pages.addView(webViews[i], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        chrome.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        chrome.addView(createTabBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        root.addView(chrome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (savedInstanceState != null) {
            current = savedInstanceState.getInt("current", 0);
            for (int i = 0; i < webViews.length; i++) {
                Bundle state = savedInstanceState.getBundle("web" + i);
                if (state == null || webViews[i].restoreState(state) == null) {
                    webViews[i].loadUrl(URLS[i]);
                }
            }
        } else {
            for (int i = 0; i < webViews.length; i++) {
                webViews[i].loadUrl(URLS[i]);
            }
        }
        selectTab(current);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(final int index) {
        WebView web = new WebView(this);
        web.setBackgroundColor(COLOR_BG);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (index == current) progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (index == current) progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOfflinePage(view, index);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (index == current) {
                    progress.setProgress(newProgress);
                    progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                }
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

    /** Web links stay inside the app; anything else (intent:, tel:, whatsapp:, …) opens externally. */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about")
                || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
            return false;
        }
        try {
            Intent intent = scheme.equals("intent")
                    ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            startActivity(intent);
        } catch (ActivityNotFoundException | java.net.URISyntaxException e) {
            Toast.makeText(this, "Is link ke liye koi app nahi mili", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void showOfflinePage(WebView view, int index) {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#0D0F15;color:#E8EDF2;font-family:sans-serif;text-align:center'>"
                + "<div><div style='font-size:48px'>&#128225;</div>"
                + "<h3>Internet connection check karein</h3>"
                + "<p style='color:#8A93A6'>" + TITLES[index] + " load nahi ho paya.</p>"
                + "<a href='" + URLS[index] + "' style='display:inline-block;margin-top:12px;padding:12px 28px;"
                + "background:#00E5FF;color:#0D0F15;border-radius:24px;text-decoration:none;font-weight:bold'>"
                + "Dobara try karein</a></div></body></html>";
        view.loadDataWithBaseURL(URLS[index], html, "text/html", "UTF-8", URLS[index]);
    }

    private LinearLayout createTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(COLOR_BAR);

        for (int i = 0; i < TITLES.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(TITLES[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(15);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (index == current) {
                        webViews[index].loadUrl(URLS[index]); // tap active tab again = go to its home page
                    } else {
                        selectTab(index);
                    }
                }
            });
            tabs[i] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        TextView reload = new TextView(this);
        reload.setText("↻");
        reload.setGravity(Gravity.CENTER);
        reload.setTextSize(22);
        reload.setTextColor(COLOR_IDLE);
        reload.setContentDescription("Reload");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webViews[current].reload();
            }
        });
        bar.addView(reload, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));
        return bar;
    }

    private void selectTab(int index) {
        current = index;
        for (int i = 0; i < webViews.length; i++) {
            boolean active = i == index;
            webViews[i].setVisibility(active ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(active ? COLOR_ACTIVE : COLOR_IDLE);
        }
        int p = webViews[index].getProgress();
        progress.setProgress(p);
        progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        savedOrientation = getRequestedOrientation();

        chrome.setVisibility(View.GONE);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        chrome.setVisibility(View.VISIBLE);
        setSystemBarsHidden(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(savedOrientation);
    }

    @SuppressWarnings("deprecation")
    private void setSystemBarsHidden(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
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

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (webViews[current].canGoBack()) {
            webViews[current].goBack();
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
        for (int i = 0; i < webViews.length; i++) {
            Bundle state = new Bundle();
            webViews[i].saveState(state);
            outState.putBundle("web" + i, state);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView w : webViews) w.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (WebView w : webViews) w.onResume();
    }

    @Override
    protected void onDestroy() {
        for (WebView w : webViews) {
            if (w != null) {
                w.stopLoading();
                w.destroy();
            }
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
