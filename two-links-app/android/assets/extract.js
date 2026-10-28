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
      if (!it || !it.n || !it.u || seen[it.u]) continue;
      seen[it.u] = 1;
      clean.push({ n: String(it.n).trim().slice(0, 80), c: String(it.c || '').trim().slice(0, 40),
                   l: /^https?:/.test(it.l || '') ? it.l : '', u: it.u });
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
          return { n: text(card, '.ch-name'), c: text(card, '.ch-group'),
                   l: img ? img.getAttribute('src') : '', u: a ? a.href : '' };
        });
      }, 25);
    }
  };

  window.__mlExtract = function (id) {
    if (readers[id]) readers[id](id); else done(id, []);
  };
})();
