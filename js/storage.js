/**
 * Wispr Flow - Storage & Local Intelligence Service
 * Handles persistence for Flow history, personal dictionary, custom vocabulary,
 * quick voice snippets, user settings, and typing speed analytics.
 */

const STORAGE_KEYS = {
  SETTINGS: 'wisprflow_settings_v1',
  HISTORY: 'wisprflow_history_v1',
  DICTIONARY: 'wisprflow_dictionary_v1',
  SNIPPETS: 'wisprflow_snippets_v1',
  STATS: 'wisprflow_stats_v1',
  ACTIVE_MODE: 'wisprflow_active_mode_v1'
};

// Default Flow Modes
const FLOW_MODES = {
  POLISH: {
    id: 'polish',
    name: 'Smart Polish',
    icon: '✨',
    badge: 'Wispr Default',
    desc: 'Removes "um/uh", fixes stutters, adds perfect punctuation while keeping your natural voice.',
    targetApp: 'Universal / All Apps'
  },
  CASUAL: {
    id: 'casual',
    name: 'Casual Chat',
    icon: '💬',
    badge: 'WhatsApp & DMs',
    desc: 'Friendly, concise, conversational tone. Perfect for WhatsApp, Slack DMs, Telegram, and texting.',
    targetApp: 'WhatsApp, Telegram, Slack'
  },
  EMAIL: {
    id: 'email',
    name: 'Executive Email',
    icon: '✉️',
    badge: 'Work & Clients',
    desc: 'Polished professional email with proper greeting, structured paragraphs, and sign-off.',
    targetApp: 'Gmail, Outlook, Superhuman'
  },
  NOTES: {
    id: 'notes',
    name: 'Structured Notes',
    icon: '📝',
    badge: 'Brain Dump',
    desc: 'Organizes rambling thoughts into structured Markdown with headings, bullet points, and action items.',
    targetApp: 'Notion, Apple Notes, Obsidian'
  },
  DIRECT: {
    id: 'direct',
    name: 'Raw Dictation',
    icon: '⚡',
    badge: '1:1 Exact',
    desc: 'Zero alteration. Transcribes exact words with precision punctuation and zero hallucinations.',
    targetApp: 'Form Fields, Search Bars'
  },
  CODE: {
    id: 'code',
    name: 'Code & Dev',
    icon: '💻',
    badge: 'Technical',
    desc: 'camelCase variables, SQL queries, markdown code blocks, and terminal commands.',
    targetApp: 'GitHub, Terminal, VS Code'
  },
  COMMAND: {
    id: 'command',
    name: 'Voice Prompt / AI',
    icon: '🪄',
    badge: 'AI Action',
    desc: 'Speak an instruction (e.g. "Draft reply declining politely") and AI outputs the completed task.',
    targetApp: 'AI Assistant, Drafting'
  }
};

class FlowStorageService {
  constructor() {
    this.initDefaults();
  }

  initDefaults() {
    // Initialize default dictionary if empty
    if (!localStorage.getItem(STORAGE_KEYS.DICTIONARY)) {
      const defaultVocab = [
        { id: 'v1', word: 'Kubernetes', replacement: 'Kubernetes (K8s)', category: 'tech' },
        { id: 'v2', word: 'PostgreSQL', replacement: 'PostgreSQL', category: 'tech' },
        { id: 'v3', word: 'Wispr Flow', replacement: 'Wispr Flow', category: 'brand' },
        { id: 'v4', word: 'API', replacement: 'API', category: 'acronym' },
        { id: 'v5', word: 'SaaS', replacement: 'SaaS', category: 'acronym' }
      ];
      this.saveDictionary(defaultVocab);
    }

    // Initialize stats if empty
    if (!localStorage.getItem(STORAGE_KEYS.STATS)) {
      this.saveStats({
        totalWordsSpoken: 0,
        totalDictations: 0,
        totalTimeSavedSeconds: 0, // estimated vs 40 WPM average mobile typing
        averageWpm: 140
      });
    }
  }

