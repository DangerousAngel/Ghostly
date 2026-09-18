# 👻 Ghostly

> **A modern, minimalist, pure monochrome Android browser engineered for zero-trace browsing, ephemeral memory, and maximum privacy.**
!(ss)[src/main/res/drawable/ic_ghost_launcher.png]
Created by **[DangerousAngel](https://github.com/DangerousAngel)** 

---

##  Features

- **Pure Monochrome Aesthetic**
- **Zero-Cookie Engine** 
- **Ephemeral In-Memory Sessions**
- **Strict No-Cache Architecture**
- **Privacy Headers**
- **Instant Purge**
- **Zero Third-Party Libraries**

---

##  Project Architecture

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

##  Building & Running

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

## 📄 License

Open source under the Apache 2.0 / MIT License.
