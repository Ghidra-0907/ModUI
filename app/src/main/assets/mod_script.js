(function () {
  'use strict';

  var STYLE_ID = 'mod-ui-liquid-glass-style';

  var TRANSPARENT_SELECTORS = [
    'html',
    'body',
    'app-root',
    'chat-app',
    'main',
    'bard-sidenav-container',
    'mat-sidenav-container',
    'mat-sidenav-content',
    '.mat-drawer-container',
    '.mat-drawer-content',
    'chat-window',
    'chat-window-content',
    '.chat-history',
    '.chat-history-scroll-container',
    'infinite-scroller',
    'input-container',
    '.bottom-container',
    'bard-header',
    'header',
    'zero-state-v2',
    '.zero-state-container',
    'modular-zero-state'
  ];

  var INNER_INPUT_SELECTORS = [
    'rich-textarea',
    '.ql-container',
    '.ql-editor',
    '.text-input-field',
    '.text-input-field-with-images',
    '.text-input-field_textarea-wrapper',
    '.text-input-field_textarea',
    '.input-box-wrapper',
    '.input-area',
    '.input-area-with-dictation'
  ];

  var DIRECT_SUGGESTION_SELECTORS = [
    'zero-state-suggestions',
    'suggestion-chips',
    'modular-zero-state-chips',
    'suggested-prompts',
    'zero-state-cards',
    'intent-card',
    '.zero-state-suggestions',
    '.suggestion-chips',
    '.suggestion-chips-container',
    '.suggested-prompts-container',
    '.prompt-suggestions',
    '.zero-state-cards',
    '.card-carousel',
    '.starter-prompts',
    '.intent-card-container',
    '.suggestions-container',
    '.chips-container',
    '.zero-state-prompts',
    '[data-test-id="zero-state-suggestions"]',
    '[data-test-id="suggested-prompts"]',
    '[data-test-id*="suggestion-chip"]',
    '[data-test-id*="zero-state-chip"]',
    '[data-test-id*="starter-prompt"]'
  ];

  function isProtectedNode(el) {
    if (!el || el.nodeType !== 1) return true;
    if (
      el.closest('input-area-v2') ||
      el.closest('input-container') ||
      el.closest('bard-header') ||
      el.closest('header') ||
      el.closest('bard-sidenav') ||
      el.closest('mat-drawer') ||
      el.closest('.mat-drawer') ||
      el.closest('.cdk-overlay-container')
    ) {
      return true;
    }
    if (
      el.querySelector(
        'input-area-v2, rich-textarea, bard-header, bard-sidenav, mat-drawer, h1, .greeting, .hello-text'
      )
    ) {
      return true;
    }
    return false;
  }

  function applyTransparentInlineFixes() {
    for (var i = 0; i < TRANSPARENT_SELECTORS.length; i++) {
      var nodes = document.querySelectorAll(TRANSPARENT_SELECTORS[i]);
      for (var j = 0; j < nodes.length; j++) {
        var el = nodes[j];
        if (!el || !el.style) continue;
        if (el.style.backgroundColor && el.style.backgroundColor !== 'transparent') {
          el.style.setProperty('background-color', 'transparent', 'important');
        }
        if (el.style.background && el.style.background !== 'transparent') {
          el.style.setProperty('background', 'transparent', 'important');
        }
      }
    }
  }

  function normalizeInputBox() {
    var oldTagged = document.querySelectorAll('.mod-ui-glass-input');
    for (var k = 0; k < oldTagged.length; k++) {
      oldTagged[k].classList.remove('mod-ui-glass-input');
    }

    var outerInput = document.querySelector('input-area-v2');
    if (!outerInput) {
      var editor = document.querySelector('rich-textarea');
      if (editor) {
        outerInput =
          editor.closest('input-area-v2') ||
          editor.closest('fieldset') ||
          editor.closest('.input-area-container');
      }
    }

    if (outerInput) {
      outerInput.classList.add('mod-ui-glass-input');

      // Strip any inline borders/backgrounds on inner wrappers to prevent double-border look
      for (var i = 0; i < INNER_INPUT_SELECTORS.length; i++) {
        var innerNodes = outerInput.querySelectorAll(INNER_INPUT_SELECTORS[i]);
        for (var j = 0; j < innerNodes.length; j++) {
          var node = innerNodes[j];
          if (node === outerInput || !node.style) continue;
          node.style.setProperty('background', 'transparent', 'important');
          node.style.setProperty('background-color', 'transparent', 'important');
          node.style.setProperty('border', 'none', 'important');
          node.style.setProperty('box-shadow', 'none', 'important');
        }
      }

      var innerFieldsets = outerInput.querySelectorAll('fieldset');
      for (var f = 0; f < innerFieldsets.length; f++) {
        if (innerFieldsets[f] !== outerInput && innerFieldsets[f].style) {
          innerFieldsets[f].style.setProperty('background', 'transparent', 'important');
          innerFieldsets[f].style.setProperty('border', 'none', 'important');
          innerFieldsets[f].style.setProperty('box-shadow', 'none', 'important');
        }
      }
    }
  }

  function enhanceHeaderPill() {
    var candidates = document.querySelectorAll(
      'bard-mode-switcher button, [data-test-id="bard-mode-menu-button"], .logo-pill-btn'
    );
    for (var i = 0; i < candidates.length; i++) {
      var btn = candidates[i];
      if (!btn) continue;
      btn.classList.add('mod-ui-model-pill');
      var parent = btn.parentElement;
      if (parent && parent.style && !parent.matches('button')) {
        parent.style.setProperty('background', 'transparent', 'important');
        parent.style.setProperty('border', 'none', 'important');
        parent.style.setProperty('box-shadow', 'none', 'important');
      }
    }
  }

  function clearHiddenSuggestions() {
    var hidden = document.querySelectorAll('.mod-ui-hidden-suggestion');
    for (var i = 0; i < hidden.length; i++) {
      hidden[i].classList.remove('mod-ui-hidden-suggestion');
    }
  }

  function hideMobileSuggestions() {
    // 1. Hide all known Gemini suggestion / zero-state chip containers
    var selector = DIRECT_SUGGESTION_SELECTORS.join(', ');
    var directMatches = document.querySelectorAll(selector);
    for (var i = 0; i < directMatches.length; i++) {
      var el = directMatches[i];
      if (!isProtectedNode(el)) {
        el.classList.add('mod-ui-hidden-suggestion');
      }
    }

    // 2. Only scan welcome-screen buttons when there are no active chat messages yet
    var hasActiveChat = document.querySelector('user-query, model-response, .conversation-turn') !== null;
    if (hasActiveChat) return;

    // Look inside zero-state containers or main chat area for suggestion buttons below greeting
    var zeroRoots = document.querySelectorAll(
      'zero-state-v2, modular-zero-state, .zero-state-container, chat-window, main'
    );

    var greetingEl = null;
    var headings = document.querySelectorAll(
      'zero-state-v2 h1, zero-state-v2 h2, .zero-state-container h1, .zero-state-container h2, main h1, main h2, .greeting-text, .hello-text'
    );
    for (var h = 0; h < headings.length; h++) {
      var hd = headings[h];
      if (hd && !hd.closest('bard-sidenav') && !hd.closest('bard-header')) {
        greetingEl = hd;
        break;
      }
    }

    var greetingBottom = greetingEl ? greetingEl.getBoundingClientRect().bottom : 0;
    var inputEl = document.querySelector('input-area-v2, .mod-ui-glass-input, rich-textarea');
    var inputTop = inputEl ? inputEl.getBoundingClientRect().top : window.innerHeight;

    for (var r = 0; r < zeroRoots.length; r++) {
      var root = zeroRoots[r];
      if (!root) continue;

      var chips = root.querySelectorAll(
        'button, [role="button"], mat-chip, .mat-mdc-chip, .chip, .card, a.suggestion'
      );
      for (var c = 0; c < chips.length; c++) {
        var chip = chips[c];
        if (!chip || isProtectedNode(chip)) continue;
        if (chip.classList.contains('mod-ui-model-pill')) continue;

        var rect = chip.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) continue;

        // If the clickable chip sits visually between the greeting text and the bottom input bar,
        // it is a mobile welcome suggestion (e.g. "Create an image", "Clean up my calendar", etc.)
        var isBelowGreeting = greetingBottom > 0 ? rect.top >= greetingBottom - 8 : rect.top > 120;
        var isAboveInput = rect.bottom <= inputTop + 4;
        var text = (chip.innerText || chip.textContent || '').trim();

        if (isBelowGreeting && isAboveInput && text.length >= 4) {
          // Hide either the chip itself or its direct wrapper item if it only holds this chip
          chip.classList.add('mod-ui-hidden-suggestion');
          var parent = chip.parentElement;
          if (
            parent &&
            !isProtectedNode(parent) &&
            parent.children.length === 1 &&
            !parent.matches('zero-state-v2, modular-zero-state, .zero-state-container, chat-window, main')
          ) {
            parent.classList.add('mod-ui-hidden-suggestion');
          }
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

    var blurPx = typeof config.blurPx === 'number' ? config.blurPx : 20;
    var overlayDim = typeof config.overlayDim === 'number' ? config.overlayDim : 0.35;
    var isVideoMode = config.bgMode === 'NATIVE_VIDEO';
    var bgUrl = config.bgUrl || '';

    if (root) {
      root.style.setProperty('--mod-glass-blur', blurPx + 'px');
      root.style.setProperty('--mod-overlay-dim', String(overlayDim));
      if (!isVideoMode && bgUrl) {
        root.style.setProperty('--mod-bg-image', 'url("' + bgUrl + '")');
      } else {
        root.style.setProperty('--mod-bg-image', 'none');
      }
      root.classList.toggle('mod-webview-bg', !isVideoMode && !!bgUrl);
      root.classList.toggle('mod-video-mode', isVideoMode);
      root.classList.toggle('mod-ambient-glow', !!config.ambientGlow);
      root.classList.toggle('mod-hide-suggestions', !!config.hideSuggestions);
    }

    if (body) {
      body.classList.toggle('mod-webview-bg', !isVideoMode && !!bgUrl);
      body.classList.toggle('mod-video-mode', isVideoMode);
      body.classList.toggle('mod-ambient-glow', !!config.ambientGlow);
      body.classList.toggle('mod-hide-suggestions', !!config.hideSuggestions);
    }

    applyTransparentInlineFixes();
    normalizeInputBox();
    enhanceHeaderPill();

    if (config.hideSuggestions) {
      hideMobileSuggestions();
    } else {
      clearHiddenSuggestions();
    }
  }

  function removeModUi() {
    window.__MOD_UI_ACTIVE__ = false;
    clearHiddenSuggestions();

    var existing = document.getElementById(STYLE_ID);
    if (existing && existing.parentNode) {
      existing.parentNode.removeChild(existing);
    }

    var taggedInputs = document.querySelectorAll('.mod-ui-glass-input');
    for (var i = 0; i < taggedInputs.length; i++) {
      taggedInputs[i].classList.remove('mod-ui-glass-input');
    }

    var taggedPills = document.querySelectorAll('.mod-ui-model-pill');
    for (var p = 0; p < taggedPills.length; p++) {
      taggedPills[p].classList.remove('mod-ui-model-pill');
    }

    var classes = ['mod-webview-bg', 'mod-video-mode', 'mod-ambient-glow', 'mod-hide-suggestions'];
    var root = document.documentElement;
    var body = document.body;
    if (root) {
      root.style.removeProperty('--mod-glass-blur');
      root.style.removeProperty('--mod-overlay-dim');
      root.style.removeProperty('--mod-bg-image');
      root.classList.remove.apply(root.classList, classes);
    }
    if (body) {
      body.classList.remove.apply(body.classList, classes);
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
