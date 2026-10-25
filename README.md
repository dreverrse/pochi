<div align="center">

```
   ╭──────────────────────────────╮
   │                              │
   │           ^      ^           │
   │                              │
   ╰──────────────────────────────╯
```

# Pochi ✦
### *Your AI Companion for Today, Agent, Focus & Deck*

[![Release](https://img.shields.io/badge/Release-v1.0.0-facc15.svg?style=for-the-badge&logo=android&logoColor=black)](https://github.com/dreverrse/pochi/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7f52ff.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-120fps-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-8.0_to_16+-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20%26%20Local--First-06B6D4.svg?style=for-the-badge)]()
[![Solo Developer](https://img.shields.io/badge/Sole%20Developer-dreverrse-111827.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/dreverrse)
[![License](https://img.shields.io/badge/License-MIT-E11D48.svg?style=for-the-badge)](LICENSE)

<br/>

**Pochi** is a modern, high-frame-rate Android companion and productivity operating layer. Built from the ground up with 100% Jetpack Compose, it features a fluid **120fps Dynamic Island status-bar overlay** and four primary productivity pillars: **Today**, **Agent**, **Focus**, and **Deck**.

*Smarter. Simpler. With You.*

---

</div>

## ✦ Highlights & Core Features

### 1. ⚡ 120fps Floating Dynamic Island (Status-Bar Flush)
- **Flush Under Status Bar**: Sits seamlessly below the status bar on all modern hole-punch, notch, and bezel displays (tested and optimized for Nubia Neo 3 GT 5G & Android 16).
- **Expressive Mood Engine**: Signature matte-black squircle with responsive glowing white eyes (`^ ^`) that react with real emotions (happy, wink, curious, sleepy, laughing, blushing).
- **Interactive Micro-Gestures**: Poke, pet, or tap to cycle expressions or expand into full widgets.
- **Smart At-a-Glance Widgets**:
  - **Auto-Location Weather**: GPS and IP-fallback weather updates with hourly temperature canvas charts.
  - **Islamic Prayer Timetable**: Accurate times for Subuh, Dzuhur, Ashar, Maghrib, and Isya with countdowns and clean zero-scrollbar UI.
  - **Expense Tracker (Dompet)**: Natural language logging (e.g. `beli kopi 18rb`) with auto-formatted Rupiah currency.
  - **Hydration Tracker**: 8-glass water goal with visual progress counters.

---

### 2. 🗓️ Today — Daily Overview & Smart Agenda
- **Real-Time Schedule**: Interactive timetable with status-colored tags (`10:00`, `14:00`, `16:00`, `19:00`).
- **Natural Language Task Parser**: Understands multi-clause commands in both English and Indonesian (e.g., *"Study UI Design, then 4pm team sync"* or *"cek PR, lalu jam 2 siang rapat"*).
- **Task & Subtask Management**: Hierarchical subtasks checklist with editable persistent notes.

---

### 3. 🤖 Agent — Hybrid On-Device & Cloud AI
- **0ms Instant Local Hardware Actions**:
  - 📞 **Phone Calls**: Call contacts or dial phone numbers directly via natural speech or text.
  - 💬 **SMS & WhatsApp**: Compose and launch targeted messages in one tap.
  - 🚀 **App Launcher**: Open any installed app on your device instantly.
  - 📸 **Camera, Torch & Alarms**: Control flashlight, set timers, and schedule alarms.
  - 🧮 **Quick Math**: Instant evaluation for arithmetic and percentage calculations.
- **Cloud Intelligence (Bring Your Own Key)**:
  - Plug-and-play support for **Google Gemini**, **OpenRouter**, and **OpenCode**.
- **Pure Silent Listening (No Robotic TTS)**:
  - Speech recognition with real-time feedback that listens without intrusive robot voices speaking back.
- **6 Prompt Accelerators**:
  - `💡 Explain this` • `✏️ Write` • `🔍 Research` • `🛠️ Code` • `📄 Summarize` • `••• More`

---

### 4. ☕ Focus — Pomodoro & Flow State
- **Golden-Yellow Ring Progress Timer**: Ultra-smooth canvas arc timer with coffee rest indicator.
- **3 Phase Presets**:
  - `✦ Focus (25m)` • `Short Break (5m)` • `Long Break (15m)`
- **Productivity Analytics**: Weekly bar charts and completed session history.

---

### 5. 🗂️ Deck — Collect & Organize
- **Curated Personal Collections**:
  - 🖼️ UI Inspiration • `</>` Programming • 🎨 Design System • 📄 AI Research
- **Category Filters**: All, Notes, Bookmarks, Files, Apps.
- **Built-in App Launcher**: Search and trigger all installed Android applications directly inside Deck.

---

### 6. 🎨 Expressive Typography & 4 App Icon Styles
- **Non-Monotonous Typography System**:
  - **Jakarta Geometric**: Clean modern sans-serif with tight geometric letter spacing.
  - **Tech Grotesk**: Condensed, high-contrast monospace & technical display.
  - **Editorial Serif**: High-contrast, sophisticated editorial typeface.
  - **Tabular Monospace Numerals**: Clean alignment for timers and schedule timestamps.
- **4 App Icon & Mascot Variants**:
  - ⬛ **Main / Dark Mode**: Matte-black squircle with glossy rim and glowing curved white `^ ^` eyes.
  - ⬜ **Light Mode**: Crisp white squircle with midnight black `^ ^` eyes.
  - 🟨 **Accent Icon**: Golden-yellow squircle (`#FACC15`) with deep contrast eyes.
  - 🟦 **Adaptive Icon**: Layered sky-blue back card with black squircle and glowing blue eyes.

---

## 🔒 Security & Privacy Audit (v1.0.0)

Pochi is built with a **strict local-first, privacy-respecting philosophy**:

| Security Domain | Implementation Standard | Status |
| :--- | :--- | :---: |
| **API Keys & Credentials** | Zero hardcoded tokens; all user API keys are stored encrypted in private app sandbox | ✅ Verified 100% |
| **Git Repository Audit** | Zero leaked secrets or credentials across entire commit history | ✅ Clean |
| **Data Storage** | Tasks, notes, collections, and expenses stay strictly on-device | ✅ Local-First |
| **Component Exposure** | Only `MainActivity` is exported (`LAUNCHER`). All background services are strictly unexported (`android:exported="false"`) | ✅ Hardened |
| **Microphone Privacy** | Audio is processed transiently via `SpeechRecognizer` for intent parsing; audio is never persisted to disk | ✅ Ephemeral |
| **Network Requests** | 100% HTTPS TLS encryption for weather, prayer, and AI endpoints | ✅ Secure |

---

## 🛠️ Tech Stack & Architecture

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material 3 + Compose Canvas 120fps)
- **Asynchronous Architecture**: Kotlin Coroutines & Reactive `StateFlow`
- **System Services**:
  - `DynamicIslandService`: Foreground Service (`specialUse: floating_assistant_overlay`)
  - `PochiAccessibilityService`: Context-aware UI signals
  - `PochiNotificationService`: Real-time notification mirroring
  - `BiometricGateActivity`: Hardware biometric authentication gate
- **AI Engine**: Google Generative AI Android SDK (`gemini-2.5-flash`), OpenRouter, OpenCode

---

## 📱 Supported Devices & Requirements

- **Operating System**: Android 8.0 (Oreo / API 26) through **Android 16+**
- **Hardware**: Compatible with 60Hz, 90Hz, 120Hz, and 144Hz high-refresh displays
- **Display Layouts**: Dynamic Island supports centered hole-punch, pill cutouts, and corner notches

---

## 📥 Installation & Download

1. Download the latest release APK from [GitHub Releases](https://github.com/dreverrse/pochi/releases):
   - **File**: `Pochi-1.0.0.apk`
2. Install the APK on your Android device (enable *Install unknown apps* if prompted).
3. Open **Pochi**, complete the 3-step interactive onboarding, and grant permissions:
   - **Display over other apps (Overlay)**: Enables the floating Dynamic Island.
   - **Microphone**: Enables silent voice command listening.
   - **Call & SMS**: Allows Pochi to call contacts and draft messages on request.
   - **Location**: Automatic local weather & prayer schedule detection.

---

## 👤 Author & Sole Collaborator

**Pochi** is designed, developed, and maintained solely by:

- **dreverrse** (Andre Kusuma / Kyle)
  - GitHub: [@dreverrse](https://github.com/dreverrse)
  - Repository: [dreverrse/pochi](https://github.com/dreverrse/pochi)

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for full details.

<div align="center">
  <br/>
  <b>Pochi v1.0.0 — Crafted with passion for a smarter, simpler day.</b>
</div>

