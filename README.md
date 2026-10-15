# 🎙️ Smart AI Keyboard & Executive Meeting Assistant

> **The Professional Executive Android AI Keyboard with Smart AI Voice Dictation, Meeting Conversation Recording with AI Action Items, Glide Animated GIFs, and Smart Contact Card Scanner.**

Smart AI Keyboard is a commercial-grade, multi-modal productivity suite for Android. It transforms natural, rambling speech into clean, professionally formatted text with **sub-second latency** (<300ms) using **Google Gemini 2.0 Flash**, multi-model fallback cascades, and continuous meeting transcription. It also features the **Smart AI Card Scanner** that extracts and saves business cards directly into your phone contacts with zero human clicks.

---

## 🚀 Key Innovations (v1.6.0)

### 1. 🎙️💬 Meeting & Conversation Recording Mode
* **Person-Controlled Recording**: Manual `[▶️ Start Conversation]` and `[⏹️ Stop & Summarize]` buttons.
* **Continuous Multi-Speaker Dialogue**: Uninterrupted audio recording that captures lengthy client meetings and discussions.
* **Structured AI Deliverables**:
  1. 📋 **Executive Summary**: High-level bulleted summary.
  2. 💡 **Key Discussion Points**: Breakdown of core topics.
  3. ⏳ **Pending Action Items**: Markdown checklist (`- [ ] [Task] (Owner, Due)`).
* **1-Tap Actions**: `📥 Insert All`, `📋 Copy Summary`, and `⏳ Copy Action Items`.

### 2. 🎞️ Glide Visual Animated GIF Engine & In-Keyboard Typing Search
* **Moving Animated GIFs**: Smooth 2-column GIF previews powered by `Glide 4.16.0`.
* **In-Keyboard Typing Search**: Bypasses Android IME soft-keyboard focus constraints with an in-panel interactive QWERTY keypad.
* **Instant 🎙️ Voice Search**: Tap the mic in the GIF bar to speak your reaction query.
* **1-Tap Media Dispatch**: Rich media direct delivery via `InputConnectionCompat.commitContent()` (WhatsApp, Telegram, Slack, Messages).

### 3. 📌 Robust Clipboard History & Quick Pinned Access
* **Protected Pinned Messages**: "Clear History" deletes *only* temporary recent clips—pinned items are 100% preserved.
* **Segmented Shelf**: Instant quick-access tabs `[⭐ All]`, `[📌 Pinned]`, and `[🕒 Recent]`.
* **Instant Refresh**: Toggling pin (`📌`) or deleting individual clips (`🗑️`) updates immediately without view stacking.

### 4. 📇 Strict 6-Field Contact Card Scanner
* **Strict Schema Compliance**: Extracts strictly `Name`, `Designation`, `Organization`, `Email`, `Phone Number`, and `City` with zero empty placeholders.
* **⚡ 100% Automated Direct Contact Saving**: Programmatically inserts extracted details directly into Android's native contacts provider without any manual dialogs.

### 5. 🧠 Multi-Model Fallback Cascade & Transparent Engine Badges
* **Intelligent AI Cascade**: Automatically waterfalls through models:
  `gemini-2.0-flash` ➔ `gemini-1.5-flash` ➔ `gemini-1.5-pro` ➔ `Offline On-Device OCR / Rule Engine`.
* **Transparent Badges**: Displays execution details in real time:
  - `🟢 Pure AI: gemini-2.0-flash (320ms)`
  - `🟡 Fallback AI: gemini-1.5-flash (480ms)`
  - `⚪ Offline: Local Engine (210ms)`

---

## 📦 Android APK Download (v1.6.0)

Download the ready-to-install Android APK from the official release:
* **Release**: [Smart AI v1.6.0 Release](https://github.com/tomeshjain/leadpulse-scanner/releases/tag/smart-ai-v1.6.0)
* **Direct APK Download**: [SmartAIKeyboard.apk](https://github.com/tomeshjain/leadpulse-scanner/releases/download/smart-ai-v1.6.0/SmartAIKeyboard.apk)

