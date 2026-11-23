(function () {
  'use strict';

  var STYLE_ID = 'mod-ui-liquid-glass-style';
  var TRANSPARENT_SELECTORS = [
    'html',
    'body',
    'bard-sidenav-container',
    'mat-sidenav-container',
    'mat-sidenav-content',
    'chat-window',
    'chat-window-content',
    '.chat-history',
    '.input-area-container',
    '.bottom-container',
    'infinite-scroller'
  ];

  function applyTransparentInlineFixes() {
    if (!window.__MOD_UI_ACTIVE__) return;
    for (var i = 0; i < TRANSPARENT_SELECTORS.length; i++) {
      var nodes = document.querySelectorAll(TRANSPARENT_SELECTORS[i]);
      for (var j = 0; j < nodes.length; j++) {
        var el = nodes[j];
        if (el && el.style && el.style.backgroundColor && el.style.backgroundColor !== 'transparent') {
          el.style.setProperty('background-color', 'transparent', 'important');
          el.style.setProperty('background', 'transparent', 'important');
        }
      }
    }
  }

  function applyConfig(config) {
    if (!config) return;
    window.__MOD_UI_ACTIVE__ = true;
    window.__MOD_UI_CONFIG__ = config;

    var root = document.documentElement;
    var body = document.body;

    if (root) {
      var blurPx = typeof config.blurPx === 'number' ? config.blurPx : 16;
      root.style.setProperty('--mod-glass-blur', blurPx + 'px');
      root.classList.toggle('mod-ambient-glow', !!config.ambientGlow);
      root.classList.toggle('mod-hide-suggestions', !!config.hideSuggestions);
    }

    if (body) {
      body.classList.toggle('mod-ambient-glow', !!config.ambientGlow);
      body.classList.toggle('mod-hide-suggestions', !!config.hideSuggestions);
    }

    applyTransparentInlineFixes();
  }

  function removeModUi() {
    window.__MOD_UI_ACTIVE__ = false;
    var existing = document.getElementById(STYLE_ID);
    if (existing && existing.parentNode) {
      existing.parentNode.removeChild(existing);
    }
    var root = document.documentElement;
    var body = document.body;
    if (root) {
      root.style.removeProperty('--mod-glass-blur');
      root.classList.remove('mod-ambient-glow', 'mod-hide-suggestions');
    }
    if (body) {
      body.classList.remove('mod-ambient-glow', 'mod-hide-suggestions');
    }
  }

  window.__MOD_UI_APPLY__ = applyConfig;
  window.__MOD_UI_REMOVE__ = removeModUi;

  if (!window.__MOD_UI_OBSERVER_ATTACHED__) {
    window.__MOD_UI_OBSERVER_ATTACHED__ = true;
    var rafScheduled = false;

    var observer = new MutationObserver(function () {
      if (!window.__MOD_UI_ACTIVE__ || rafScheduled) return;
      rafScheduled = true;
      window.requestAnimationFrame(function () {
        rafScheduled = false;
        if (window.__MOD_UI_CONFIG__) {
          applyConfig(window.__MOD_UI_CONFIG__);
        }
      });
    });

    var startObserving = function () {
      var target = document.body || document.documentElement;
      if (target) {
        observer.observe(target, { childList: true, subtree: true });
      }
    };

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', startObserving, { once: true });
    } else {
      startObserving();
    }
  }

  if (window.__MOD_UI_CONFIG__) {
    applyConfig(window.__MOD_UI_CONFIG__);
  }
})();
