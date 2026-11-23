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
    '.text-input-field',
    '.text-input-field-with-images',
    '.text-input-field_textarea-wrapper',
    '.text-input-field_textarea',
    '.input-box-wrapper',
    '.input-area',
    '.input-area-with-dictation'
  ];

  function isEditorFocused() {
    var active = document.activeElement;
    if (!active) return false;
    var tag = active.tagName ? active.tagName.toLowerCase() : '';
    if (tag === 'input' || tag === 'textarea') return true;
    if (active.isContentEditable) return true;
    if (active.closest && active.closest('rich-textarea, .ql-editor')) return true;
    return false;
  }

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

  function hexToRgbString(hex) {
    var clean = String(hex || '#38bdf8').replace('#', '').trim();
    if (clean.length === 3) {
      clean = clean[0] + clean[0] + clean[1] + clean[1] + clean[2] + clean[2];
    }
    var num = parseInt(clean, 16);
    if (isNaN(num)) return '56, 189, 248';
    var r = (num >> 16) & 255;
    var g = (num >> 8) & 255;
    var b = num & 255;
    return r + ', ' + g + ', ' + b;
  }

  function ensureLedOverlays(outerInput) {
    if (!outerInput) return;

    // Ensure immediate parent does not clip the 10px soft outer LED glow
    var parent = outerInput.parentElement;
    if (parent && parent.style && parent.style.overflow !== 'visible') {
      parent.style.setProperty('overflow', 'visible', 'important');
    }

    if (!outerInput.querySelector(':scope > .mod-ui-led-halo')) {
      var halo = document.createElement('span');
      halo.className = 'mod-ui-led-halo';
      halo.setAttribute('aria-hidden', 'true');
      var haloRing = document.createElement('span');
      haloRing.className = 'mod-ui-led-halo-ring';
      halo.appendChild(haloRing);
      outerInput.appendChild(halo);
    }

    if (!outerInput.querySelector(':scope > .mod-ui-led-ring')) {
      var ring = document.createElement('span');
      ring.className = 'mod-ui-led-ring';
      ring.setAttribute('aria-hidden', 'true');
      outerInput.appendChild(ring);
    }
  }

  function normalizeInputBox() {
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

    var oldTagged = document.querySelectorAll('.mod-ui-glass-input');
    for (var k = 0; k < oldTagged.length; k++) {
      if (oldTagged[k] !== outerInput) {
        oldTagged[k].classList.remove('mod-ui-glass-input');
      }
    }

    if (outerInput) {
      if (!outerInput.classList.contains('mod-ui-glass-input')) {
        outerInput.classList.add('mod-ui-glass-input');
      }

      // Attach pointer-events: none LED ring & blurred perimeter halo overlays
      ensureLedOverlays(outerInput);

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
    var ambientOn = !!config.ambientGlow;

    // Build custom LED color configuration (1 color = breathing pulse, 2+ colors = rotating conic-gradient)
    var rawColors = Array.isArray(config.ledColors) ? config.ledColors : [];
    var ledColors = [];
    for (var ci = 0; ci < rawColors.length; ci++) {
      if (typeof rawColors[ci] === 'string' && rawColors[ci].trim().length > 0) {
        ledColors.push(rawColors[ci].trim());
      }
    }
    if (ledColors.length === 0) {
      ledColors = ['#38bdf8', '#a855f7', '#ec4899'];
    }

    var isSingleLed = ambientOn && ledColors.length === 1;
    var isMultiLed = ambientOn && ledColors.length >= 2;
    var firstColor = ledColors[0];
    var closedStops = ledColors.concat([firstColor]).join(', ');
    var ledGradientValue = isMultiLed
      ? 'conic-gradient(from var(--led-angle), ' + closedStops + ')'
      : firstColor;

    if (root) {
      root.style.setProperty('--mod-glass-blur', blurPx + 'px');
      root.style.setProperty('--mod-overlay-dim', String(overlayDim));
      root.style.setProperty('--led-single-color', firstColor);
      root.style.setProperty('--led-single-rgb', hexToRgbString(firstColor));
      root.style.setProperty('--led-stops', closedStops);
      root.style.setProperty('--led-gradient', ledGradientValue);

      if (!isVideoMode && bgUrl) {
        root.style.setProperty('--mod-bg-image', 'url("' + bgUrl + '")');
      } else {
        root.style.setProperty('--mod-bg-image', 'none');
      }
      root.classList.toggle('mod-webview-bg', !isVideoMode && !!bgUrl);
      root.classList.toggle('mod-video-mode', isVideoMode);
      root.classList.toggle('mod-ambient-glow', ambientOn);
      root.classList.toggle('mod-led-single', isSingleLed);
      root.classList.toggle('mod-led-multi', isMultiLed);
      root.classList.toggle('mod-hide-suggestions', !!config.hideSuggestions);
    }

    if (body) {
      body.classList.toggle('mod-webview-bg', !isVideoMode && !!bgUrl);
      body.classList.toggle('mod-video-mode', isVideoMode);
      body.classList.toggle('mod-ambient-glow', ambientOn);
      body.classList.toggle('mod-led-single', isSingleLed);
      body.classList.toggle('mod-led-multi', isMultiLed);
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
    window.__modUiInjected = false;
    clearHiddenSuggestions();

    var existing = document.getElementById(STYLE_ID);
    if (existing && existing.parentNode) {
      existing.parentNode.removeChild(existing);
    }

    var ledNodes = document.querySelectorAll('.mod-ui-led-ring, .mod-ui-led-halo');
    for (var l = 0; l < ledNodes.length; l++) {
      if (ledNodes[l].parentNode) {
        ledNodes[l].parentNode.removeChild(ledNodes[l]);
      }
    }

    var taggedInputs = document.querySelectorAll('.mod-ui-glass-input');
    for (var i = 0; i < taggedInputs.length; i++) {
      taggedInputs[i].classList.remove('mod-ui-glass-input');
    }

    var taggedPills = document.querySelectorAll('.mod-ui-model-pill');
    for (var p = 0; p < taggedPills.length; p++) {
      taggedPills[p].classList.remove('mod-ui-model-pill');
    }

    var classes = [
      'mod-webview-bg',
      'mod-video-mode',
      'mod-ambient-glow',
      'mod-led-single',
      'mod-led-multi',
      'mod-hide-suggestions'
    ];
    var root = document.documentElement;
    var body = document.body;
    if (root) {
      root.style.removeProperty('--mod-glass-blur');
      root.style.removeProperty('--mod-overlay-dim');
      root.style.removeProperty('--mod-bg-image');
      root.style.removeProperty('--led-single-color');
      root.style.removeProperty('--led-single-rgb');
      root.style.removeProperty('--led-stops');
      root.style.removeProperty('--led-gradient');
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

    function isMutationInsideInputArea(mutation) {
      var target = mutation && mutation.target;
      if (!target) return false;
      var el = target.nodeType === 1 ? target : target.parentElement;
      if (!el || !el.closest) return false;
      return !!el.closest('input-area-v2, .mod-ui-glass-input, rich-textarea, .ql-editor');
    }

    var observer = new MutationObserver(function (mutations) {
      if (!window.__MOD_UI_ACTIVE__ || rafScheduled) return;
      // Do not mutate DOM/styles while user is focusing or typing in the prompt box
      if (isEditorFocused()) return;

      // Ignore mutations that occurred exclusively inside the input box / editor
      var hasExternalMutation = false;
      for (var m = 0; m < mutations.length; m++) {
        if (!isMutationInsideInputArea(mutations[m])) {
          hasExternalMutation = true;
          break;
        }
      }
      if (!hasExternalMutation) return;

      rafScheduled = true;
      window.requestAnimationFrame(function () {
        rafScheduled = false;
        if (window.__MOD_UI_CONFIG__ && !isEditorFocused()) {
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
