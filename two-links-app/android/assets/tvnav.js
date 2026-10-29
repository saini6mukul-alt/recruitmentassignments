// D-pad (TV remote) navigation for any page: arrow keys move a visible highlight to
// the nearest clickable thing in that direction, OK/Enter clicks it. Moving up past
// the top of the page hands focus back to the app's tab bar via MLTv.focusTabs().
(function () {
  if (window.__mlNav) return;

  var FOCUS_CLASS = '__ml_focus';
  var NATIVE = 'a[href],button,input,select,textarea,[onclick],[tabindex]:not([tabindex="-1"]),[role="button"],[role="tab"],video';
  var MAYBE = 'div,li,span,img,label,article,section,td';
  var current = null;

  var style = document.createElement('style');
  style.textContent = '.' + FOCUS_CLASS + '{outline:3px solid #00e5ff!important;outline-offset:2px!important;' +
    'box-shadow:0 0 0 6px rgba(0,229,255,.35)!important;transform:scale(1.04);transition:transform .12s;' +
    'position:relative;z-index:2}';
  (document.head || document.documentElement).appendChild(style);

  function visible(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) return null;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || +cs.opacity === 0) return null;
    return r;
  }

  // An open pop-up dialog (fixed, covering most of the screen) traps the remote inside it.
  function overlayRoot() {
    var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
    for (; el && el !== document.body && el !== document.documentElement; el = el.parentElement) {
      if (getComputedStyle(el).position !== 'fixed') continue;
      var r = el.getBoundingClientRect();
      if (r.width * r.height >= window.innerWidth * window.innerHeight * 0.6) return el;
    }
    return null;
  }

  function candidates() {
    var all = collect();
    var overlay = overlayRoot();
    if (overlay) {
      var inside = all.filter(function (c) { return overlay.contains(c.el); });
      if (inside.length) return inside;
    }
    return all;
  }

  function collect() {
    var list = [], seen = new Set();
    var vh = window.innerHeight;
    function consider(el) {
      if (seen.has(el)) return;
      seen.add(el);
      var r = visible(el);
      // Only look a couple of screens around the viewport; long channel grids stay fast.
      if (r && r.bottom > -vh * 2 && r.top < vh * 3) list.push({ el: el, r: r });
    }
    document.querySelectorAll(NATIVE).forEach(consider);
    document.querySelectorAll(MAYBE).forEach(function (el) {
      if (seen.has(el)) return;
      if (getComputedStyle(el).cursor !== 'pointer') return;
      // Skip wrappers whose clickable child is already a candidate.
      if (el.parentElement && getComputedStyle(el.parentElement).cursor === 'pointer') return;
      consider(el);
    });
    return list;
  }

  function setCurrent(el) {
    if (current) current.classList.remove(FOCUS_CLASS);
    current = el;
    if (!el) return;
    el.classList.add(FOCUS_CLASS);
    try { el.focus({ preventScroll: true }); } catch (e) {}
    el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' });
  }

  function first() {
    var best = null, bestScore = Infinity;
    candidates().forEach(function (c) {
      if (c.r.bottom < 0 || c.r.top > window.innerHeight) return;
      var score = c.r.top * 2 + c.r.left;
      if (score < bestScore) { bestScore = score; best = c.el; }
    });
    setCurrent(best);
    return best;
  }

  function move(dir) {
    if (!current || !document.contains(current) || !visible(current)) return !!first();
    var overlay = overlayRoot();
    if (overlay && !overlay.contains(current) && candidates().some(function (c) { return overlay.contains(c.el); })) {
      return !!first();
    }
    var from = current.getBoundingClientRect();
    var fx = from.left + from.width / 2, fy = from.top + from.height / 2;
    var best = null, bestScore = Infinity;
    candidates().forEach(function (c) {
      if (c.el === current || current.contains(c.el) || c.el.contains(current)) return;
      var cx = c.r.left + c.r.width / 2, cy = c.r.top + c.r.height / 2;
      var main, side;
      if (dir === 'down') { main = c.r.top - from.bottom + 1; side = Math.abs(cx - fx); if (cy <= fy) return; }
      else if (dir === 'up') { main = from.top - c.r.bottom + 1; side = Math.abs(cx - fx); if (cy >= fy) return; }
      else if (dir === 'right') { main = c.r.left - from.right + 1; side = Math.abs(cy - fy); if (cx <= fx) return; }
      else { main = from.left - c.r.right + 1; side = Math.abs(cy - fy); if (cx >= fx) return; }
      var score = Math.max(main, 0) + side * 2.5;
      if (score < bestScore) { bestScore = score; best = c.el; }
    });
    if (best) { setCurrent(best); return true; }
    if (dir === 'down') { window.scrollBy(0, window.innerHeight * 0.6); return true; }
    if (dir === 'up') {
      if (window.scrollY > 0) { window.scrollBy(0, -window.innerHeight * 0.6); return true; }
      return false;
    }
    return true;
  }

  function activate() {
    if (!current) return first();
    var tag = current.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') { current.focus(); return; }
    if (tag === 'VIDEO') { if (current.paused) current.play(); else current.pause(); return; }
    current.click();
  }

  var KEYS = { 37: 'left', 38: 'up', 39: 'right', 40: 'down' };
  document.addEventListener('keydown', function (e) {
    if (document.fullscreenElement || document.webkitFullscreenElement) return; // let the player use the keys
    var dir = KEYS[e.keyCode];
    var typing = current && (current.tagName === 'INPUT' || current.tagName === 'TEXTAREA') &&
                 document.activeElement === current;
    if (dir) {
      if (typing && (dir === 'left' || dir === 'right')) return; // move the text cursor
      e.preventDefault();
      e.stopPropagation();
      if (!move(dir) && window.MLTv) MLTv.focusTabs();
    } else if (e.keyCode === 13 && !typing) {
      e.preventDefault();
      e.stopPropagation();
      activate();
    }
  }, true);

  window.__mlNav = { first: first, focusIn: function () {
    if (current && document.contains(current) && visible(current)) setCurrent(current); else first();
  }, togglePlay: function () {
    var v = document.querySelector('video');
    if (v) { if (v.paused) v.play(); else v.pause(); }
  } };
})();
