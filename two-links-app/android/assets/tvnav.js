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
    'box-shadow:0 0 0 6px rgba(0,229,255,.35)!important}';
  (document.head || document.documentElement).appendChild(style);

  function visible(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 8 || r.height < 8) return null;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || +cs.opacity === 0) return null;
    return r;
  }

  // An open pop-up dialog (fixed, covering most of the screen) traps the remote inside it.
  // Fixed-position elements are recorded during the clickable scan, so this check only
  // looks at a handful of elements instead of hit-testing the whole page on every key.
  function overlayRoot() {
    if (!clickables) clickables = scanClickables();
    var screen = window.innerWidth * window.innerHeight;
    for (var i = fixedEls.length - 1; i >= 0; i--) {
      var el = fixedEls[i];
      if (!el.isConnected) continue;
      var r = visible(el);
      if (r && r.width * r.height >= screen * 0.6) return el;
    }
    return null;
  }

  // Hit-testing for an overlay is the costliest step, so it runs once per key press.
  function candidates() {
    var all = collect();
    var overlay = overlayRoot();
    if (overlay) {
      var inside = all.filter(function (c) { return overlay.contains(c.el); });
      if (inside.length) { inside.overlay = overlay; return inside; }
    }
    return all;
  }

  // Finding "clickable-looking" elements needs getComputedStyle on every div/span, which
  // is slow on big channel grids. Do it once and redo it only after the page's DOM changes.
  var clickables = null, fixedEls = [];
  new MutationObserver(function (records) {
    for (var i = 0; i < records.length; i++) {
      if (records[i].addedNodes.length || records[i].removedNodes.length) { clickables = null; return; }
    }
  }).observe(document.documentElement, { childList: true, subtree: true });

  function scanClickables() {
    var list = [], seen = new Set();
    fixedEls = [];
    document.querySelectorAll('dialog,aside').forEach(function (el) {
      if (getComputedStyle(el).position === 'fixed') fixedEls.push(el);
    });
    document.querySelectorAll(NATIVE).forEach(function (el) { seen.add(el); list.push(el); });
    document.querySelectorAll(MAYBE).forEach(function (el) {
      var cs = getComputedStyle(el);
      if (cs.position === 'fixed') fixedEls.push(el);
      if (seen.has(el)) return;
      if (cs.cursor !== 'pointer') return;
      // Skip wrappers whose clickable child is already a candidate.
      if (el.parentElement && getComputedStyle(el.parentElement).cursor === 'pointer') return;
      list.push(el);
    });
    return list;
  }

  function collect() {
    if (!clickables) clickables = scanClickables();
    var list = [];
    var vh = window.innerHeight;
    for (var i = 0; i < clickables.length; i++) {
      var el = clickables[i];
      var r = el.getBoundingClientRect();
      // Only look a couple of screens around the viewport; long channel grids stay fast.
      if (r.width < 8 || r.height < 8 || r.bottom < -vh * 2 || r.top > vh * 3) continue;
      if (visible(el)) list.push({ el: el, r: r });
    }
    return list;
  }

  function setCurrent(el) {
    if (current) current.classList.remove(FOCUS_CLASS);
    current = el;
    if (!el) return;
    el.classList.add(FOCUS_CLASS);
    var tag = el.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') {
      // Just passing over a text box must not pop up the TV keyboard; OK opens it.
      if (document.activeElement && document.activeElement !== el) document.activeElement.blur();
    } else {
      try { el.focus({ preventScroll: true }); } catch (e) {}
    }
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
    var list = candidates();
    if (list.overlay && !list.overlay.contains(current)) return !!first();
    var from = current.getBoundingClientRect();
    var fx = from.left + from.width / 2, fy = from.top + from.height / 2;
    var best = null, bestScore = Infinity;
    list.forEach(function (c) {
      if (c.el === current || current.contains(c.el) || c.el.contains(current)) return;
      var cx = c.r.left + c.r.width / 2, cy = c.r.top + c.r.height / 2;
      var main, side;
      if (dir === 'down') { main = c.r.top - from.bottom + 1; side = Math.abs(cx - fx); if (cy <= fy) return; }
      else if (dir === 'up') { main = from.top - c.r.bottom + 1; side = Math.abs(cx - fx); if (cy >= fy) return; }
      else if (c.r.bottom <= from.top || c.r.top >= from.bottom) return; // left/right stays on the same row
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

  function atEdge(input, dir) {
    try {
      if (input.selectionStart !== input.selectionEnd) return false;
      return dir === 'left' ? input.selectionStart === 0 : input.selectionEnd === input.value.length;
    } catch (e) { return true; } // inputs without a caret (e.g. checkboxes)
  }

  var KEYS = { 37: 'left', 38: 'up', 39: 'right', 40: 'down' };
  document.addEventListener('keydown', function (e) {
    if (document.fullscreenElement || document.webkitFullscreenElement) return; // let the player use the keys
    var dir = KEYS[e.keyCode];
    var typing = current && (current.tagName === 'INPUT' || current.tagName === 'TEXTAREA') &&
                 document.activeElement === current;
    if (dir) {
      if (typing && (dir === 'left' || dir === 'right') && !atEdge(current, dir)) return; // move the text cursor
      if (typing) current.blur();
      e.preventDefault();
      e.stopPropagation();
      if (!move(dir) && window.MLTv) MLTv.focusTabs();
    } else if (e.keyCode === 13 && !typing) {
      e.preventDefault();
      e.stopPropagation();
      activate();
    }
  }, true);

  window.__mlNav = { first: first, select: setCurrent, focusIn: function () {
    if (current && document.contains(current) && visible(current)) setCurrent(current); else first();
  }, togglePlay: function () {
    var v = document.querySelector('video');
    if (v) { if (v.paused) v.play(); else v.pause(); }
  } };
})();