  // --- SETTINGS ---
  getSettings() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      return data ? JSON.parse(data) : {
        geminiApiKey: '',
        selectedModel: 'gemini-2.0-flash', // Ultra-low latency Google DeepMind model
        autoCopyOnFinish: true,
        playHapticFeedback: true,
        continuousListening: false,
        silenceThresholdMs: 1400,
        floatingHudEnabled: true,
        keyboardCompanionActive: true,
        speechLanguage: 'en-US'
      };
    } catch (e) {
      return {
        geminiApiKey: '',
        selectedModel: 'gemini-2.0-flash',
        autoCopyOnFinish: true,
        playHapticFeedback: true,
        continuousListening: false,
        silenceThresholdMs: 1400,
        floatingHudEnabled: true,
        keyboardCompanionActive: true,
        speechLanguage: 'en-US'
      };
    }
  }

  saveSettings(newSettings) {
    const current = this.getSettings();
    const updated = { ...current, ...newSettings };
    localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(updated));
    return updated;
  }

  getGeminiApiKey() {
    return this.getSettings().geminiApiKey || '';
  }

  setGeminiApiKey(key) {
    this.saveSettings({ geminiApiKey: (key || '').trim() });
  }

  getSelectedModel() {
    return this.getSettings().selectedModel || 'gemini-2.0-flash';
  }

  setSelectedModel(modelId) {
    this.saveSettings({ selectedModel: modelId });
  }

  // --- ACTIVE MODE ---
  getActiveMode() {
    return localStorage.getItem(STORAGE_KEYS.ACTIVE_MODE) || 'polish';
  }

  setActiveMode(modeId) {
    if (FLOW_MODES[modeId.toUpperCase()] || Object.values(FLOW_MODES).some(m => m.id === modeId)) {
      localStorage.setItem(STORAGE_KEYS.ACTIVE_MODE, modeId);
    }
  }

  // --- FLOW HISTORY ---
  getHistory() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.HISTORY);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      console.error('Failed to get history:', e);
      return [];
    }
  }

  saveHistory(history) {
    localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(history));
  }

  addHistoryItem(item) {
    const history = this.getHistory();
    const wordCount = (item.polishedText || item.rawSpeech || '').trim().split(/\s+/).filter(Boolean).length;

    const newItem = {
      id: 'flow_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
      rawSpeech: item.rawSpeech || '',
      polishedText: item.polishedText || item.rawSpeech || '',
      mode: item.mode || this.getActiveMode(),
      durationMs: item.durationMs || 2500,
      wpm: item.wpm || Math.round((wordCount / Math.max(1, (item.durationMs || 2500) / 60000))),
      latencyMs: item.latencyMs || 280,
      wordCount: wordCount,
      timestamp: new Date().toISOString(),
      isFavorite: false,
      copiedCount: 1
    };

    history.unshift(newItem);
    // Keep last 150 items for optimal performance
    if (history.length > 150) history.pop();
    this.saveHistory(history);

    // Update global typing stats
    this.incrementStats(wordCount, newItem.durationMs);

    return newItem;
  }

  deleteHistoryItem(id) {
    let history = this.getHistory();
    history = history.filter(h => h.id !== id);
    this.saveHistory(history);
  }

  clearHistory() {
    this.saveHistory([]);
  }

  toggleFavoriteHistory(id) {
    const history = this.getHistory();
    const item = history.find(h => h.id === id);
    if (item) {
      item.isFavorite = !item.isFavorite;
      this.saveHistory(history);
      return item.isFavorite;
    }
    return false;
  }

  // --- CUSTOM VOCABULARY & PERSONAL DICTIONARY ---
  getDictionary() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.DICTIONARY);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      return [];
    }
  }

  saveDictionary(dict) {
    localStorage.setItem(STORAGE_KEYS.DICTIONARY, JSON.stringify(dict));
  }

  addDictionaryWord(word, replacement, category = 'custom') {
    const dict = this.getDictionary();
    const cleanWord = (word || '').trim();
    if (!cleanWord) return null;

    // Check existing
    const existingIndex = dict.findIndex(d => d.word.toLowerCase() === cleanWord.toLowerCase());
    const newItem = {
      id: 'vocab_' + Date.now(),
      word: cleanWord,
      replacement: (replacement || cleanWord).trim(),
      category: category,
      addedAt: new Date().toISOString()
    };

    if (existingIndex >= 0) {
      dict[existingIndex] = newItem;
    } else {
      dict.unshift(newItem);
    }

    this.saveDictionary(dict);
    return newItem;
  }

  deleteDictionaryWord(id) {
    let dict = this.getDictionary();
    dict = dict.filter(d => d.id !== id);
    this.saveDictionary(dict);
  }

  // --- TYPING SPEED & TIME SAVED STATS ---
  getStats() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.STATS);
      return data ? JSON.parse(data) : {
        totalWordsSpoken: 0,
        totalDictations: 0,
        totalTimeSavedSeconds: 0,
        averageWpm: 140
      };
    } catch (e) {
      return { totalWordsSpoken: 0, totalDictations: 0, totalTimeSavedSeconds: 0, averageWpm: 140 };
    }
  }

  saveStats(stats) {
    localStorage.setItem(STORAGE_KEYS.STATS, JSON.stringify(stats));
  }

  incrementStats(words, durationMs) {
    const stats = this.getStats();
    stats.totalWordsSpoken += words;
    stats.totalDictations += 1;

    // Standard mobile thumb typing speed is approx 38 WPM (0.63 words/second).
    // Speaking speed with Wispr Flow is approx 140-160 WPM (2.5 words/second).
    // Time saved calculation:
    const typingTimeSec = (words / 38) * 60;
    const voiceTimeSec = (durationMs / 1000);
    const savedSec = Math.max(0, typingTimeSec - voiceTimeSec);

    stats.totalTimeSavedSeconds += Math.round(savedSec);

    if (durationMs > 1000 && words > 1) {
      const currentWpm = Math.round((words / (durationMs / 60000)));
      stats.averageWpm = Math.round((stats.averageWpm * 0.7) + (currentWpm * 0.3));
    }

    this.saveStats(stats);
    return stats;
  }
}

// Global Storage Singleton
window.flowStorage = new FlowStorageService();
window.FLOW_MODES = FLOW_MODES;
