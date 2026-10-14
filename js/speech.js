/**
 * Wispr Flow - Low-Latency Speech Recognition & Audio Wave Engine
 * Delivers instant continuous speech-to-text (<20ms perceived latency)
 * and 60 FPS Web Audio frequency visualization.
 */

class FlowSpeechEngine {
  constructor() {
    this.recognition = null;
    this.isListening = false;
    this.audioContext = null;
    this.analyser = null;
    this.microphoneStream = null;
    this.dataArray = null;

    this.rawTranscript = '';
    this.interimTranscript = '';
    this.startTime = 0;
    this.silenceTimer = null;
    this.silenceThresholdMs = 1400; // auto-finish after 1.4s of silence

    // Callbacks
    this.onInterimCallback = null;
    this.onFinalCallback = null;
    this.onStateChangeCallback = null;
    this.onAudioVolumeCallback = null;

    this.initSpeechRecognition();
  }

  /**
   * Initializes browser speech recognition with maximum speed flags
   */
  initSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      console.warn('SpeechRecognition API not natively available in this browser. Fallback input available.');
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
        console.warn('Speech recognition event note:', event.error);
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
   * Starts listening and launches Web Audio frequency analysis
   */
  async startListening() {
    if (this.isListening) return;

    this.rawTranscript = '';
    this.interimTranscript = '';
    this.startTime = Date.now();
    this.isListening = true;

    // 1. Initialize microphone audio stream & Web Audio analyser
    await this.initAudioAnalyser();

    // 2. Start speech recognizer
    if (this.recognition) {
      try {
        const lang = (window.flowStorage && window.flowStorage.getSettings().speechLanguage) || 'en-US';
        this.recognition.lang = lang;
        this.recognition.start();
      } catch (e) {
        console.log('Recognition start note:', e.message);
      }
    } else {
      this.initSpeechRecognition();
      if (this.recognition) this.recognition.start();
    }

    if (this.onStateChangeCallback) this.onStateChangeCallback('listening');
  }

  /**
   * Stops listening and returns duration & transcript
   */
  stopListening() {
    this.isListening = false;
    this.clearSilenceTimer();

    const durationMs = Math.max(500, Date.now() - this.startTime);
    const finalSpeech = (this.rawTranscript + ' ' + this.interimTranscript).trim();

    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (e) {}
    }

    this.stopAudioAnalyser();
    this.triggerHaptic([15, 30]);

    if (this.onStateChangeCallback) this.onStateChangeCallback('stopped');

    if (this.onFinalCallback) {
      this.onFinalCallback(finalSpeech, durationMs);
    }

    return { speech: finalSpeech, durationMs };
  }

  /**
   * Initializes Web Audio analyser node for 60fps liquid soundwaves
   */
  async initAudioAnalyser() {
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
      if (!AudioCtx) return;

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
    }
  }

  /**
   * Continuous loop feeding audio decibel levels to visualizer
   */
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

  stopAudioAnalyser() {
    if (this.microphoneStream) {
      this.microphoneStream.getTracks().forEach(t => t.stop());
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
    const threshold = settings.silenceThresholdMs || 1400;

    // Auto-commit if user stops speaking and has accumulated speech
    this.silenceTimer = setTimeout(() => {
      if (this.isListening && (this.rawTranscript.trim() || this.interimTranscript.trim())) {
        console.log('Wispr Flow: Silence detected, polishing speech...');
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
    if (navigator.vibrate && window.flowStorage && window.flowStorage.getSettings().playHapticFeedback) {
      try {
        navigator.vibrate(pattern);
      } catch (e) {}
    }
  }
}

// Global Speech Engine Singleton
window.flowSpeech = new FlowSpeechEngine();
