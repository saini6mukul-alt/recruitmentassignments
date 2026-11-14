// Injected before the app loads (Playwright addInitScript). Stands in for the Android shell and the YouTube player.
// `fx` holds fixture JSON text keyed by name.
(function install(fx) {
  window.__np = [];
  window.__requests = [];

  // Synthetic YouTube Music search response: distinct tracks per query, in the real WEB_REMIX shape.
  function hash(s) { let h = 2166136261; for (const c of s) h = Math.imul(h ^ c.charCodeAt(0), 16777619); return (h >>> 0).toString(36); }
  function vid(q, i) { return (hash(q + '#' + i) + 'abcdefghijk').slice(0, 11).replace(/[^\w-]/g, 'x'); }
  function searchResponse(q) {
    const items = Array.from({ length: 12 }, (_, i) => ({
      musicResponsiveListItemRenderer: {
        playlistItemData: { videoId: vid(q, i) },
        thumbnail: { musicThumbnailRenderer: { thumbnail: { thumbnails: [{ url: `https://i.ytimg.com/vi/${vid(q, i)}/hqdefault.jpg`, width: 400 }] } } },
        flexColumns: [
          { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [{ text: `${q} track ${i + 1}` }] } } },
          { musicResponsiveListItemFlexColumnRenderer: { text: { runs: [
            { text: `Artist ${i % 4}`, navigationEndpoint: { browseEndpoint: { browseId: 'UCx' + i, browseEndpointContextSupportedConfigs: { browseEndpointContextMusicConfig: { pageType: 'MUSIC_PAGE_TYPE_ARTIST' } } } } },
            { text: ' • ' }, { text: `3:${String(10 + i).padStart(2, '0')}` },
          ] } } },
        ],
      },
    }));
    return JSON.stringify({ contents: { tabbedSearchResultsRenderer: { tabs: [{ tabRenderer: { content: { sectionListRenderer: { contents: [{ musicShelfRenderer: { contents: items } }] } } } }] } } });
  }

  // Party-zone queries answer fast, everything else slowly: a stale build would land last (Review Focus 3).
  const latency = (q) => (/party|dance|bhangra|club|wedding/i.test(q) ? 40 : 1200);

  window.LeharNative = {
    request(id, url, method, headers, body) {
      window.__requests.push(url);
      let status = 200, text = '{}', delay = 30;
      if (url.includes('/youtubei/v1/search')) {
        const b = JSON.parse(body || '{}');
        delay = latency(b.query || '');
        text = b.params ? searchResponse(b.query) : fx['search-all'];
      } else if (url.includes('get_search_suggestions')) text = fx.suggest;
      else if (url.includes('/youtubei/v1/next')) { text = fx.next; delay = 700; }
      else if (url.includes('lrclib.net/api/get')) {
        // same lyrics, reported at the requested length so the duration check accepts it
        const d = Number(new URL(url).searchParams.get('duration')) || 268;
        text = JSON.stringify({ ...JSON.parse(fx['lrclib-get']), duration: d });
      }
      else if (url.includes('lrclib.net/api/search')) text = fx['lrclib-search'];
      else status = 404;
      setTimeout(() => window.__lehar.resolve(id, status, text), delay);
    },
    setNowPlaying(json) { window.__np.push(JSON.parse(json)); },
    setPosition() {},
    stopService() {},
    toast() {},
    saveFile() { return true; },
    version() { return 'test'; },
  };

  // Fake YouTube IFrame API
  class Player {
    constructor(id, opts) {
      this.ev = opts.events; this.t = 0; this.state = -1; this.vid = '';
      setTimeout(() => this.ev.onReady({ target: this }), 10);
      setInterval(() => { if (this.state === 1) this.t += 0.5; }, 500);
    }
    set(s) { this.state = s; this.ev.onStateChange({ data: s, target: this }); }
    loadVideoById(o) { this.vid = o.videoId || o; this.t = o.startSeconds || 0; this.set(1); }
    cueVideoById(o) { this.vid = o.videoId || o; this.t = o.startSeconds || 0; this.set(5); }
    playVideo() { this.set(1); }
    pauseVideo() { this.set(2); }
    stopVideo() { this.state = 0; }
    seekTo(s) { this.t = s; }
    getCurrentTime() { return this.t; }
    getDuration() { return 200; }
    mute() {} unMute() {}
  }
  if (!window.__noYT) window.YT = { Player };
})(window.__fx || {});
