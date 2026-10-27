/*
 * TizenTube MOD - Ad Blocker Userscript
 *
 * Injected into the YouTube TV web app (youtube.com/tv) running inside Cobalt by the
 * "Inject Adblocker Userscript" patch. The injector re-runs this file every few hundred
 * milliseconds during the first minutes, because the first documents are thrown away by
 * navigations. The guard below makes every repeat in the same document a no-op.
 *
 * Rules for editing this file:
 *  - Plain ES5 (no arrow functions, no let/const, no template strings): runs on every Cobalt build.
 *  - No external requests: everything happens locally in the page.
 *  - Keep the guard flag, otherwise the hooks stack up on each re-injection.
 *
 * How it works: the TV app turns InnerTube responses (/youtubei/v1/player, /next, /browse)
 * into objects with JSON.parse, or receives them through fetch / XMLHttpRequest. The ad data
 * lives under well-known keys (adPlacements, playerAds, adSlots, ...). Removing those keys
 * before the app reads them means the player never schedules an ad break.
 */
(function () {
  'use strict';

  if (window.__tizenTubeModAdblock) return;
  window.__tizenTubeModAdblock = true;

  // Keys removed at the top level of a player response (and of a nested playerResponse).
  var AD_KEYS = [
    'adPlacements',
    'playerAds',
    'adSlots',
    'adBreakHeartbeatParams',
    'adBreakServiceMetadata',
    'importantAdBreak'
  ];

  // Renderers removed from feed / search / watch-next lists (promoted tiles, masthead ads).
  var AD_RENDERERS = [
    'adSlotRenderer',
    'promotedSparklesWebRenderer',
    'promotedVideoRenderer',
    'tvMastheadRenderer',
    'statementBannerRenderer'
  ];

  var stats = { responses: 0, keys: 0, renderers: 0 };

  function isObject(value) {
    return value !== null && typeof value === 'object';
  }

  /** Remove the ad keys from a player response. */
  function stripPlayerResponse(response) {
    if (!isObject(response)) return;
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (AD_KEYS[i] in response) {
        try { delete response[AD_KEYS[i]]; stats.keys++; } catch (e) { /* frozen object */ }
      }
    }
  }

  /** True if an item of a list is an ad renderer (e.g. { adSlotRenderer: {...} }). */
  function isAdItem(item) {
    if (!isObject(item)) return false;
    for (var i = 0; i < AD_RENDERERS.length; i++) {
      if (item[AD_RENDERERS[i]]) return true;
    }
    return false;
  }

  /** Walk the object tree (bounded depth) and drop ad renderers from every array. */
  function stripRenderers(node, depth) {
    if (!isObject(node) || depth > 24) return;

    if (Array.isArray(node)) {
      for (var i = node.length - 1; i >= 0; i--) {
        if (isAdItem(node[i])) {
          node.splice(i, 1);
          stats.renderers++;
        } else {
          stripRenderers(node[i], depth + 1);
        }
      }
      return;
    }

    for (var key in node) {
      if (Object.prototype.hasOwnProperty.call(node, key)) {
        stripRenderers(node[key], depth + 1);
      }
    }
  }

  /** Entry point for any parsed InnerTube JSON. Returns the same object. */
  function clean(data) {
    if (!isObject(data)) return data;
    try {
      // Only touch objects that look like InnerTube responses, keeps the cost low.
      if (data.responseContext || data.playerResponse || data.playabilityStatus || data.contents) {
        stats.responses++;
        stripPlayerResponse(data);
        stripPlayerResponse(data.playerResponse);
        stripRenderers(data.contents, 0);
        stripRenderers(data.onResponseReceivedActions, 0);
      }
    } catch (e) { /* never break the app */ }
    return data;
  }

  function isInnerTubeUrl(url) {
    url = String(url || '');
    return url.indexOf('/youtubei/') !== -1 || url.indexOf('get_midroll_info') !== -1;
  }

  // --- Hook 1: JSON.parse (covers XMLHttpRequest + responseText, the main path on TV) ---
  var originalParse = JSON.parse;
  JSON.parse = function () {
    return clean(originalParse.apply(this, arguments));
  };

  // --- Hook 2: fetch (rewrites the body of InnerTube responses) ---
  var originalFetch = window.fetch;
  if (typeof originalFetch === 'function' && typeof Response === 'function') {
    window.fetch = function () {
      return originalFetch.apply(this, arguments).then(function (response) {
        if (!response || !isInnerTubeUrl(response.url)) return response;
        return response.clone().text().then(function (text) {
          try {
            var json = JSON.stringify(clean(originalParse(text)));
            return new Response(json, {
              status: response.status,
              statusText: response.statusText,
              headers: response.headers
            });
          } catch (e) {
            return response;
          }
        }, function () { return response; });
      });
    };
  }

  // --- Hook 3: XHR responseType 'json' (bypasses JSON.parse) ---
  try {
    var descriptor = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
    if (descriptor && descriptor.get && descriptor.configurable) {
      Object.defineProperty(XMLHttpRequest.prototype, 'response', {
        configurable: true,
        enumerable: descriptor.enumerable,
        get: function () {
          var value = descriptor.get.call(this);
          return this.responseType === 'json' ? clean(value) : value;
        }
      });
    }
  } catch (e) { /* engine without configurable XHR getters */ }

  // --- Safety net: skip any ad that still reaches the player ---
  // The TV player adds "ad-showing" / "ad-interrupting" on its root while an ad plays.
  setInterval(function () {
    try {
      var player = document.querySelector('.ad-showing, .ad-interrupting');
      if (!player) return;
      var video = document.querySelector('video');
      if (video && isFinite(video.duration) && video.duration > 0) {
        video.currentTime = video.duration; // jump to the end of the ad
      }
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
      if (skip && skip.click) skip.click();
    } catch (e) { /* ignore */ }
  }, 500);

  // Debug helper: run `window.__tizenTubeModAdblockStats` in remote devtools.
  window.__tizenTubeModAdblockStats = stats;
  console.log('[TizenTube MOD] adblock userscript active');
})();
