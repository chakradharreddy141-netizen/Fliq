# Fliq: Spatial Touch App ✋📱

Fliq brings spatial gesture control to Android! Control your phone using hand gestures captured through your front camera—without touching the screen. Built with Google's MediaPipe for precise, real-time on-device machine learning.

## ✨ Features (v2.0)

* **Fully Customizable Gestures:** Map any of the 7 built-in hand gestures to 22 different actions!
* **Dedicated Settings UI:** A clean, dark-themed settings menu to easily configure your gesture mappings.
* **On-Device AI:** Uses MediaPipe for low-latency, private, offline hand tracking.
* **Smart Overlays:** Displays non-intrusive custom toast messages across all apps (bypassing strict OEM background restrictions like Realme/Oppo UI).
* **Pause/Resume:** Instantly pause camera processing with the "I Love You" gesture to save battery.
* **Background Operation:** Runs as a foreground service, so you can lock your screen or switch apps while maintaining gesture control.

## 🖐️ Supported Gestures
By default, gestures are mapped as follows (but all are fully customizable!):

* 👍 **Thumb Up** ➡️ Scroll Up
* 👎 **Thumb Down** ➡️ Scroll Down
* ✊ **Closed Fist** ➡️ Go Back
* 🖐️ **Open Palm** ➡️ Go Home
* ✌️ **Victory (Peace)** ➡️ Open WhatsApp
* ☝️ **Pointing Up** ➡️ Open Instagram
* 🤟 **I Love You** ➡️ Pause/Resume Fliq

## ⚙️ Available Actions (22 Total)
You can assign gestures to do almost anything:
* **Apps:** WhatsApp, Instagram, YouTube, Camera, Chrome, Spotify, Google Maps, Phone, Messages, Settings
* **Navigation:** Scroll Up, Scroll Down, Scroll Left, Scroll Right, Go Back, Go Home
* **Utilities:** Toggle Flashlight, Take Screenshot, Lock Screen, Google Assistant
* **Media:** Play/Pause, Next Track, Previous Track, Volume Up, Volume Down
* **System:** Open Notifications, Quick Settings, Recent Apps

## 🚀 How to Install & Use

1. Download the latest `Fliq.apk` from the **Releases** tab.
2. Install the APK on your Android device (Android 9.0+ recommended).
3. Open the Fliq app and tap **1. Grant Overlay Permission**.
4. Tap **2. Gesture Settings** to customize what each hand sign does.
5. Tap **3. Start Background Service** and enable Fliq in your Accessibility Settings.
6. Boom! The app is now running in the background. Use the **Stop Background Service** button when you're done to save battery.

## 🛠️ Technical Details

* **Language:** Kotlin
* **AI Engine:** Google MediaPipe Tasks Vision (`hand_landmarker.task`)
* **Core Components:**
  * `GestureForegroundService`: Manages camera lifecycle outside the main app.
  * `FliqAccessibilityService`: Injects global actions (scrolling, back, home) and system intents.
  * `HandTrackingHelper`: Processes frames using MediaPipe.
  * `GesturePreferences`: Stores user configurations via SharedPreferences.

---
*Created by Chakradhar Reddy*
