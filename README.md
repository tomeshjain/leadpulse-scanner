# 🎙️ LeadPulse AI Keyboard & Smart Scanner

> **The Executive Android AI Keyboard with PulseFlow Voice Dictation, Strict 6-Field Lead Scanner, and Zero-Touch Automated Contact Saving.**

LeadPulse AI is a commercial-grade, multi-modal productivity suite for Android. It transforms natural, rambling speech into clean, professionally formatted text with **sub-second latency** (<300ms) using **Google Gemini 2.0 Flash**, multi-model fallback cascades, and Gemini Multimodal Live WebSocket streaming (`wss://`). It also features the **LeadPulse Smart Scanner** that extracts and saves business cards directly into your phone contacts with zero human clicks.

---

## 🚀 Key Innovations (v1.5.0)

### 1. 📇 Strict 6-Field Contact Card Scanner
* **Strict Schema Compliance**: Extracts strictly `Name`, `Designation`, `Organization`, `Email`, `Phone Number`, and `City`.
* **Zero Placeholders**: Any missing field is completely omitted—absolutely no `"N/A"`, `"None"`, `"null"`, or empty lines.
* **⚡ 100% Automated Direct Contact Saving**: Programmatically inserts extracted details directly into Android's native contacts provider (`ContactsContract.RawContacts` and `ContactsContract.Data`) via batch operations. No manual confirmation dialog or contact editor screen needed.

### 2. 🧠 Multi-Model Fallback Cascade & Transparent Engine Badges
* **Intelligent AI Cascade**: Automatically waterfalls through models:
  `gemini-2.0-flash` ➔ `gemini-1.5-flash` ➔ `gemini-1.5-pro` ➔ `Offline On-Device OCR / Rule Engine`.
* **Transparent Badges**: Displays execution details in real time:
  - `🟢 Pure AI: gemini-2.0-flash (320ms)`
  - `🟡 Fallback AI: gemini-1.5-flash (480ms)`
  - `⚪ Offline: Local Engine (210ms)`

### 3. ⚡ Gemini Multimodal Live Streaming (WebSocket `wss://`)
* High-performance bi-directional streaming client connecting to:
  `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`
* Full duplex audio/text transmission with instant handshake verification.

### 4. 🧪 Dedicated Pure AI & Live Diagnostics Suite
* Accessible in **Settings > Step 4**:
  - **🎙️ Test Pure AI Voice**: Strictly tests voice dictation without fallback.
  - **📇 Test Pure AI Contact Card**: Strictly tests card extraction and verifies 6-field formatting.
  - **⚡ Test Gemini Multimodal Live**: Handshakes with the Live WebSocket server and logs real-time socket events in a dark monospace console.

### 5. ⌨️ Professional Extended System-Wide Keyboard
* **Dynamic Tone Modes**: Smart Polish, Casual Chat, Executive Email, Structured Notes, Raw Dictation, Code & Dev, AI Voice Prompt.
* **Real Animated GIF Insertion**: Rich content image delivery via `InputConnectionCompat.commitContent()` (WhatsApp, Telegram, Slack, Google Messages).
* **Contextual Text-to-GIF Suggestions**: Glow chips suggest relevant reaction GIFs based on conversational keywords.
* **Clipboard History & Quick Snippets**: Persistent clipboard manager with pin/star favorites.
* **Clean Light & System Auto Themes**: Executive Royal Indigo (`#4f46e5`) accent palette matching Android 12+ Material You.

---

## 📦 Android APK Download

Download the ready-to-install Android APK from the official release:
* **Release**: [LeadPulse v1.5.0 Release](https://github.com/tomeshjain/leadpulse-scanner/releases/tag/leadpulse-v1.5.0)
* **Direct APK Download**: [LeadPulseAIKeyboard.apk](https://github.com/tomeshjain/leadpulse-scanner/releases/download/leadpulse-v1.5.0/LeadPulseAIKeyboard.apk)
