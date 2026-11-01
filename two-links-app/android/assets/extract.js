// Injected into a hidden copy of each source page. Reads only the public channel
// list (name, category, logo, link to the site's own player page) and hands it to
// the app through MLBridge.deliver(sourceId, json).
(function () {
  if (window.__mlExtract) return;

  function done(id, items) {
    var clean = [];
    var seen = {};
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      var key = it && it.u + '|' + (it.g || '');
      if (!it || !it.n || !it.u || seen[key]) continue;
      seen[key] = 1;
      var row = { n: String(it.n).trim().slice(0, 80), c: String(it.c || '').trim().slice(0, 40),
                  l: /^https?:/.test(it.l || '') ? it.l : '', u: it.u };
      if (it.g) row.g = String(it.g).slice(0, 60);
      if (it.lg) row.lg = String(it.lg).trim().toLowerCase().slice(0, 20);
      clean.push(row);
    }
    MLBridge.deliver(id, JSON.stringify(clean));
  }

  function poll(id, read, tries) {
    var items = [];
    try { items = read() || []; } catch (e) {}
    if (items.length || tries <= 0) return done(id, items);
    setTimeout(function () { poll(id, read, tries - 1); }, 1000);
  }

  function text(el, sel) {
    var x = el.querySelector(sel);
    return x ? (x.textContent || '').trim() : '';
  }

  var readers = {
    // JioTV+ (from Live TV Hub): channel list JSON, metadata fields only.
    jtv: function (id) {
      fetch('/jstr4web.json').then(function (r) { return r.json(); }).then(function (list) {
        done(id, list.map(function (ch) {
          return { n: ch.name, c: ch.category, l: ch.logo,
                   u: location.origin + '/pind.html?id=' + encodeURIComponent(ch.id) };
        }));
      }).catch(function () { done(id, []); });
    },
    // SonyLiv (from Live TV Hub): plain links to the player page.
    sony: function (id) {
      poll(id, function () {
        return Array.prototype.map.call(document.querySelectorAll('a[href*="ptest"]'), function (a) {
          var img = a.querySelector('img');
          return { n: (a.textContent || '').trim(), c: '', l: img ? img.src : '', u: a.href };
        });
      }, 20);
    },
    // StreamFlex: rendered channel cards.
    sf: function (id) {
      poll(id, function () {
        return Array.prototype.map.call(document.querySelectorAll('.card'), function (card) {
          var a = card.querySelector('a.watch-btn');
          var img = card.querySelector('img.ch-logo');
          return { n: text(card, '.ch-name'), c: text(card, '.ch-group'), lg: card.getAttribute('data-lang'),
                   l: img ? img.getAttribute('src') : '', u: a ? a.href : '' };
        });
      }, 25);
    }
  };

  // Nxsha (movies & shows): the site's own home feed, titles + posters only.
  // Links go straight to the player page (TV shows start at S1 E1; the player has an episode menu).
  // The two India rows are rebuilt as Top 10 lists of Hindi & English titles, using the site's
  // browse pages (India, most popular now) with its language filter.
  function browse(path) {
    return fetch(path).then(function (r) { return r.text(); }).then(function (html) {
      var doc = new DOMParser().parseFromString(html, 'text/html');
      var out = [], seen = {};
      doc.querySelectorAll('a[href^="/movie/"], a[href^="/tv/"]').forEach(function (a) {
        var m = a.getAttribute('href').match(/^\/(movie|tv)\/(\d+)/);
        var img = a.querySelector('img');
        if (!m || !img || seen[m[2]]) return;
        seen[m[2]] = 1;
        out.push({ type: m[1], id: m[2], title: img.getAttribute('alt'), poster: img.getAttribute('src') });
      });
      return out;
    });
  }

  function topTen(kind, heading) {
    var base = '/browse/' + kind + '?country=IN';
    return Promise.all([browse(base), browse(base + '&language=hi'), browse(base + '&language=en')])
      .then(function (res) {
        var ok = {};
        res[1].concat(res[2]).forEach(function (c) { ok[c.id] = 1; });
        var list = res[0].filter(function (c) { return ok[c.id]; });
        var inList = {};
        list.forEach(function (c) { inList[c.id] = 1; });
        res[1].forEach(function (c) { if (list.length < 10 && !inList[c.id]) { list.push(c); inList[c.id] = 1; } });
        return list.slice(0, 10).map(function (c) { return item(c.type, c.id, c.title, c.poster, heading); });
      });
  }

  // Top 10 shows only from the big three OTTs: take each platform's most popular Hindi shows
  // in India and interleave them (Netflix #1, Prime #1, JioHotstar #1, Netflix #2, …).
  var OTTS = [['8', 'Netflix'], ['119', 'Prime Video'], ['2336', 'JioHotstar']];
  function topTenOttShows(heading) {
    return Promise.all(OTTS.map(function (o) {
      return fetch('/api/media?mediaType=tv&page=1&providerId=' + o[0] + '&sort=popularity.desc&language=hi&country=IN')
        .then(function (r) { return r.json(); })
        .then(function (d) { return (d.items || []).map(function (it) { it.ott = o[1]; return it; }); });
    })).then(function (lists) {
      var out = [], seen = {};
      for (var i = 0; out.length < 10 && i < 20; i++) {
        lists.forEach(function (list) {
          var it = list[i];
          if (!it || seen[it.id] || out.length >= 10) return;
          seen[it.id] = 1;
          var row = item('tv', it.id, it.title, it.poster, heading);
          row.c = it.ott; // shown under the poster
          out.push(row);
        });
      }
      if (!out.length) throw new Error('no shows');
      return out;
    });
  }

  function item(type, id, title, poster, heading) {
    return { n: title, c: type === 'tv' ? 'TV Show' : 'Movie', l: poster, g: heading,
             u: location.origin + (type === 'tv' ? '/embed/tv/' + id + '/1/1' : '/embed/movie/' + id) };
  }

  readers.nx = function (id) {
    var feed = fetch('/api/home-feed?tabKey=home&country=IN').then(function (r) { return r.json(); });
    var tops = Promise.all([
      topTen('movies', '\uD83D\uDD25 India\u2019s Top 10 Movies'),
      topTenOttShows('\u26A1 India\u2019s Top 10 Shows \u00B7 Netflix, Prime, Hotstar')
        .catch(function () { return topTen('tv-shows', '\u26A1 India\u2019s Top 10 Shows'); })
    ]).catch(function () { return null; });
    Promise.all([feed, tops]).then(function (res) {
      var out = [], top = res[1];
      if (top) { out = out.concat(top[0], top[1]); }
      (res[0].fetchedSections || []).forEach(function (sec) {
        // Replaced by the Top 10 rows above (kept if those failed to load).
        if (top && /^trendingCountry/.test(sec.key)) return;
        (sec.items || []).forEach(function (it) {
          if (!it || !it.id || (it.mediaType !== 'movie' && it.mediaType !== 'tv')) return;
          out.push(item(it.mediaType, it.id, it.title, it.poster, sec.heading));
        });
      });
      done(id, out);
    }).catch(function () { done(id, []); });
  };

  window.__mlExtract = function (id) {
    if (readers[id]) readers[id](id); else done(id, []);
  };
})();
