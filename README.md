# 🎙️ Wispr Flow - Universal AI Voice Keyboard & Dictation

> **Low-Latency AI Voice Recognition that replaces typing across your entire mobile phone.**

Wispr Flow is an AI-powered voice dictation and universal keyboard application. It transforms natural, rambling speech into clean, professionally formatted text in **sub-second latency** (<300ms) using **Google Gemini 2.0 Flash** and continuous streaming speech recognition.

---

## 🚀 Key Innovations

### 1. Dual-Engine Low-Latency Recognition
* **Tier 1 (Instant Live Feedback, <20ms)**: Real-time continuous speech recognition with interim syllable streaming. Words light up on screen the millisecond you speak them.
* **Tier 2 (Gemini 2.0 Flash Flow Engine, ~200ms)**: Sub-second AI polishing that strips filler words (*"um", "uh", "like", "you know", "basically"*), eliminates stuttering, and inlines perfect punctuation and capitalization.
* **60 FPS Liquid Audio Waveform**: Built with the Web Audio API (`AudioContext` + `AnalyserNode`), rendering real-time responsive neon soundwaves reacting to your vocal pitch and volume.

---

## 📱 How Wispr Flow Replaces Mobile Typing System-Wide

### A. Floating Voice Overlay HUD
A draggable, compact microphone bubble that hovers on top of any active Android application (**WhatsApp, Chrome, Slack, Telegram, Gmail, Instagram, Notes**).
1. Tap the floating mic.
2. Speak your thoughts naturally.
3. Release or pause — Wispr Flow automatically polishes the text and **copies it to your system clipboard** with a 1-tap instant paste button.

### B. Android Custom Keyboard (Input Method Service)
Wispr Flow includes a native Android `InputMethodService` that registers as a full system keyboard:
1. Enable **Wispr Flow AI Keyboard** in **Android Settings > System > Languages & Input > On-Screen Keyboard**.
2. When typing in any app, tap the central **Flow AI Mic Spacebar**.
3. Speech is captured, refined with AI, and **directly injected into the active text field** (`InputConnection.commitText()`).

---

## 🎨 Multi-Flow Tone Presets

| Mode | Target Apps | Formatting Behavior |
| :--- | :--- | :--- |
| **✨ Smart Polish** | Universal | Strips filler words, corrects stutters, adds natural punctuation while preserving your authentic voice. |
| **💬 Casual Chat** | WhatsApp, Telegram, Slack | Conversational, friendly, concise, with contextual emojis where appropriate. |
| **✉️ Executive Email** | Gmail, Outlook, Clients | Formats into structured business emails with greetings, clean paragraphs, and sign-offs. |
| **📝 Structured Notes** | Notion, Apple Notes, Obsidian | Converts rambling thoughts into Markdown `# Headings`, bullet points, and `[ ]` action checkboxes. |
| **⚡ Raw Dictation** | Form Fields, Search | 1:1 exact transcription with high-accuracy punctuation. |
| **💻 Code & Dev** | IDEs, GitHub, Terminal | Recognizes technical terms, formats `camelCase`, SQL queries, and terminal commands. |
| **🪄 Voice Prompt** | AI Assistant, Drafting | Speaks an instruction (e.g. *"Draft a polite reply declining the meeting"*), and the AI executes the deliverable. |

---

## 📖 Personal Vocabulary & Custom Dictionary
Never suffer misspelled technical jargon again:
* Add custom terms like `Kubernetes (K8s)`, `PostgreSQL`, `API Gateway`, or colleague names like `Tomesh`.
* The AI engine prioritizes your personal dictionary when phonetically matching spoken words.

---

## ⚡ Productivity Analytics
* **Words Per Minute (WPM)**: Real-time speaking speed speedometer (typically 140–160 WPM vs. 38 WPM thumb typing).
* **Time Saved Tracker**: Automatically calculates minutes and hours saved across your daily messaging and emailing.

---

## 📦 Android APK Build & GitHub Actions CI/CD

Wispr Flow is automatically built into a native Android APK via GitHub Actions:
* **Workflow**: `.github/workflows/build-apk.yml`
* **Output**: `WisprFlow.apk`
* **Releases**: Download the latest APK directly from the [GitHub Releases](https://github.com/tomeshjain/leadpulse-scanner/releases) tab.

---

## 🛠️ Technology Stack
* **Frontend**: HTML5, Vanilla CSS3 (Custom Dark Luxury Glassmorphism), Modern ES6+ JavaScript.
* **Audio & Speech**: Web Speech API (`webkitSpeechRecognition`), Web Audio API (`AudioContext`, `AnalyserNode`).
* **AI Model**: Google Gemini 2.0 Flash (`gemini-2.0-flash`) with instant offline rule-based fallback.
* **Android Native**: Java, WebView, `InputMethodService` (Custom IME Keyboard), `Service` with `SYSTEM_ALERT_WINDOW` (Floating HUD).
