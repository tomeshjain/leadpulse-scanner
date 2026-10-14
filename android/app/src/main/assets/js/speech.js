/**
 * Wispr Flow - Low-Latency Speech Recognition & Audio Wave Engine
 * Supports:
 * 1. Native Android SpeechRecognizer bridge via window.AndroidNative (for Android APK & WebView)
 * 2. Web Speech API (window.SpeechRecognition / window.webkitSpeechRecognition for Chrome, Edge, Safari)
 * 3. 60 FPS Web Audio frequency visualization with graceful fallback for insecure HTTP
 * 4. Sample test speech injection for zero-friction testing
 */

class FlowSpeechEngine {
  constructor() {
    this.recognition = null;
    this.isListening = false;
    this.audioContext = null;
    this.analyser = null;
    this.microphoneStream = null;
    this.dataArray = null;
    this.simulatedVolumeInterval = null;

    this.rawTranscript = '';
    this.interimTranscript = '';
    this.startTime = 0;
    this.silenceTimer = null;
    this.silenceThresholdMs = 1500; // auto-finish after 1.5s of silence

    // Detect environment
    this.isNative = !!(window.AndroidNative && typeof window.AndroidNative.isNativeApp === 'function' && window.AndroidNative.isNativeApp());

    // Callbacks
    this.onInterimCallback = null;
    this.onFinalCallback = null;
    this.onStateChangeCallback = null;
    this.onAudioVolumeCallback = null;

    if (!this.isNative) {
      this.initSpeechRecognition();
    }
  }

  /**
   * Check if speech recognition is supported in current environment
   */
  isSupported() {
    if (this.isNative) return true;
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  }

