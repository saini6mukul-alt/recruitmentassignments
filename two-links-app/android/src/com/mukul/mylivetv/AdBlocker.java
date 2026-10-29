package com.mukul.mylivetv;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Host-based ad/tracker blocking. The listed networks are the ones the two
 * sites (and the player pages they link to) load: pop-under networks that
 * redirect to Chrome, Google ads, and the header-bidding/cookie-sync swarm
 * that the players pull in.
 */
final class AdBlocker {

    private AdBlocker() {
    }

    /** Matched against the host and each parent domain (a.b.example.com → b.example.com → example.com). */
    private static final String[] BLOCKED_DOMAINS = {
            // Pop-unders / redirects seen on the sites
            "al5sm.com", "rtmark.net", "255md.com", "veilimportantly.com", "monetag.com",
            "propellerads.com", "propellerclick.com", "onclickads.net", "onclkds.com",
            "popads.net", "popcash.net", "adsterra.com", "adsterratech.com",
            "highperformanceformat.com", "profitablegatecpm.com", "effectiveratecpm.com",
            "exoclick.com", "exosrv.com", "juicyads.com", "hilltopads.net", "a-ads.com",
            "adcash.com", "clickadu.com", "galaksion.com", "evadav.com", "admaven.com",
            "rollerads.com", "richpush.co", "pushame.com", "trafficjunky.net", "adspyglass.com",
            // Google ads
            "googlesyndication.com", "doubleclick.net", "googleadservices.com",
            "adtrafficquality.google", "googletagservices.com", "google-analytics.com",
            // Seen on the movies site (nxsha.space)
            "monarchspiro.com", "googletagmanager.com",
            // Header bidding / cookie sync pulled in by the players
            "connatix.com", "adsrvr.org", "rubiconproject.com", "sharethrough.com", "iqm.com",
            "contextweb.com", "ingage.tech", "bidgx.com", "adform.net", "fwmrm.net", "adscale.de",
            "1rx.io", "casalemedia.com", "sonobi.com", "loopme.me", "smaato.net", "pubmatic.com",
            "criteo.com", "criteo.net", "ottadvisors.com", "bidswitch.net", "yellowblue.io",
            "gumgum.com", "3lift.com", "turn.com", "ipredictive.com", "inmobi.com",
            "smartadserver.com", "adnxs.com", "sitescout.com", "intentiq.com", "liadm.com",
            "postrelease.com", "pmbmonetize.live", "bidr.io", "media.net", "openx.net",
            "copper6.com", "id5-sync.com", "33across.com", "hadronid.net", "creativecdn.com",
            "yieldmo.com", "admanmedia.com", "dotomi.com", "mfadsrvr.com", "lijit.com", "a-mo.net",
            "onetag-sys.com", "presage.io", "rbstsystems.live", "seedtag.com",
            "minutemedia-prebid.com", "ad.gt", "amxrtb.com", "analytics.yahoo.com", "pbs.yahoo.com",
            "blismedia.com", "adkernel.com", "cadent.com", "simpli.fi", "amazon-adsystem.com",
            "deepintent.com", "rfihub.com", "temu.com", "everesttech.net", "adentifi.com",
            "adx.opera.com", "oa.opera.com", "cognitivlabs.com", "tribalfusion.com", "mxptint.net",
            "tapad.com", "rlcdn.com", "demdex.net", "thrtle.com", "sportradarserving.com",
            "trustedstack.com", "plusoneads.com", "a-mx.com", "360yield.com", "omnitagjs.com",
            "outbrain.com", "agkn.com", "pippio.com", "sascdn.com", "taptapnetworks.com",
            "creative-serving.com", "ads.linkedin.com", "amx1.net", "krushmedia.com",
            "tracookiepixel.xyz", "quantserve.com", "acuityplatform.com", "adition.com",
            "resetdigital.co", "ctnsnet.com", "adgrx.com", "adtech.ink", "iqzonertb.live",
            "company-target.com", "mgid.com", "pacvue.com", "advolve.io", "vistarsagency.com",
            "appier.net", "iprom.net", "loudecho.ai", "appnerve.net", "bidtheatre.com", "de17a.com",
            "onaudience.com", "semasio.net", "primis.tech", "smilewanted.com", "digitalaudience.io",
            "rtb-oveeo.com", "richaudience.com", "rtb.mx", "springserve.com", "ssp.disqus.com",
            "taboola.com", "jwpltx.com"
    };

    /** Catches ad hosts on random domains that the list above cannot name. */
    private static final String[] BLOCKED_HOST_PARTS = {
            "adserver", "adservice", "adsystem", "popunder", "popads", "prebid", "adsync",
            "cookie-sync", "cookiesync", "usersync", "user-sync"
    };

    static boolean isBlocked(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("ads.") || host.startsWith("ad.") || host.startsWith("sync.")
                || host.startsWith("csync.") || host.startsWith("pixel.")) {
            return true;
        }
        for (String part : BLOCKED_HOST_PARTS) {
            if (host.contains(part)) return true;
        }
        String h = host;
        while (true) {
            for (String domain : BLOCKED_DOMAINS) {
                if (h.equals(domain)) return true;
            }
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false;
            h = h.substring(dot + 1);
        }
    }

    static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    /** Hides ad slots that were already laid out before their network request was blocked. */
    static final String HIDE_AD_SLOTS_JS =
            "(function(){if(document.getElementById('__ml_noads'))return;"
                    + "var s=document.createElement('style');s.id='__ml_noads';"
                    + "s.textContent='ins.adsbygoogle,.adsbygoogle,iframe[id^=\"google_ads\"],iframe[id^=\"aswift\"],"
                    + "iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"],div[id^=\"div-gpt-ad\"]"
                    + "{display:none!important;height:0!important;min-height:0!important}';"
                    + "(document.head||document.documentElement).appendChild(s);})();";
}
