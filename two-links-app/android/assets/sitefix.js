// Small per-site tweaks that take the user straight to the content.
(function () {
  if (window.__mlSiteFix) return;
  window.__mlSiteFix = true;

  // nxsha.space: the /watch/ page wraps the player in an iframe with extra page chrome.
  // Open the player page itself (/embed/) instead, and press its big play button.
  if (/(^|\.)nxsha\.space$/.test(location.hostname)) {
    var check = function () {
      if (location.pathname.indexOf('/watch/') === 0) {
        location.replace(location.pathname.replace('/watch/', '/embed/') + location.search);
      }
    };
    // The site is a single-page app: catch in-app navigations too.
    ['pushState', 'replaceState'].forEach(function (name) {
      var orig = history[name];
      history[name] = function () {
        var r = orig.apply(this, arguments);
        setTimeout(check, 0);
        return r;
      };
    });
    window.addEventListener('popstate', check);
    check();

    // Skip the "Get the App / Join Telegram / Continue to website" welcome box.
    var welcomeTries = 0;
    var skipWelcome = setInterval(function () {
      if (++welcomeTries > 20) return clearInterval(skipWelcome);
      var buttons = document.querySelectorAll('button');
      for (var i = 0; i < buttons.length; i++) {
        if (/continue to website/i.test(buttons[i].textContent || '')) {
          buttons[i].click();
          return clearInterval(skipWelcome);
        }
      }
    }, 500);

    if (location.pathname.indexOf('/embed/') === 0) {
      var tries = 0;
      var autoPlay = setInterval(function () {
        var v = document.querySelector('video');
        if (++tries > 30 || (v && (v.currentSrc || !v.paused))) return clearInterval(autoPlay);
        // The play overlay is the element under the centre of the player.
        var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
        if (el && el.closest('.absolute.inset-0')) {
          // The hit may be the SVG icon, which has no .click(); dispatch a real click event.
          el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
          clearInterval(autoPlay);
        }
      }, 500);
    }
  }
})();
