/**
 * Wispr Flow - Main Application Controller
 * Orchestrates ultra-low latency voice capture, Gemini 2.0 Flash AI polishing,
 * 60fps liquid audio waveform, clipboard auto-copy, Floating HUD, and Android Keyboard companion.
 */

class FlowAppController {
  constructor() {
    this.currentView = 'viewFlow';
    this.activeMode = 'polish';
    this.isRecording = false;
    this.currentPolishedText = '';
    this.currentRawSpeech = '';
    this.speechStartTime = 0;
    this.animationFrameId = null;

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.init());
    } else {
      this.init();
    }
  }

  init() {
    console.log('Wispr Flow AI Initializing...');

    this.cacheDomElements();
    this.bindEvents();
    this.loadSettings();
    this.renderModePills();
    this.renderStats();
    this.renderHistory();
    this.renderDictionary();
    this.initKeyboardSimulation();
    this.initWaveCanvas();

    // Register service worker for standalone PWA capabilities
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('./sw.js').catch(err => {
        console.log('SW registration note:', err);
      });
    }

    // Connect speech engine callbacks
    if (window.flowSpeech) {
      window.flowSpeech.onInterimCallback = (fullRaw, interimDelta) => this.handleInterimSpeech(fullRaw, interimDelta);
      window.flowSpeech.onFinalCallback = (finalSpeech, durationMs) => this.handleFinalSpeech(finalSpeech, durationMs);
      window.flowSpeech.onStateChangeCallback = (state) => this.handleSpeechStateChange(state);
      window.flowSpeech.onAudioVolumeCallback = (vol, freqArray) => this.handleAudioWaveform(vol, freqArray);
    }
  }

  cacheDomElements() {
    // Navigation & Views
    this.navBtns = document.querySelectorAll('.nav-item-btn[data-target]');
    this.viewScreens = document.querySelectorAll('.view-screen');
    this.topLogoBtn = document.getElementById('topLogoBtn');
    this.btnHeaderSettings = document.getElementById('btnHeaderSettings');
    this.btnHeaderFloatingHud = document.getElementById('btnHeaderFloatingHud');

    // Visualizer & Primary Dictation Arena
    this.waveCanvas = document.getElementById('waveCanvas');
    this.btnMainMic = document.getElementById('btnMainMic');
    this.micRipple = document.getElementById('micRipple');
    this.statusPill = document.getElementById('statusPill');
    this.statusPillText = document.getElementById('statusPillText');
    this.liveLatencyPill = document.getElementById('liveLatencyPill');
    this.wpmLiveCounter = document.getElementById('wpmLiveCounter');
    this.btnDemoPhrase = document.getElementById('btnDemoPhrase');
    this.btnStandardVoiceDialog = document.getElementById('btnStandardVoiceDialog');
    this.btnOpenKeyboardSettings = document.getElementById('btnOpenKeyboardSettings');
    this.btnLaunchFloatingOverlay = document.getElementById('btnLaunchFloatingOverlay');

    // Transcripts & Output Card
    this.liveInterimContainer = document.getElementById('liveInterimContainer');
    this.liveInterimText = document.getElementById('liveInterimText');
    this.outputCard = document.getElementById('outputCard');
    this.outputContent = document.getElementById('outputContent');
    this.outputModeBadge = document.getElementById('outputModeBadge');
    this.outputMetaStats = document.getElementById('outputMetaStats');
    this.btnCopyResult = document.getElementById('btnCopyResult');
    this.btnShareResult = document.getElementById('btnShareResult');
    this.btnSpeakResult = document.getElementById('btnSpeakResult');
    this.btnClearResult = document.getElementById('btnClearResult');
    this.selectRepolishTone = document.getElementById('selectRepolishTone');

    // Mode Selector Track
    this.modePillsTrack = document.getElementById('modePillsTrack');
    this.activeModeBannerName = document.getElementById('activeModeBannerName');
    this.activeModeBannerDesc = document.getElementById('activeModeBannerDesc');

    // Floating HUD Overlay Elements
    this.floatingHud = document.getElementById('floatingHud');
    this.btnHudMic = document.getElementById('btnHudMic');
    this.hudStatusText = document.getElementById('hudStatusText');
    this.btnHudClose = document.getElementById('btnHudClose');
    this.btnHudCopy = document.getElementById('btnHudCopy');

    // Android Keyboard Companion
    this.keyboardTestInput = document.getElementById('keyboardTestInput');
    this.btnKeyboardMicSpace = document.getElementById('btnKeyboardMicSpace');
    this.keyboardMicStatus = document.getElementById('keyboardMicStatus');

    // Personal Dictionary
    this.dictList = document.getElementById('dictList');
    this.inputNewWord = document.getElementById('inputNewWord');
    this.inputNewReplacement = document.getElementById('inputNewReplacement');
    this.selectNewCategory = document.getElementById('selectNewCategory');
    this.btnAddDictWord = document.getElementById('btnAddDictWord');

    // History
    this.historyFeed = document.getElementById('historyFeed');
    this.inputSearchHistory = document.getElementById('inputSearchHistory');
    this.btnClearAllHistory = document.getElementById('btnClearAllHistory');

    // Settings
    this.settingGeminiApiKey = document.getElementById('settingGeminiApiKey');
    this.btnToggleApiKeyVis = document.getElementById('btnToggleApiKeyVis');
    this.settingModelSelect = document.getElementById('settingModelSelect');
    this.btnRefreshModels = document.getElementById('btnRefreshModels');
    this.settingAutoCopy = document.getElementById('settingAutoCopy');
    this.settingHaptic = document.getElementById('settingHaptic');
    this.settingSilenceThreshold = document.getElementById('settingSilenceThreshold');
    this.silenceThresholdDisplay = document.getElementById('silenceThresholdDisplay');
    this.settingSpeechLang = document.getElementById('settingSpeechLang');

    // Stats Displays
    this.statWordsSpoken = document.getElementById('statWordsSpoken');
    this.statTimeSaved = document.getElementById('statTimeSaved');
    this.statAverageWpm = document.getElementById('statAverageWpm');

    // Toast
    this.toastContainer = document.getElementById('toastContainer');
  }

  bindEvents() {
    // Navigation
    this.navBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-target');
        this.switchView(target);
      });
    });

    if (this.topLogoBtn) {
      this.topLogoBtn.addEventListener('click', () => this.switchView('viewFlow'));
    }
    if (this.btnHeaderSettings) {
      this.btnHeaderSettings.addEventListener('click', () => this.switchView('viewSettings'));
    }
    if (this.btnHeaderFloatingHud) {
      this.btnHeaderFloatingHud.addEventListener('click', () => this.toggleFloatingHud());
    }

    // Main Mic Action Trigger
    if (this.btnMainMic) {
      this.btnMainMic.addEventListener('click', () => this.toggleListening());
    }

    // Quick Sample Speech Demo Test
    if (this.btnDemoPhrase) {
      this.btnDemoPhrase.addEventListener('click', () => this.runDemoTest());
    }

    // Standard Android Voice Dialog Trigger
    if (this.btnStandardVoiceDialog) {
      this.btnStandardVoiceDialog.addEventListener('click', () => {
        if (window.AndroidNative && typeof window.AndroidNative.launchSpeechDialog === 'function') {
          const lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';
          window.AndroidNative.launchSpeechDialog(lang);
        } else {
          this.toggleListening();
        }
      });
    }

    // Android System Integration Buttons
    if (this.btnOpenKeyboardSettings) {
      this.btnOpenKeyboardSettings.addEventListener('click', () => this.openAndroidKeyboardSettings());
    }
    if (this.btnLaunchFloatingOverlay) {
      this.btnLaunchFloatingOverlay.addEventListener('click', () => this.launchAndroidFloatingOverlay());
    }

    // Keyboard Spacebar Shortcut for Desktop & Companion
    window.addEventListener('keydown', (e) => {
      // Spacebar when not typing in an input
      if (e.code === 'Space' && !['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
        e.preventDefault();
        this.toggleListening();
      }
    });

    // Output Card Action Buttons
    if (this.btnCopyResult) {
      this.btnCopyResult.addEventListener('click', () => this.copyToClipboard(this.currentPolishedText));
    }
    if (this.btnShareResult) {
      this.btnShareResult.addEventListener('click', () => this.shareContent(this.currentPolishedText));
    }
    if (this.btnSpeakResult) {
      this.btnSpeakResult.addEventListener('click', () => this.textToSpeech(this.currentPolishedText));
    }
    if (this.btnClearResult) {
      this.btnClearResult.addEventListener('click', () => this.clearResult());
    }
    if (this.selectRepolishTone) {
      this.selectRepolishTone.addEventListener('change', () => {
        const newTone = this.selectRepolishTone.value;
        this.repolishWithTone(newTone);
      });
    }

    // Floating HUD Events
    if (this.btnHudMic) {
      this.btnHudMic.addEventListener('click', () => this.toggleListening());
    }
    if (this.btnHudClose) {
      this.btnHudClose.addEventListener('click', () => this.toggleFloatingHud(false));
    }
    if (this.btnHudCopy) {
      this.btnHudCopy.addEventListener('click', () => this.copyToClipboard(this.currentPolishedText));
    }

    // Android Keyboard Companion Spacebar Mic
    if (this.btnKeyboardMicSpace) {
      this.btnKeyboardMicSpace.addEventListener('click', () => {
        this.toggleListening(true); // target keyboard input
      });
    }

    // Personal Dictionary Form
    if (this.btnAddDictWord) {
      this.btnAddDictWord.addEventListener('click', () => this.handleAddDictionaryWord());
    }

    // History Search & Clear
    if (this.inputSearchHistory) {
      this.inputSearchHistory.addEventListener('input', () => this.renderHistory());
    }
    if (this.btnClearAllHistory) {
      this.btnClearAllHistory.addEventListener('click', () => {
        if (confirm('Clear all voice dictation history?')) {
          window.flowStorage.clearHistory();
          this.renderHistory();
          this.showToast('History cleared', 'info');
        }
      });
    }

    // Settings Handlers
    if (this.settingGeminiApiKey) {
      this.settingGeminiApiKey.addEventListener('change', () => {
        window.flowStorage.setGeminiApiKey(this.settingGeminiApiKey.value.trim());
        this.showToast('Gemini API Key updated', 'success');
      });
    }
    if (this.btnToggleApiKeyVis) {
      this.btnToggleApiKeyVis.addEventListener('click', () => {
        const isPw = this.settingGeminiApiKey.type === 'password';
        this.settingGeminiApiKey.type = isPw ? 'text' : 'password';
        this.btnToggleApiKeyVis.textContent = isPw ? '🔒' : '👁️';
      });
    }
    if (this.settingModelSelect) {
      this.settingModelSelect.addEventListener('change', () => {
        window.flowStorage.setSelectedModel(this.settingModelSelect.value);
        this.showToast(`Selected AI Model: ${this.settingModelSelect.value}`, 'info');
      });
    }
    if (this.btnRefreshModels) {
      this.btnRefreshModels.addEventListener('click', () => this.fetchAvailableModels());
    }
    if (this.settingAutoCopy) {
      this.settingAutoCopy.addEventListener('change', () => {
        window.flowStorage.saveSettings({ autoCopyOnFinish: this.settingAutoCopy.checked });
      });
    }
    if (this.settingHaptic) {
      this.settingHaptic.addEventListener('change', () => {
        window.flowStorage.saveSettings({ playHapticFeedback: this.settingHaptic.checked });
      });
    }
    if (this.settingSilenceThreshold) {
      this.settingSilenceThreshold.addEventListener('input', (e) => {
        const val = parseInt(e.target.value, 10);
        if (this.silenceThresholdDisplay) this.silenceThresholdDisplay.textContent = `${(val / 1000).toFixed(1)}s`;
        window.flowStorage.saveSettings({ silenceThresholdMs: val });
      });
    }
    if (this.settingSpeechLang) {
      this.settingSpeechLang.addEventListener('change', () => {
        window.flowStorage.saveSettings({ speechLanguage: this.settingSpeechLang.value });
        this.showToast(`Voice Language set to: ${this.settingSpeechLang.value}`, 'info');
      });
    }
  }

  // --- VIEW SWITCHER ---
  switchView(viewId) {
    this.currentView = viewId;
    this.viewScreens.forEach(screen => {
      screen.classList.toggle('active', screen.id === viewId);
    });

    this.navBtns.forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-target') === viewId);
    });

    if (viewId === 'viewHistory') {
      this.renderHistory();
    } else if (viewId === 'viewDictionary') {
      this.renderDictionary();
    } else if (viewId === 'viewSettings') {
      this.loadSettings();
      this.renderStats();
    }
  }

  // --- FLOW MODES ---
  renderModePills() {
    if (!this.modePillsTrack) return;
    this.modePillsTrack.innerHTML = '';
    const modes = Object.values(window.FLOW_MODES || {});
    this.activeMode = window.flowStorage.getActiveMode();

    modes.forEach(mode => {
      const pill = document.createElement('button');
      pill.className = `mode-pill ${this.activeMode === mode.id ? 'active' : ''}`;
      pill.innerHTML = `
        <span class="mode-icon">${mode.icon}</span>
        <span class="mode-label">${mode.name}</span>
      `;

      pill.addEventListener('click', () => {
        this.modePillsTrack.querySelectorAll('.mode-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.setMode(mode.id);
      });

      this.modePillsTrack.appendChild(pill);
    });

    this.updateModeBanner();
  }

  setMode(modeId) {
    this.activeMode = modeId;
    window.flowStorage.setActiveMode(modeId);
    this.updateModeBanner();
    this.showToast(`Flow Mode: ${this.getActiveModeConfig().name}`, 'info');
  }

  getActiveModeConfig() {
    const modes = Object.values(window.FLOW_MODES || {});
    return modes.find(m => m.id === this.activeMode) || modes[0];
  }

  updateModeBanner() {
    const config = this.getActiveModeConfig();
    if (this.activeModeBannerName) this.activeModeBannerName.textContent = `${config.icon} ${config.name}`;
    if (this.activeModeBannerDesc) this.activeModeBannerDesc.textContent = config.desc;
  }

  // --- VOICE LISTENING & POLISHING PIPELINE ---
  async toggleListening(isKeyboardTarget = false) {
    if (this.isRecording) {
      this.stopListening(isKeyboardTarget);
    } else {
      this.startListening(isKeyboardTarget);
    }
  }

  async startListening(isKeyboardTarget = false) {
    this.isRecording = true;
    this.speechStartTime = Date.now();
    this.currentRawSpeech = '';
    this.isKeyboardTarget = isKeyboardTarget;

    // UI Updates
    if (this.btnMainMic) this.btnMainMic.classList.add('is-listening');
    if (this.micRipple) this.micRipple.classList.add('active');
    if (this.btnHudMic) this.btnHudMic.classList.add('is-listening');
    if (this.btnKeyboardMicSpace) this.btnKeyboardMicSpace.classList.add('is-listening');

    this.updateStatusPill('listening', 'Listening... speak your thoughts');
    if (this.hudStatusText) this.hudStatusText.textContent = 'Listening...';
    if (this.keyboardMicStatus) this.keyboardMicStatus.textContent = '🎙️ Listening...';

    if (this.liveInterimContainer) {
      this.liveInterimContainer.classList.add('active');
      if (this.liveInterimText) this.liveInterimText.textContent = 'Listening for speech...';
    }

    await window.flowSpeech.startListening();
  }

  async stopListening(isKeyboardTarget = false) {
    this.isRecording = false;

    if (this.btnMainMic) this.btnMainMic.classList.remove('is-listening');
    if (this.micRipple) this.micRipple.classList.remove('active');
    if (this.btnHudMic) this.btnHudMic.classList.remove('is-listening');
    if (this.btnKeyboardMicSpace) this.btnKeyboardMicSpace.classList.remove('is-listening');

    this.updateStatusPill('polishing', '✨ Polishing with Gemini AI...');
    if (this.hudStatusText) this.hudStatusText.textContent = 'AI Polishing...';
    if (this.keyboardMicStatus) this.keyboardMicStatus.textContent = '✨ Polishing with AI...';

    window.flowSpeech.stopListening();
  }

  handleInterimSpeech(fullRaw, currentInterim) {
    this.currentRawSpeech = fullRaw;

    if (this.liveInterimText) {
      this.liveInterimText.innerHTML = `${fullRaw} <span class="interim-highlight">${currentInterim}</span>`;
    }

    // Live WPM calculation
    const elapsedSec = Math.max(1, (Date.now() - this.speechStartTime) / 1000);
    const words = fullRaw.trim().split(/\s+/).filter(Boolean).length;
    const currentWpm = Math.round((words / elapsedSec) * 60);

    if (this.wpmLiveCounter && words > 1) {
      this.wpmLiveCounter.textContent = `${currentWpm} WPM • ${Math.round(currentWpm / 38)}x typing speed`;
    }
  }

  async handleFinalSpeech(finalSpeech, durationMs) {
    this.resetListeningUi();

    if (!finalSpeech || !finalSpeech.trim()) {
      this.updateStatusPill('ready', 'No speech detected • Tap mic or try Demo');
      if (this.keyboardMicStatus) this.keyboardMicStatus.textContent = 'Hold or Tap to Dictate with AI';
      this.showToast('No speech detected. Please speak clearly or tap Demo Test!', 'info');
      return;
    }

    const startTime = performance.now();
    const modeConfig = this.getActiveModeConfig();

    try {
      const polished = await window.flowAI.polishSpeech(finalSpeech, this.activeMode);
      const latencyMs = Math.round(performance.now() - startTime);

      this.currentPolishedText = polished;

      // Update UI Output Card
      this.displayPolishedResult(polished, finalSpeech, durationMs, latencyMs);

      // Save to History & Stats
      const historyItem = window.flowStorage.addHistoryItem({
        rawSpeech: finalSpeech,
        polishedText: polished,
        mode: this.activeMode,
        durationMs: durationMs,
        latencyMs: latencyMs
      });

      // Target Auto-actions
      const settings = window.flowStorage.getSettings();
      if (settings.autoCopyOnFinish) {
        this.copyToClipboard(polished, false); // silent copy
      }

      // If triggered from Android Keyboard test input, directly inject text
      if (this.isKeyboardTarget && this.keyboardTestInput) {
        this.keyboardTestInput.value = (this.keyboardTestInput.value ? this.keyboardTestInput.value + ' ' : '') + polished;
        if (this.keyboardMicStatus) this.keyboardMicStatus.textContent = '✓ Injected into text field!';
        this.showToast('✓ Injected directly into active keyboard field!', 'success');
      }

      this.updateStatusPill('copied', `✓ Ready (${latencyMs}ms) • Copied to clipboard`);
      this.renderStats();
    } catch (err) {
      console.error('Flow processing error:', err);
      this.displayPolishedResult(finalSpeech, finalSpeech, durationMs, 20);
      this.updateStatusPill('ready', 'Ready to Flow');
    }
  }

  displayPolishedResult(polished, raw, durationMs, latencyMs) {
    if (!this.outputCard || !this.outputContent) return;

    this.outputContent.textContent = polished;
    this.outputCard.classList.add('has-content');

    const config = this.getActiveModeConfig();
    if (this.outputModeBadge) {
      this.outputModeBadge.textContent = `${config.icon} ${config.name}`;
    }

    const words = polished.split(/\s+/).filter(Boolean).length;
    const wpm = Math.round((words / Math.max(0.5, durationMs / 60000)));

    if (this.outputMetaStats) {
      this.outputMetaStats.textContent = `${words} words • ${wpm} WPM • Latency: ${latencyMs}ms`;
    }

    if (this.liveLatencyPill) {
      this.liveLatencyPill.textContent = `⚡ ${latencyMs}ms AI latency`;
    }
  }

  async repolishWithTone(newTone) {
    if (!this.currentPolishedText && !this.currentRawSpeech) return;
    this.showToast(`Re-polishing in ${newTone} style...`, 'info');

    const sourceText = this.currentRawSpeech || this.currentPolishedText;
    const startTime = performance.now();
    const newPolished = await window.flowAI.polishSpeech(sourceText, newTone);
    const latency = Math.round(performance.now() - startTime);

    this.currentPolishedText = newPolished;
    this.displayPolishedResult(newPolished, sourceText, 2000, latency);

    window.flowStorage.addHistoryItem({
      rawSpeech: sourceText,
      polishedText: newPolished,
      mode: newTone,
      durationMs: 2000,
      latencyMs: latency
    });

    this.copyToClipboard(newPolished, true);
  }

  updateStatusPill(state, text) {
    if (!this.statusPill || !this.statusPillText) return;

    this.statusPill.className = `status-pill ${state}`;
    this.statusPillText.textContent = text;
  }

  handleSpeechStateChange(state, errorInfo) {
    console.log('Wispr Flow Speech State:', state, errorInfo);

    if (state === 'permission-denied') {
      this.resetListeningUi();
      this.updateStatusPill('ready', 'Microphone Permission Needed');
      this.showToast('Microphone permission required for voice dictation', 'warning');
    } else if (state === 'listening') {
      this.isRecording = true;
      if (this.btnMainMic) this.btnMainMic.classList.add('is-listening');
      if (this.micRipple) this.micRipple.classList.add('active');
      this.updateStatusPill('listening', '🎙️ Listening... speak naturally');
      if (this.liveInterimContainer) {
        this.liveInterimContainer.classList.add('active');
        if (this.liveInterimText) this.liveInterimText.textContent = 'Listening for speech...';
      }
    } else if (state === 'processing') {
      this.updateStatusPill('polishing', '✨ AI Polishing speech...');
    } else if (state === 'stopped') {
      this.resetListeningUi();
      if (!this.currentPolishedText) {
        this.updateStatusPill('ready', 'Tap mic or Spacebar to Flow');
      }
    } else if (state === 'error' || state === 'unsupported') {
      this.resetListeningUi();
      this.updateStatusPill('ready', 'Tap mic to retry or tap Demo test');

      // If continuous recognizer had an error on Android, fallback to standard voice dialog
      if (window.AndroidNative && typeof window.AndroidNative.launchSpeechDialog === 'function') {
        this.showToast('Switching to standard Android voice dialog...', 'info');
        const lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';
        window.AndroidNative.launchSpeechDialog(lang);
      } else {
        this.showToast('Voice engine unavailable or quiet. Tap Demo test for sample speech!', 'warning');
      }
    }
  }

  resetListeningUi() {
    this.isRecording = false;
    if (this.btnMainMic) this.btnMainMic.classList.remove('is-listening');
    if (this.micRipple) this.micRipple.classList.remove('active');
    if (this.btnHudMic) this.btnHudMic.classList.remove('is-listening');
    if (this.btnKeyboardMicSpace) this.btnKeyboardMicSpace.classList.remove('is-listening');
    if (this.liveInterimContainer) this.liveInterimContainer.classList.remove('active');
  }

  // --- AUDIO WAVEFORM VISUALIZER (60 FPS) ---
  initWaveCanvas() {
    if (!this.waveCanvas) return;
    const canvas = this.waveCanvas;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    try {
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      const w = Math.max(280, rect.width || 320);
      const h = Math.max(60, rect.height || 80);
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.scale(dpr, dpr);

      // Initial idle state wave
      this.drawIdleWave(ctx, w, h);
    } catch (e) {
      console.warn('Wave canvas init warning:', e);
    }
  }

  drawRoundedRect(ctx, x, y, width, height, radius) {
    if (typeof ctx.roundRect === 'function') {
      ctx.beginPath();
      ctx.roundRect(x, y, width, height, radius);
      return;
    }
    // Universal fallback for Android WebView / older browsers
    const r = Math.min(radius, width / 2, height / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + width - r, y);
    ctx.quadraticCurveTo(x + width, y, x + width, y + r);
    ctx.lineTo(x + width, y + height - r);
    ctx.quadraticCurveTo(x + width, y + height, x + width - r, y + height);
    ctx.lineTo(x + r, y + height);
    ctx.quadraticCurveTo(x, y + height, x, y + height - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  handleAudioWaveform(normalizedVolume, freqArray) {
    if (!this.waveCanvas) return;
    const canvas = this.waveCanvas;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width / (window.devicePixelRatio || 1);
    const height = canvas.height / (window.devicePixelRatio || 1);

    ctx.clearRect(0, 0, width, height);

    const bars = 36;
    const barWidth = Math.max(2, width / bars - 2);
    const centerY = height / 2;

    for (let i = 0; i < bars; i++) {
      const freqIdx = Math.floor((i / bars) * (freqArray ? freqArray.length : 128));
      const val = freqArray ? freqArray[freqIdx] / 255 : (normalizedVolume || 0.25);
      const barHeight = Math.max(4, val * (height * 0.85));

      const x = i * (barWidth + 2);
      const y = centerY - barHeight / 2;

      const gradient = ctx.createLinearGradient(0, y, 0, y + barHeight);
      gradient.addColorStop(0, '#6366f1');
      gradient.addColorStop(0.5, '#ec4899');
      gradient.addColorStop(1, '#06b6d4');

      ctx.fillStyle = gradient;
      this.drawRoundedRect(ctx, x, y, barWidth, barHeight, 4);
      ctx.fill();
    }
  }

  drawIdleWave(ctx, width, height) {
    ctx.clearRect(0, 0, width, height);
    const bars = 36;
    const barWidth = Math.max(2, width / bars - 2);
    const centerY = height / 2;

    ctx.fillStyle = 'rgba(99, 102, 241, 0.25)';
    for (let i = 0; i < bars; i++) {
      const h = 4 + Math.sin(i * 0.4) * 2;
      const x = i * (barWidth + 2);
      const y = centerY - h / 2;
      this.drawRoundedRect(ctx, x, y, barWidth, h, 2);
      ctx.fill();
    }
  }

  // --- ACTIONS: CLIPBOARD, SHARE, SPEECH ---
  async copyToClipboard(text, notify = true) {
    if (!text) return;

    // Use native Android clipboard if inside APK
    if (window.AndroidNative && typeof window.AndroidNative.copyToClipboard === 'function') {
      try {
        window.AndroidNative.copyToClipboard(text);
        if (notify) this.showToast('📋 Copied to clipboard!', 'success');
        return;
      } catch (e) {}
    }

    try {
      await navigator.clipboard.writeText(text);
      if (notify) this.showToast('📋 Copied polished text to clipboard!', 'success');
      if (window.flowSpeech) window.flowSpeech.triggerHaptic(25);
    } catch (e) {
      // Fallback
      const input = document.createElement('textarea');
      input.value = text;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      document.body.removeChild(input);
      if (notify) this.showToast('📋 Copied to clipboard!', 'success');
    }
  }

  async shareContent(text) {
    if (!text) return;
    if (navigator.share) {
      try {
        await navigator.share({
          title: 'Wispr Flow Voice Dictation',
          text: text
        });
      } catch (err) {
        console.log('Share dismissed');
      }
    } else {
      this.copyToClipboard(text);
      this.showToast('Share API not supported; copied text to clipboard instead', 'info');
    }
  }

  textToSpeech(text) {
    if (!text || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.05;
    window.speechSynthesis.speak(utterance);
    this.showToast('🔊 Speaking polished text...', 'info');
  }

  clearResult() {
    this.currentPolishedText = '';
    this.currentRawSpeech = '';
    if (this.outputCard) this.outputCard.classList.remove('has-content');
    if (this.outputContent) this.outputContent.textContent = '';
    this.updateStatusPill('ready', 'Tap mic or Spacebar to Flow');
  }

  // --- FLOATING HUD OVERLAY ---
  toggleFloatingHud(forceState) {
    if (window.AndroidNative && typeof window.AndroidNative.startFloatingOverlay === 'function') {
      try {
        window.AndroidNative.startFloatingOverlay();
        return;
      } catch (e) {}
    }

    if (!this.floatingHud) return;
    const newState = forceState !== undefined ? forceState : !this.floatingHud.classList.contains('active');
    this.floatingHud.classList.toggle('active', newState);

    if (newState) {
      this.showToast('Floating Flow HUD active! Position anywhere on screen.', 'info');
    }
  }

  // --- NATIVE ANDROID SYSTEM HELPERS ---
  openAndroidKeyboardSettings() {
    if (window.AndroidNative && typeof window.AndroidNative.openKeyboardSettings === 'function') {
      window.AndroidNative.openKeyboardSettings();
    } else {
      this.showToast('Open Android Settings > System > Languages > On-screen Keyboard to enable Wispr Flow', 'info');
    }
  }

  launchAndroidFloatingOverlay() {
    if (window.AndroidNative && typeof window.AndroidNative.startFloatingOverlay === 'function') {
      window.AndroidNative.startFloatingOverlay();
    } else {
      this.toggleFloatingHud(true);
    }
  }

  // --- QUICK DEMO PHRASE TEST BENCH ---
  async runDemoTest() {
    const demoPhrases = [
      "um basically you know we need to deploy the new microservice by Thursday afternoon so yeah please review the PR and let me know your thoughts",
      "hey team uh I was thinking we should reschedule the sync to Friday at 10 AM because like three people have conflicts",
      "so yeah basically action items from the call are one update the database schema two send the contract to the client and three check the logs",
      "could you please review the proposal and uh let me know if we need any revisions before signing"
    ];

    const sample = demoPhrases[Math.floor(Math.random() * demoPhrases.length)];

    this.showToast('💡 Running sample speech test with filler words...', 'info');
    this.updateStatusPill('listening', 'Simulating speech input...');
    if (this.liveInterimContainer) {
      this.liveInterimContainer.classList.add('active');
      if (this.liveInterimText) this.liveInterimText.textContent = sample;
    }

    // Pulse simulated wave
    if (window.flowSpeech && window.flowSpeech.onAudioVolumeCallback) {
      window.flowSpeech.onAudioVolumeCallback(0.75, null);
    }

    setTimeout(async () => {
      this.updateStatusPill('polishing', '✨ AI Polishing speech...');
      await this.handleFinalSpeech(sample, 2200);
      this.showToast('✓ Polished with Wispr Flow!', 'success');
    }, 700);
  }

  // --- ANDROID KEYBOARD COMPANION SIMULATION ---
  initKeyboardSimulation() {
    // When keys on the simulated mobile keyboard are tapped, insert character
    const keys = document.querySelectorAll('.kb-key[data-char]');
    keys.forEach(k => {
      k.addEventListener('click', () => {
        if (!this.keyboardTestInput) return;
        const char = k.getAttribute('data-char');
        if (char === 'backspace') {
          this.keyboardTestInput.value = this.keyboardTestInput.value.slice(0, -1);
        } else if (char === 'space') {
          this.keyboardTestInput.value += ' ';
        } else {
          this.keyboardTestInput.value += char;
        }
      });
    });
  }

  // --- PERSONAL VOCABULARY / DICTIONARY ---
  renderDictionary() {
    if (!this.dictList) return;
    const dict = window.flowStorage.getDictionary();
    this.dictList.innerHTML = '';

    if (dict.length === 0) {
      this.dictList.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">📖</div>
          <div class="empty-state-title">No Custom Vocabulary Yet</div>
          <div class="empty-state-desc">Add specialized terms, company names, or acronyms so Wispr Flow AI never misinterprets them.</div>
        </div>
      `;
      return;
    }

    dict.forEach(item => {
      const card = document.createElement('div');
      card.className = 'vocab-item-card';
      card.innerHTML = `
        <div class="vocab-info">
          <div class="vocab-word-row">
            <span class="vocab-word">${item.word}</span>
            <span class="vocab-arrow">→</span>
            <span class="vocab-replacement">${item.replacement}</span>
            <span class="vocab-category-tag">${item.category || 'tech'}</span>
          </div>
        </div>
        <button class="icon-btn delete" title="Delete word" onclick="window.app.deleteVocabWord('${item.id}')">✕</button>
      `;
      this.dictList.appendChild(card);
    });
  }

  handleAddDictionaryWord() {
    const word = this.inputNewWord ? this.inputNewWord.value.trim() : '';
    const replacement = this.inputNewReplacement ? this.inputNewReplacement.value.trim() : word;
    const cat = this.selectNewCategory ? this.selectNewCategory.value : 'custom';

    if (!word) {
      alert('Please enter a word or phrase to teach Wispr Flow.');
      return;
    }

    window.flowStorage.addDictionaryWord(word, replacement, cat);
    if (this.inputNewWord) this.inputNewWord.value = '';
    if (this.inputNewReplacement) this.inputNewReplacement.value = '';

    this.renderDictionary();
    this.showToast(`✓ Added "${word}" to personal AI vocabulary`, 'success');
  }

  deleteVocabWord(id) {
    window.flowStorage.deleteDictionaryWord(id);
    this.renderDictionary();
    this.showToast('Vocabulary term removed', 'info');
  }

  // --- FLOW HISTORY ---
  renderHistory() {
    if (!this.historyFeed) return;
    const history = window.flowStorage.getHistory();
    const query = this.inputSearchHistory ? this.inputSearchHistory.value.toLowerCase().trim() : '';

    let filtered = history;
    if (query) {
      filtered = filtered.filter(h => {
        return (h.polishedText + ' ' + h.rawSpeech + ' ' + h.mode).toLowerCase().includes(query);
      });
    }

    this.historyFeed.innerHTML = '';

    if (filtered.length === 0) {
      this.historyFeed.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">🎙️</div>
          <div class="empty-state-title">No Voice Transcriptions</div>
          <div class="empty-state-desc">Your voice dictations and polished notes will appear here automatically.</div>
        </div>
      `;
      return;
    }

    filtered.forEach(item => {
      const card = document.createElement('div');
      card.className = 'history-item-card';

      const timeAgo = this.formatTimeAgo(item.timestamp);
      const config = window.FLOW_MODES[item.mode ? item.mode.toUpperCase() : 'POLISH'] || { name: item.mode, icon: '✨' };

      card.innerHTML = `
        <div class="history-item-header">
          <span class="mode-badge-mini">${config.icon} ${config.name}</span>
          <span class="history-time-meta">${timeAgo} • ${item.wordCount || 0} words • ${item.wpm || 140} WPM</span>
          <button class="icon-btn-star ${item.isFavorite ? 'active' : ''}" onclick="window.app.toggleHistoryFav('${item.id}')">★</button>
        </div>
        <div class="history-polished-body">${item.polishedText}</div>
        <div class="history-item-footer">
          <button class="btn-action-sm" onclick="window.app.copyToClipboard('${item.polishedText.replace(/'/g, "\\'")}')">
            📋 Copy
          </button>
          <button class="btn-action-sm" onclick="window.app.shareContent('${item.polishedText.replace(/'/g, "\\'")}')">
            📱 Share
          </button>
          <button class="btn-text-sm delete" onclick="window.app.deleteHistoryEntry('${item.id}')" style="margin-left: auto;">
            Delete
          </button>
        </div>
      `;

      this.historyFeed.appendChild(card);
    });
  }

  toggleHistoryFav(id) {
    window.flowStorage.toggleFavoriteHistory(id);
    this.renderHistory();
  }

  deleteHistoryEntry(id) {
    window.flowStorage.deleteHistoryItem(id);
    this.renderHistory();
    this.showToast('Item deleted', 'info');
  }

  formatTimeAgo(isoString) {
    if (!isoString) return 'Just now';
    const diff = Math.round((Date.now() - new Date(isoString).getTime()) / 1000);
    if (diff < 60) return 'Just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  }

  // --- STATS & SPEEDOMETER ---
  renderStats() {
    const stats = window.flowStorage.getStats();

    if (this.statWordsSpoken) this.statWordsSpoken.textContent = stats.totalWordsSpoken.toLocaleString();

    if (this.statTimeSaved) {
      const minutes = Math.round(stats.totalTimeSavedSeconds / 60);
      this.statTimeSaved.textContent = minutes >= 60 ? `${(minutes / 60).toFixed(1)} hrs` : `${minutes} mins`;
    }

    if (this.statAverageWpm) this.statAverageWpm.textContent = `${stats.averageWpm || 140} WPM`;
  }

  // --- SETTINGS VIEW ---
  loadSettings() {
    const settings = window.flowStorage.getSettings();

    if (this.settingGeminiApiKey) {
      this.settingGeminiApiKey.value = settings.geminiApiKey || '';
    }
    if (this.settingAutoCopy) {
      this.settingAutoCopy.checked = Boolean(settings.autoCopyOnFinish);
    }
    if (this.settingHaptic) {
      this.settingHaptic.checked = Boolean(settings.playHapticFeedback);
    }
    if (this.settingSilenceThreshold) {
      this.settingSilenceThreshold.value = settings.silenceThresholdMs || 1400;
      if (this.silenceThresholdDisplay) this.silenceThresholdDisplay.textContent = `${((settings.silenceThresholdMs || 1400) / 1000).toFixed(1)}s`;
    }
    if (this.settingSpeechLang && settings.speechLanguage) {
      this.settingSpeechLang.value = settings.speechLanguage;
    }

    this.populateModelsSelect();
  }

  populateModelsSelect() {
    if (!this.settingModelSelect) return;
    const selected = window.flowStorage.getSelectedModel();

    const supportedModels = [
      { id: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash (Recommended - Sub-second Latency)' },
      { id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash (Standard)' },
      { id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro (Deep Reasoning & Complex Rewrites)' }
    ];

    this.settingModelSelect.innerHTML = '';
    supportedModels.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = m.name;
      if (m.id === selected) opt.selected = true;
      this.settingModelSelect.appendChild(opt);
    });
  }

  async fetchAvailableModels() {
    const key = (this.settingGeminiApiKey ? this.settingGeminiApiKey.value : '').trim();
    if (!key) {
      alert('Please enter your Google Gemini API key first to detect models.');
      return;
    }

    this.showToast('Connecting to Gemini API to query supported models...', 'info');
    try {
      const models = await window.flowAI.fetchAvailableModels(key);
      this.showToast(`✓ Retrieved ${models.length} active models from Google Gemini!`, 'success');
    } catch (e) {
      this.showToast(`Fetch notice: ${e.message}`, 'error');
    }
  }

  showToast(message, type = 'info') {
    if (!this.toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast-msg ${type}`;
    toast.textContent = message;

    this.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 2800);
  }
}

// Global Wispr Flow App Singleton
window.app = new FlowAppController();
