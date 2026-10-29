// TV only: as soon as a page starts playing a video, stretch it over the whole
// screen (object-fit: cover) and hide everything else. Arrow keys leave cover
// mode to reach the page's own controls; OK toggles play/pause; MLTv.coverChanged
// tells the app to hide its own bars while cover mode is on.
(function () {
  if (window.__mlCover) return;

  var ON = '__ml_cover_on', TARGET = '__ml_cover', ANCESTOR = '__ml_cover_anc';
  var active = false, userExited = false, target = null, timer = null, started = Date.now();

  var style = document.createElement('style');
  style.textContent =
    'html.' + ON + ',html.' + ON + ' body{overflow:hidden!important;background:#000!important}' +
    'html.' + ON + ' body *:not(.' + TARGET + '):not(.' + ANCESTOR + '){visibility:hidden!important}' +
    'html.' + ON + ' .' + ANCESTOR + '{transform:none!important;filter:none!important;contain:none!important;' +
    'will-change:auto!important;visibility:visible!important}' +
    'html.' + ON + ' .' + TARGET + '{position:fixed!important;left:0!important;top:0!important;right:0!important;' +
    'bottom:0!important;width:100vw!important;height:100vh!important;max-width:none!important;' +
    'max-height:none!important;margin:0!important;padding:0!important;border:0!important;' +
    'object-fit:cover!important;background:#000!important;z-index:2147483647!important;' +
    'visibility:visible!important;transform:none!important}';
  (document.head || document.documentElement).appendChild(style);

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
    document.querySelectorAll('.' + TARGET + ',.' + ANCESTOR).forEach(function (el) {
      el.classList.remove(TARGET, ANCESTOR);
    });
    var was = active;
    active = false;
    if (!silent && was && window.MLTv && MLTv.coverChanged) MLTv.coverChanged(false);
  }

  function watch() {
    if (userExited) return;
    if (active && target && !document.contains(target)) { exit(); target = null; }
    if (!active) {
      var v = findVideo();
      // Prefer a real <video>; fall back to a big iframe player after a few seconds.
      if (v) enter(v);
      else if (Date.now() - started > 4000) { var f = findPlayerFrame(); if (f) enter(f); }
    }
  }

  timer = setInterval(watch, 700);
  setTimeout(function () { clearInterval(timer); timer = setInterval(watch, 3000); }, 60000);

  // Runs before the page's (and tvnav.js's) own key handlers.
  window.addEventListener('keydown', function (e) {
    if (!active) return;
    var k = e.keyCode;
    if (k >= 37 && k <= 40) {          // arrows: show the page again (server/quality buttons)
      e.preventDefault(); e.stopImmediatePropagation();
      userExited = true;
      exit();
    } else if (k === 13) {             // OK: play / pause
      e.preventDefault(); e.stopImmediatePropagation();
      if (target && target.tagName === 'VIDEO') { if (target.paused) target.play(); else target.pause(); }
    }
  }, true);

  window.__mlCover = {
    enter: function () { userExited = false; return enter(); },
    exit: function () { userExited = true; exit(); },
    isActive: function () { return active; }
  };
})();
