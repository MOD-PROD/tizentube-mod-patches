/*
 * TizenTube MOD - userscript injected by "Inject Adblocker Userscript".
 *
 * This is ysamjo's payload, taken unchanged from his "Werbe-Blocker-Userscript" patch
 * (github.com/ysamjo/morphe-patches, CobaltScriptInjector.FULL_PAYLOAD, GPLv3), verified by
 * him on a real Android TV device. It contains two independent parts:
 *
 *  1. Ad blocker: hooks JSON.parse (and fetch when available) and deletes the ad keys
 *     (adPlacements, playerAds, adSlots, ...) from InnerTube responses before the app reads them.
 *  2. Playback speed fix: TizenTube stores the chosen speed but the player resets it to 1x after
 *     'canplay'. This part re-applies the stored speed and persists speeds picked from the
 *     native YouTube speed menu.
 *
 * Both parts guard themselves with a window flag, because the injector re-runs this file
 * several times while the app starts.
 */
(function() {
  if (window.__ysamjoTvAdblock) return;
  window.__ysamjoTvAdblock = true;
  var KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'importantAdBreak', 'adBreakServiceMetadata'];

  function strip(o) {
    if (!o || typeof o !== 'object') return o;
    for (var i = 0; i < KEYS.length; i++) {
      if (KEYS[i] in o) {
        try {
          delete o[KEYS[i]];
        } catch (e) {}
      }
    }
    return o;
  }
  var parse = JSON.parse;
  JSON.parse = function() {
    var r = parse.apply(this, arguments);
    try {
      strip(r);
    } catch (e) {}
    return r;
  };
  var origFetch = window.fetch;
  if (origFetch && typeof Response === 'function') {
    window.fetch = function() {
      return origFetch.apply(this, arguments).then(function(res) {
        try {
          var u = (res && res.url) || '';
          if (u.indexOf('/youtubei/') !== -1 || u.indexOf('get_midroll_info') !== -1) {
            return res.clone().text().then(function(t) {
              try {
                var j = parse(t);
                strip(j);
                return new Response(JSON.stringify(j), {
                  status: res.status,
                  statusText: res.statusText,
                  headers: res.headers
                });
              } catch (e) {
                return res;
              }
            }).catch(function() {
              return res;
            });
          }
        } catch (e) {}
        return res;
      });
    };
  }
  console.log('YSAMJO_ADBLOCK_ACTIVE');
})();
(function() {
  if (window.__ysamjoSpeed) return;
  window.__ysamjoSpeed = true;
  var KEY = 'ytaf-configuration';
  var FALLBACK = 1;
  var quietUntil = 0;

  function readCfg() {
    try {
      var c = JSON.parse(window.localStorage[KEY] || '{}');
      return c || {};
    } catch (e) {
      return {};
    }
  }

  function desired() {
    var v = readCfg().videoSpeed;
    if (typeof v === 'string') v = parseFloat(v);
    if (typeof v !== 'number' || !isFinite(v) || v <= 0 || v > 5) return FALLBACK;
    return v;
  }

  function persist(v) {
    try {
      var c = readCfg();
      c.videoSpeed = v;
      window.localStorage[KEY] = JSON.stringify(c);
      console.log('YSAMJO_SPEED_PERSIST ' + v);
    } catch (e) {
      console.log('YSAMJO_SPEED_PERSIST_FAIL ' + e);
    }
  }

  function apply(v) {
    var el = document.querySelector('video');
    if (!el) return;
    if (Math.abs(el.playbackRate - v) < 0.0005) return;
    try {
      el.playbackRate = v;
      console.log('YSAMJO_SPEED_APPLY ' + v);
    } catch (e) {}
  }

  function enforce() {
    var v = desired();
    console.log('YSAMJO_SPEED_ENFORCE ' + v);
    quietUntil = Date.now() + 1500;
    setTimeout(function() {
      apply(v);
    }, 0);
    setTimeout(function() {
      apply(v);
    }, 250);
    setTimeout(function() {
      apply(v);
    }, 1000);
  }

  function menuOpen() {
    try {
      var b = document.body ? document.body.textContent : '';
      if (!b) return false;
      if (b.indexOf('Wiedergabegeschwindigkeit') !== -1) return true;
      if (b.indexOf('Playback speed') !== -1) return true;
      var hits = b.match(/[0-9]\.[0-9]{1,2}x/g);
      return !!(hits && hits.length >= 4);
    } catch (e) {
      return false;
    }
  }
  document.addEventListener('canplay', enforce, true);
  document.addEventListener('loadeddata', enforce, true);
  document.addEventListener('ratechange', function(ev) {
    var el = ev.target;
    if (!el || !el.tagName || el.tagName !== 'VIDEO') return;
    var v = el.playbackRate;
    var open = menuOpen();
    console.log('YSAMJO_SPEED_RATE ' + v + ' quiet=' + (Date.now() < quietUntil) + ' menu=' + (open ? 1 : 0));
    if (Date.now() < quietUntil) return;
    if (!open) return;
    if (typeof v !== 'number' || !isFinite(v) || v <= 0 || v > 5) return;
    if (Math.abs(v - desired()) < 0.0005) return;
    if (Math.abs(v - 1) < 0.0005) return;
    persist(v);
  }, true);
  setInterval(function() {
    if (Date.now() < quietUntil) return;
    apply(desired());
  }, 2000);
})();