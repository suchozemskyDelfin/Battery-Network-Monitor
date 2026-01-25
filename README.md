# Battery Network Monitor 🔋

A lightweight, cross-platform solution for remote monitoring of Android battery status across a local network. This project consists of a Kotlin-based Android foreground service and a Python PC client.

## 🚀 Features
- **Real-time Monitoring**: Get battery percentage and charging status instantly.
- **Deep Wake Technology**: Uses UDP Unicast and Android WakeLocks to wake up devices even from deep sleep (tested on Xiaomi/Android 10).
- **Auto-Discovery**: PC client scans the local subnet to find all active monitors.
- **Minimalistic UI**: Clean Windows-native look using Python's Tkinter.

---

## 📱 Android App (Kotlin)
The Android component runs as a **Foreground Service** to ensure the system doesn't kill the process.

### Key Components:
- **UDP Server**: Listens on port `8888`.
- **Wake-on-Display**: Forcefully wakes the screen and CPU upon receiving a specific UDP packet to bypass aggressive battery-saving filters.
- **MulticastLock**: Ensures the Wi-Fi chip stays active for network discovery.

### Requirements:
- Android 8.0+ (Tested on Xiaomi Mi A2 Lite, Android 10).
- Disable "Battery Optimization" for the app in Android settings for 24/7 reliability.

---

## 💻 PC Client (Python)
The desktop client is built with Python and provides a dashboard for all monitored devices.

### How it works:
Instead of relying on unreliable broadcasts, the client performs a **Deep Unicast Scan**. It "knocks" on every IP address in the local subnet, ensuring that even sleeping devices receive the request.

### Requirements:
- Python 3.x
- `tkinter` (usually included with Python)

### Usage:
1. Run the Android app and start the service.
2. Run the Python script: `python battery_monitor.py`
3. Click **"🔍 Deep Network Scan"**.

---

## 🛠 Project Structure
```text
BatteryNetworkMonitor/
├── android_app/      # Full Android Studio project (Kotlin)
├── pc_monitor/       # Python desktop application
└── README.md         # Documentation