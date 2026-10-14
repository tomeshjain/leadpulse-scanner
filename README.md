# LeadPulse - Android AI Sales Card Scanner & CRM

[![Build Android APK](https://github.com/tomeshjain/leadpulse-scanner/actions/workflows/build-apk.yml/badge.svg)](https://github.com/tomeshjain/leadpulse-scanner/actions/workflows/build-apk.yml)

An AI-powered visiting card scanner, lead enrichment, and event CRM built specifically for sales teams attending conferences, expos, and client summits.

---

## 📱 How to Install on Android

### Method 1: Download Native Android APK (.apk)
1. Go to the **[Releases](https://github.com/tomeshjain/leadpulse-scanner/releases)** section of this repository.
2. Download **`LeadPulse.apk`**.
3. Open the `.apk` on your Android phone and tap **Install**.
4. The app runs 100% standalone and offline — no laptop or web server required!

### Method 2: Install as WebAPK / PWA
1. Open your GitHub Pages link in **Google Chrome** on your Android phone:
   `https://tomeshjain.github.io/leadpulse-scanner/`
2. Tap the three dots (**⋮**) in Chrome and select **"Install app"** or **"Add to Home screen"**.
3. The app installs as a native WebAPK with full camera access.

---

## 🚀 Key Features

### 1. 📷 Visiting Card Camera Scanner & OCR
- **High-Resolution Viewfinder**: Framed reticle with animated laser scan.
- **Hardware Camera Trigger**: Native camera photo capture on Android or upload from phone gallery.
- **Instant Test Cards**: 1-click realistic demo business cards for instant testing without physical cards.

### 2. 🧠 Google Gemini Multimodal Vision & Dynamic Model Selection
- **Self-Healing Model Fallback**: Automatically tries multiple Gemini models (`gemini-2.0-flash`, `gemini-1.5-flash`, `gemini-1.5-pro`, `gemini-2.5-flash`) to ensure zero "model not found" failures.
- **Dynamic Model Discovery**: Tap **"Refresh Models"** in Settings to query and select from all active models authorized for your Google AI Studio API key.
- **Customizable Intelligence**: Guide Gemini to extract custom sales metrics (e.g., Company size, IT budget, competitor products).

### 3. 🔍 AI Background Enrichment
- **LinkedIn Discovery**: Auto-extracts or infers LinkedIn profile URLs.
- **Career & Past Work History**: Synthesizes 2-4 sentence executive summaries of the prospect's previous roles and scale.
- **Company Intelligence**: Concise overview of their enterprise solutions and business model.
- **Sales Icebreakers**: Generates tailored conversational talking points for sales reps.

### 4. 🎯 Event-Wise Organization & Action Items
- **Event Switcher**: Create conferences and tag newly scanned leads automatically to the active event.
- **Follow-up Action Items**: Assign tasks per contact with priority tags (`HIGH`, `MED`, `LOW`) and track them in a centralized dashboard.
- **1-Tap Android Contacts Sync**: Export leads directly into phone Google Contacts via standard `.vcf` (vCard 3.0) and export CSV spreadsheets for CRM pipelines.

---

## 🛠️ Tech Stack
- **Frontend**: Vanilla HTML5, CSS3 (Material 3 Dark Design), Modern JavaScript ES6+
- **AI Core**: Google Gemini Multimodal Flash & Pro Vision APIs
- **Storage**: HTML5 LocalStorage + Service Worker Cache v2
- **Android Native**: Standalone WebView Activity with Camera & FileProvider support + GitHub Actions APK builder.
