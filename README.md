# 👻 Ghostly

> **A modern, minimalist, pure monochrome Android browser engineered for zero-trace browsing, ephemeral memory, and maximum privacy.**

Created by **[DangerousAngel](https://github.com/DangerousAngel)**

---

## ✨ Features

- **Pure Monochrome Aesthetic**: High-contrast black and white UI, styled after the signature Ghostly skull emblem. Zero color distractions.
- **Zero-Cookie Engine**: All first-party and third-party cookies are blocked and discarded.
- **Ephemeral In-Memory Sessions**: Zero persistence of web storage, session history, or local databases (`localStorage`, `sessionStorage`, `WebSQL`, `IndexedDB`). Everything lives in volatile memory and vanishes on exit.
- **Strict No-Cache Architecture**: Uses `LOAD_NO_CACHE` and clears application RAM cache immediately upon tab closure or purge.
- **Privacy Headers**: Automatically injects `DNT: 1` (Do Not Track) and `Sec-GPC: 1` (Global Privacy Control) headers into outgoing network requests.
- **Instant Purge**: Dedicated sharp-corner memory and cache purge button in Settings, as well as a panic wipe button in the navigation toolbar.
- **Zero Third-Party Libraries**: Built 100% with native Android SDK components (`android.webkit.*`, `android.widget.*`, `android.view.*`). No analytics, no advertising SDKs, no trackers.
- **Private Quick Shortcuts**:
  - DuckDuckGo (`DDG`)
  - YouTube (`YT`)
  - Wikipedia (`WIKI`)
  - Privacy Guides (`GUIDE`)
- **Multi-Tab Sandboxing**: Full multi-tab management with ephemeral tabs.
- **Localized**: Full English and Arabic (`values-ar`) support with RTL layout adaptability.

---

## 🛠 Project Architecture

- **Package**: `da.ghostly.com`
- **Minimum SDK**: Android 5.0 (API 21)
- **Target SDK**: Android 14 (API 34)

```
da.ghostly.com/
├── GhostApp.java             # Application entrypoint configuring zero-cookie defaults
├── MainActivity.java         # Core browser controller, navigation, and UI state
├── GhostWebView.java         # Ephemeral WebView with privacy header injection
├── GhostWebClient.java       # Custom client handling error states and page lifecycle
├── GhostWebChromeClient.java # Handles progress bars and title updates
├── GhostTab.java             # Ephemeral tab model encapsulation
├── GhostSettings.java        # Preference management and search engine provider
├── SettingsDialog.java       # Dark mode modal with privacy toggles & author link
└── TabsDialog.java           # Ephemeral tab overview and switcher dialog
```

---

## 🚀 Building & Running

Ensure you have Android SDK 34 installed.

### Using Gradle Wrapper:
```bash
./gradlew assembleDebug
```

### Windows:
```cmd
gradlew.bat assembleDebug
```

The compiled APK will be located at:
```
build/outputs/apk/debug/Ghostly-debug.apk
```

---

## 👤 Author

- **DangerousAngel** - [GitHub Profile](https://github.com/DangerousAngel)
- **Repository**: [DangerousAngel/Ghostly](https://github.com/DangerousAngel/Ghostly)

---

## 📄 License

Open source under the Apache 2.0 / MIT License.