  /**
   * Initializes browser speech recognition with maximum speed flags
   */
  initSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.warn('SpeechRecognition API not natively available in this browser. Native bridge or test mode active.');
      return false;
    }

    try {
      this.recognition = new SpeechRecognition();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.maxAlternatives = 1;
      this.recognition.lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';

      this.recognition.onstart = () => {
        this.isListening = true;
        this.startTime = Date.now();
        this.triggerHaptic(20);
        if (this.onStateChangeCallback) this.onStateChangeCallback('listening');
      };

      this.recognition.onresult = (event) => {
        this.resetSilenceTimer();
        let currentInterim = '';

        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const res = event.results[i];
          const text = res[0].transcript;
          if (res.isFinal) {
            this.rawTranscript += ' ' + text;
          } else {
            currentInterim += text;
          }
        }

        this.interimTranscript = currentInterim;

        // Fire instant streaming callback (<20ms perceived latency)
        if (this.onInterimCallback) {
          const combined = (this.rawTranscript + ' ' + this.interimTranscript).trim();
          this.onInterimCallback(combined, currentInterim);
        }
      };

      this.recognition.onerror = (event) => {
        console.warn('Speech recognition event:', event.error);
        if (event.error === 'not-allowed') {
          if (this.onStateChangeCallback) this.onStateChangeCallback('permission-denied');
        } else if (event.error === 'no-speech') {
          // Normal timeout when quiet, handled gracefully
        }
      };

      this.recognition.onend = () => {
        if (this.isListening) {
          // Restart if user hasn't explicitly stopped
          try {
            this.recognition.start();
          } catch (e) {
            this.stopListening();
          }
        }
      };

      return true;
    } catch (e) {
      console.error('Failed to init speech recognition:', e);
      return false;
    }
  }

  /**
   * Starts listening (routes to Android Native bridge or Web Speech API)
   */
  async startListening() {
    if (this.isListening) return;

    this.rawTranscript = '';
    this.interimTranscript = '';
    this.startTime = Date.now();
    this.isListening = true;

    // Check if running inside Android APK
    this.isNative = !!(window.AndroidNative && typeof window.AndroidNative.isNativeApp === 'function' && window.AndroidNative.isNativeApp());

    const lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';

    // 1. Try launching Web Audio analyser for 60fps waveform (safe non-blocking)
    try {
      await this.initAudioAnalyser();
    } catch (e) {
      console.log('Audio analyser fallback:', e.message);
      this.startSimulatedAudioVolume();
    }

    // 2. Start speech recognizer
    if (this.isNative) {
      try {
        console.log('Starting native Android SpeechRecognizer...');
        window.AndroidNative.startNativeSpeech(lang);
        if (this.onStateChangeCallback) this.onStateChangeCallback('listening');
      } catch (err) {
        console.error('Failed to start Android Native speech:', err);
        if (this.onStateChangeCallback) this.onStateChangeCallback('error');
      }
    } else {
      // Browser Web Speech API
      if (!this.recognition) {
        this.initSpeechRecognition();
      }

      if (this.recognition) {
        try {
          this.recognition.lang = lang;
          this.recognition.start();
        } catch (e) {
          console.log('Recognition start note:', e.message);
        }
      } else {
        console.warn('Speech recognition not supported in this browser.');
        if (this.onStateChangeCallback) this.onStateChangeCallback('unsupported');
      }
    }

    if (this.onStateChangeCallback) this.onStateChangeCallback('listening');
  }

  /**
   * Stops listening and returns duration & transcript
   */
  stopListening() {
    if (!this.isListening) return { speech: '', durationMs: 0 };

    this.isListening = false;
    this.clearSilenceTimer();

    const durationMs = Math.max(500, Date.now() - this.startTime);
    const finalSpeech = (this.rawTranscript + ' ' + this.interimTranscript).trim();

    // Stop native Android or Web Speech
    if (this.isNative && window.AndroidNative && typeof window.AndroidNative.stopNativeSpeech === 'function') {
      try {
        window.AndroidNative.stopNativeSpeech();
      } catch (e) {}
    } else if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (e) {}
    }

    this.stopAudioAnalyser();
    this.stopSimulatedAudioVolume();
    this.triggerHaptic([15, 30]);

    if (this.onStateChangeCallback) this.onStateChangeCallback('stopped');

    if (this.onFinalCallback) {
      this.onFinalCallback(finalSpeech, durationMs);
    }

    return { speech: finalSpeech, durationMs };
  }

  // --- ANDROID NATIVE BRIDGE CALLBACKS (Invoked by MainActivity.java) ---

  onNativeStateChange(state) {
    console.log('Android Native State:', state);
    if (state === 'listening') {
      this.isListening = true;
      this.startTime = Date.now();
      if (this.onStateChangeCallback) this.onStateChangeCallback('listening');
    } else if (state === 'processing') {
      if (this.onStateChangeCallback) this.onStateChangeCallback('processing');
    }
  }

  onNativeVolume(normalizedVolume) {
    // Forward native volume (0.0 to 1.0) directly to audio wave visualizer
    if (this.onAudioVolumeCallback) {
      this.onAudioVolumeCallback(normalizedVolume, null);
    }
  }

  onNativeResult(recognizedText, isFinal) {
    console.log('Android Native Result:', { recognizedText, isFinal });
    const text = (recognizedText || '').trim();

    if (!isFinal) {
      this.interimTranscript = text;
      if (this.onInterimCallback) {
        this.onInterimCallback(text, text);
      }
    } else {
      this.isListening = false;
      this.rawTranscript = text;
      this.interimTranscript = '';
      const durationMs = Math.max(600, Date.now() - this.startTime);

      this.stopAudioAnalyser();
      this.stopSimulatedAudioVolume();
      this.triggerHaptic([20, 40]);

      if (this.onStateChangeCallback) this.onStateChangeCallback('stopped');
      if (this.onFinalCallback) {
        this.onFinalCallback(text, durationMs);
      }
    }
  }

  onNativeError(error) {
    console.warn('Android Native Speech Error:', error);
    this.isListening = false;
    this.stopAudioAnalyser();
    this.stopSimulatedAudioVolume();
    if (error === 'permission-denied' || error === 9) {
      if (this.onStateChangeCallback) this.onStateChangeCallback('permission-denied');
    } else {
      if (this.onStateChangeCallback) this.onStateChangeCallback('error', error);
    }
  }

  launchNativeSpeechDialog() {
    if (this.isNative && window.AndroidNative && typeof window.AndroidNative.launchSpeechDialog === 'function') {
      const lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';
      window.AndroidNative.launchSpeechDialog(lang);
      return true;
    }
    return false;
  }

  // --- WEB AUDIO FREQUENCY ANALYSER (60 FPS) ---

  async initAudioAnalyser() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      console.log('navigator.mediaDevices not available, using simulated wave.');
      this.startSimulatedAudioVolume();
      return;
    }

    try {
      if (!this.microphoneStream) {
        this.microphoneStream = await navigator.mediaDevices.getUserMedia({
          audio: {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
          }
        });
      }

      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) {
        this.startSimulatedAudioVolume();
        return;
      }

      this.audioContext = new AudioCtx();
      if (this.audioContext.state === 'suspended') {
        await this.audioContext.resume();
      }

      const source = this.audioContext.createMediaStreamSource(this.microphoneStream);
      this.analyser = this.audioContext.createAnalyser();
      this.analyser.fftSize = 128;
      this.analyser.smoothingTimeConstant = 0.8;

      source.connect(this.analyser);
      this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);

      this.pollAudioFrequency();
    } catch (err) {
      console.warn('Web Audio mic stream unavailable:', err.message);
      this.startSimulatedAudioVolume();
    }
  }

  pollAudioFrequency() {
    if (!this.isListening || !this.analyser) return;

    this.analyser.getByteFrequencyData(this.dataArray);

    let sum = 0;
    for (let i = 0; i < this.dataArray.length; i++) {
      sum += this.dataArray[i];
    }
    const average = sum / this.dataArray.length;
    const normalizedVolume = Math.min(1, average / 110);

    if (this.onAudioVolumeCallback) {
      this.onAudioVolumeCallback(normalizedVolume, this.dataArray);
    }

    requestAnimationFrame(() => this.pollAudioFrequency());
  }

  startSimulatedAudioVolume() {
    this.stopSimulatedAudioVolume();
    let tick = 0;
    this.simulatedVolumeInterval = setInterval(() => {
      if (!this.isListening) {
        this.stopSimulatedAudioVolume();
        return;
      }
      tick += 0.15;
      const vol = 0.35 + Math.sin(tick) * 0.25 + Math.sin(tick * 2.3) * 0.15;
      if (this.onAudioVolumeCallback) {
        this.onAudioVolumeCallback(Math.max(0.1, Math.min(1, vol)), null);
      }
    }, 50);
  }

  stopSimulatedAudioVolume() {
    if (this.simulatedVolumeInterval) {
      clearInterval(this.simulatedVolumeInterval);
      this.simulatedVolumeInterval = null;
    }
  }

  stopAudioAnalyser() {
    if (this.microphoneStream) {
      try {
        this.microphoneStream.getTracks().forEach(t => t.stop());
      } catch (e) {}
      this.microphoneStream = null;
    }
    if (this.audioContext && this.audioContext.state !== 'closed') {
      try {
        this.audioContext.close();
      } catch (e) {}
      this.audioContext = null;
    }
    this.analyser = null;
  }

  resetSilenceTimer() {
    this.clearSilenceTimer();
    const settings = window.flowStorage ? window.flowStorage.getSettings() : {};
    const threshold = settings.silenceThresholdMs || 1500;

    this.silenceTimer = setTimeout(() => {
      if (this.isListening && (this.rawTranscript.trim() || this.interimTranscript.trim())) {
        console.log('Wispr Flow: Silence detected, committing speech...');
        this.stopListening();
      }
    }, threshold);
  }

  clearSilenceTimer() {
    if (this.silenceTimer) {
      clearTimeout(this.silenceTimer);
      this.silenceTimer = null;
    }
  }

  triggerHaptic(pattern) {
    if (this.isNative && window.AndroidNative && typeof window.AndroidNative.triggerHaptic === 'function') {
      try {
        const ms = Array.isArray(pattern) ? pattern[0] : pattern;
        window.AndroidNative.triggerHaptic(ms || 25);
        return;
      } catch (e) {}
    }

    if (navigator.vibrate && window.flowStorage && window.flowStorage.getSettings().playHapticFeedback) {
      try {
        navigator.vibrate(pattern);
      } catch (e) {}
    }
  }
}

// Global Speech Engine Singleton
window.flowSpeech = new FlowSpeechEngine();
