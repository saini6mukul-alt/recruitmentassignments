// As soon as a page starts playing a video, stretch it over the whole screen and hide
// everything else (TVs: object-fit cover; phones, movies only: contain). Because the
// page's own player controls are hidden then, cover mode has its own control bar:
// seek ±10s, play/pause, progress bar and time. TV remote: ←/→ seek, OK play/pause,
// ↑/↓ leave cover mode for the page's own controls (server, episodes…). Phones: tap
// shows the bar. MLTv.coverChanged tells the app to hide its own bars meanwhile.
(function () {
  if (window.__mlCover) return;
  var phone = !!window.__mlCoverPhone;
  // TV movies: no automatic full screen; a "Full Screen" button (bottom-left) enters it.
  var manual = !!window.__mlCoverManual;

  var ON = '__ml_cover_on', TARGET = '__ml_cover', ANCESTOR = '__ml_cover_anc';
  var active = false, userExited = false, target = null;

  var style = document.createElement('style');
  style.textContent =
    'html.' + ON + ',html.' + ON + ' body{overflow:hidden!important;background:#000!important}' +
    'html.' + ON + ' body *:not(.' + TARGET + '):not(.' + ANCESTOR + '){visibility:hidden!important}' +
    'html.' + ON + ' .' + ANCESTOR + '{transform:none!important;filter:none!important;contain:none!important;' +
    'will-change:auto!important;visibility:visible!important}' +
    'html.' + ON + ' .' + TARGET + '{position:fixed!important;left:0!important;top:0!important;right:0!important;' +
    'bottom:0!important;width:100vw!important;height:100vh!important;max-width:none!important;' +
    'max-height:none!important;margin:0!important;padding:0!important;border:0!important;' +
    'object-fit:' + (phone ? 'contain' : 'cover') + '!important;background:#000!important;z-index:2147483647!important;' +
    'visibility:visible!important;transform:none!important}' +
    // Control bar. It lives under <html>, outside <body>, so the hide-everything rule above
    // doesn't touch it; ids keep the site's own CSS away.
    '#__ml_ui{position:fixed!important;left:0;right:0;bottom:0;z-index:2147483647!important;display:none;' +
    'padding:28px 4vw 18px!important;background:linear-gradient(transparent,rgba(0,0,0,.85))!important;' +
    'font:600 15px/1.2 system-ui,sans-serif!important;color:#fff!important;visibility:visible!important}' +
    'html.' + ON + ' #__ml_ui.show{display:block}' +
    '#__ml_ui *{box-sizing:border-box!important;visibility:visible!important}' +
    '#__ml_track{position:relative!important;height:22px!important;cursor:pointer!important;touch-action:none}' +
    '#__ml_track:before{content:"";position:absolute;left:0;right:0;top:9px;height:4px;border-radius:2px;background:rgba(255,255,255,.3)}' +
    '#__ml_fill{position:absolute!important;left:0;top:9px;height:4px!important;border-radius:2px;background:#00e5ff!important}' +
    '#__ml_knob{position:absolute!important;top:4px;width:14px!important;height:14px!important;margin-left:-7px;' +
    'border-radius:50%;background:#00e5ff!important}' +
    '#__ml_row{display:flex!important;align-items:center!important;gap:12px!important;margin-top:6px!important}' +
    '#__ml_row button{all:unset;cursor:pointer;min-width:52px;height:40px;padding:0 10px;border-radius:20px;' +
    'background:rgba(255,255,255,.14)!important;color:#fff!important;text-align:center;font:700 15px system-ui,sans-serif!important}' +
    '#__ml_time{flex:1!important;opacity:.9;white-space:nowrap}' +
    '#__ml_hint{opacity:.6;font-size:12px!important;white-space:nowrap}' +
    '#__ml_toast{position:fixed!important;left:50%;top:44%;transform:translate(-50%,-50%);display:none;' +
    'padding:14px 26px;border-radius:30px;background:rgba(0,0,0,.65)!important;font:700 26px system-ui,sans-serif!important;' +
    'color:#fff!important;z-index:2147483647!important}' +
    'html.' + ON + ' #__ml_toast.show{display:block}' +
    '#__ml_fsbtn{all:unset;position:fixed!important;left:24px!important;bottom:24px!important;z-index:2147483647!important;' +
    'padding:12px 22px!important;border-radius:28px!important;background:rgba(0,0,0,.75)!important;color:#fff!important;' +
    'border:2px solid rgba(255,255,255,.35)!important;font:700 18px system-ui,sans-serif!important;cursor:pointer!important;' +
    'visibility:visible!important}' +
    'html.' + ON + ' #__ml_fsbtn{display:none!important}';
  (document.head || document.documentElement).appendChild(style);

  // ---------- control bar ----------
  var ui = document.createElement('div');
  ui.id = '__ml_ui';
  ui.innerHTML =
    '<div id="__ml_track"><div id="__ml_fill"></div><div id="__ml_knob"></div></div>' +
    '<div id="__ml_row">' +
    '<button data-a="back">&#9194; 10</button><button data-a="play">&#9199;</button>' +
    '<button data-a="fwd">10 &#9193;</button><span id="__ml_time"></span>' +
    '<span id="__ml_hint">' + (phone ? '' : '&larr; &rarr; 10s &nbsp;&bull;&nbsp; OK &#9199; &nbsp;&bull;&nbsp; &uarr;&darr; menu') + '</span>' +
    '<button data-a="exit">' + (phone ? '&#10005; Menu' : '&#9776;') + '</button></div>';
  var toast = document.createElement('div');
  toast.id = '__ml_toast';
  document.documentElement.appendChild(ui);
  document.documentElement.appendChild(toast);
  if (manual) {
    var fsBtn = document.createElement('button');
    fsBtn.id = '__ml_fsbtn';
    fsBtn.innerHTML = '&#x26F6; Full Screen';
    fsBtn.addEventListener('click', function () { userExited = false; enter(); });
    document.documentElement.appendChild(fsBtn);
  }
  var $ = function (id) { return document.getElementById(id); };
  var hideTimer = null, toastTimer = null;

  function video() {
    if (target && target.tagName === 'VIDEO') return target;
    return findVideo();
  }
  function isLive(v) { return !v || !isFinite(v.duration) || v.duration <= 0 || v.duration > 86400; }
  function fmt(t) {
    t = Math.max(0, Math.floor(t || 0));
    var h = Math.floor(t / 3600), m = Math.floor(t / 60) % 60, sec = t % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (sec < 10 ? '0' : '') + sec;
  }
  function paint() {
    var v = video();
    if (!v) return;
    var live = isLive(v);
    $('__ml_track').style.display = live ? 'none' : '';
    ui.querySelector('[data-a="back"]').style.display = live ? 'none' : '';
    ui.querySelector('[data-a="fwd"]').style.display = live ? 'none' : '';
    $('__ml_time').textContent = live ? '\u25CF LIVE' : fmt(v.currentTime) + ' / ' + fmt(v.duration);
    var pct = live ? 0 : Math.min(100, v.currentTime / v.duration * 100);
    $('__ml_fill').style.width = pct + '%';
    $('__ml_knob').style.left = pct + '%';
  }
  function showUi() {
    if (!active) return;
    paint();
    ui.classList.add('show');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function () {
      var v = video();
      if (v && !v.paused) ui.classList.remove('show');
    }, 4000);
  }
  function flash(text) {
    toast.textContent = text;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove('show'); }, 700);
  }
  function seek(delta) {
    var v = video();
    if (!v || isLive(v)) return false;
    v.currentTime = Math.max(0, Math.min(v.duration - 1, v.currentTime + delta));
    flash((delta > 0 ? '+' : '\u2212') + Math.abs(delta) + 's');
    showUi();
    return true;
  }
  function togglePlay() {
    var v = video();
    if (!v) return;
    if (v.paused) { var pr = v.play(); if (pr && pr.catch) pr.catch(function () {}); flash('\u25B6'); }
    else { v.pause(); flash('\u275A\u275A'); }
    showUi();
  }
  function seekTo(clientX) {
    var v = video();
    if (!v || isLive(v)) return;
    var r = $('__ml_track').getBoundingClientRect();
    v.currentTime = Math.max(0, Math.min(1, (clientX - r.left) / r.width)) * v.duration;
    showUi();
  }
  ui.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('button');
    if (!b) return;
    var a = b.getAttribute('data-a');
    if (a === 'back') seek(-10);
    else if (a === 'fwd') seek(10);
    else if (a === 'play') togglePlay();
    else if (a === 'exit') { userExited = true; exit(); }
  });
  var dragging = false;
  $('__ml_track').addEventListener('pointerdown', function (e) { dragging = true; seekTo(e.clientX); });
  window.addEventListener('pointermove', function (e) { if (dragging) seekTo(e.clientX); });
  window.addEventListener('pointerup', function () { dragging = false; });
  document.addEventListener('timeupdate', function () {
    if (active && ui.classList.contains('show')) paint();
  }, true);

  function area(el) {
    var r = el.getBoundingClientRect();
    return r.width * r.height;
  }

  function findVideo() {
    var best = null, bestArea = 0;
    document.querySelectorAll('video').forEach(function (v) {
      var playing = !v.paused || v.readyState >= 2 || v.currentSrc || v.srcObject;
      var a = area(v);
      if (playing && a > bestArea) { best = v; bestArea = a; }
    });
    return best;
  }

  // Some player pages keep the video inside an iframe; cover the iframe instead.
  function findPlayerFrame() {
    var best = null, bestArea = window.innerWidth * window.innerHeight * 0.2;
    document.querySelectorAll('iframe').forEach(function (f) {
      var a = area(f);
      if (a > bestArea) { best = f; bestArea = a; }
    });
    return best;
  }

  function enter(el) {
    el = el || target || findVideo() || findPlayerFrame();
    if (!el) return false;
    exit(true);
    target = el;
    el.classList.add(TARGET);
    for (var p = el.parentElement; p && p !== document.documentElement; p = p.parentElement) {
      p.classList.add(ANCESTOR);
    }
    document.documentElement.classList.add(ON);
    if (el.tagName === 'VIDEO') {
      if (el.paused) { var pr = el.play(); if (pr && pr.catch) pr.catch(function () {}); }
    }
    active = true;
    userExited = false;
    if (window.MLTv && MLTv.coverChanged) MLTv.coverChanged(true);
    return true;
  }

  function exit(silent) {
    document.documentElement.classList.remove(ON);
    if (typeof ui !== 'undefined') ui.classList.remove('show');
    document.querySelectorAll('.' + TARGET + ',.' + ANCESTOR).forEach(function (el) {
      el.classList.remove(TARGET, ANCESTOR);
    });
    var was = active;
    active = false;
    if (!silent && was && window.MLTv && MLTv.coverChanged) MLTv.coverChanged(false);
  }

  function maybeEnter(v) {
    if (active || userExited || manual) return;
    v = v || findVideo();
    if (v) enter(v);
  }

  // Event-driven: react when a video actually starts, instead of polling the page.
  ['playing', 'loadeddata', 'play'].forEach(function (type) {
    document.addEventListener(type, function (e) {
      if (e.target && e.target.tagName === 'VIDEO') maybeEnter(e.target);
    }, true);
  });
  // The video may already be playing when this script arrives; iframe players fire no
  // events here, so look for one a couple of times early on.
  [300, 1500, 4000, 9000].forEach(function (ms) {
    setTimeout(function () {
      maybeEnter();
      if (!active && !userExited && !manual && ms >= 4000) { var f = findPlayerFrame(); if (f) enter(f); }
    }, ms);
  });
  // While covered, a cheap check that the player wasn't replaced (e.g. server switch).
  setInterval(function () {
    if (active && target && !target.isConnected) { exit(); target = null; maybeEnter(); }
  }, 2000);

  if (phone) {
    // A tap on the covered video shows the control bar (taps on the bar itself go through).
    window.addEventListener('click', function (e) {
      if (!active || ui.contains(e.target)) return;
      e.preventDefault(); e.stopImmediatePropagation();
      if (ui.classList.contains('show')) ui.classList.remove('show'); else showUi();
    }, true);
  }

  // Runs before the page's (and tvnav.js's) own key handlers.
  window.addEventListener('keydown', function (e) {
    if (!active) return;
    var k = e.keyCode;
    if (k === 37 || k === 39) {        // ← →: seek (holding the key jumps faster)
      e.preventDefault(); e.stopImmediatePropagation();
      if (!seek((k === 39 ? 1 : -1) * (e.repeat ? 30 : 10))) showUi();
    } else if (k === 38 || k === 40) { // ↑ ↓: show the page again (server/episode buttons)
      e.preventDefault(); e.stopImmediatePropagation();
      userExited = true;
      exit();
    } else if (k === 13) {             // OK: play / pause
      e.preventDefault(); e.stopImmediatePropagation();
      togglePlay();
    }
  }, true);

  window.__mlCover = {
    enter: function () { userExited = false; return enter(); },
    exit: function () { userExited = true; exit(); },
    seek: function (delta) { return seek(delta); },
    togglePlay: function () { togglePlay(); },
    isActive: function () { return active; }
  };
})();
