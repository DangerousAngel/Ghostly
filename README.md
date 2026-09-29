#  Ghostly

> **A modern, minimalist, pure monochrome Android browser engineered for zero-trace browsing, ephemeral memory, and maximum privacy.**
![ss](Screenshot.png)
Created by **[DangerousAngel](https://github.com/DangerousAngel)** 

---

## Features

- **Pure Monochrome Aesthetic**
- **Zero-Cookie Engine** 
- **Ephemeral In-Memory Sessions**
- **Strict No-Cache Architecture**
- **In-App Isolated VPN (L2TP / PPTP / IPSec)**
- **Expert Network Diagnostics Console**
- **Responsive Settings Dialog**
- **Privacy Headers**
- **Instant Purge**
- **Zero Third-Party Libraries**

---

## Project Architecture

- **Package**: `da.ghostly.com`
- **Minimum SDK**: Android 5.0 (API 21)
- **Target SDK**: Android 14 (API 34)

```
da.ghostly.com/
├── GhostApp.java             # Application entrypoint configuring zero-cookie defaults
├── MainActivity.java         # Core browser controller, navigation, expert diagnostics, and UI state
├── GhostWebView.java         # Ephemeral WebView with privacy header injection
├── GhostWebClient.java       # Custom client handling error states and page lifecycle
├── GhostWebChromeClient.java # Handles progress bars and title updates
├── GhostTab.java             # Ephemeral tab model with error tracking encapsulation
├── GhostSettings.java        # Preference management and search engine provider
├── SettingsDialog.java       # Responsive dark-mode modal with privacy toggles & VPN controls
├── TabsDialog.java           # Ephemeral tab overview and switcher dialog
└── vpn/
    ├── GhostVpnProfile.java  # Model for in-app isolated VPN configurations
    ├── GhostVpnManager.java  # Persistent profile store, credentials memory, and state manager
    ├── GhostVpnService.java  # Android VpnService restricted exclusively to Ghostly package
    ├── AddVpnDialog.java     # Dialog to configure and add new VPN profiles
    ├── VpnConnectDialog.java # Authentication dialog with remembered/pre-filled credentials
    └── VpnListDialog.java    # Profile list manager with live connection toggles
```

---

## Building & Running

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
