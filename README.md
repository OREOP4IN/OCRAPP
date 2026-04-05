# OCRAPP

OCRAPP, an OCR App, natively merges Google ML Kit's text extraction with Gemini 1.5 Flash AI. Built using Kotlin and Jetpack Compose, it cleans noisy scans, solves math, and formats text on the fly. The app includes a custom CameraX implementation, tap-to-focus, and a responsive dark UI for seamless real-world data capture.

## Features

* **Live Text Scanning:** Real-time optical character recognition utilizing the device camera.
* **Image Import & Cropping:** Select images from the gallery or capture new ones, paired with a custom cropping interface to isolate specific text.
* **AI Formatting (Fix OCR):** Automatically sanitizes noisy OCR data, corrects spelling errors, and formats output into readable paragraphs.
* **AI Problem Solving:** Directly answers questions, summarizes context, or solves math equations scanned by the camera.
* **Dynamic UI:** A modern, dark-themed interface built entirely with Jetpack Compose, featuring an expandable text viewer and one-tap clipboard integration.

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material Design 3)
* **Camera Hardware:** CameraX API
* **On-Device Machine Learning:** Google ML Kit Text Recognition
* **Generative AI:** Google AI Client SDK (Gemini 1.5 Flash)
* **Image Manipulation:** CanHub Android Image Cropper

## Setup Instructions

To clone and run this application, you will need Android Studio and a free Google Gemini API Key.

### 1. Clone the Repository
Open your terminal and run:
```bash
git clone https://github.com/OREOP4IN/OCRAPP.git
```

### 2. Configure the API Key
For security reasons, the Gemini API key is not committed to version control. You must generate your own key and add it to your local build environment.

1. Acquire an API key from Google AI Studio.
2. Open the project in Android Studio.
3. Locate and open the `local.properties` file in the root directory.
4. Add the following line to the bottom of the file, replacing the placeholder with your actual API key:

```properties
GEMINI_API_KEY=YourActualApiKeyGoesHere123
```

### 3. Build and Run
1. Click the **Sync Project with Gradle Files** button in Android Studio to generate the secure `BuildConfig` file.
2. Connect a physical Android device or start an Android Emulator.
3. Click **Run** to compile and install the application.

## Usage Guide

* **Scan:** Point the camera at any document, textbook, or screen. Tap the preview window to manually focus the lens.
* **Pause & Edit:** Tap the **PAUSE** button to freeze the live feed. This unlocks the text box, allowing you to manually type or edit the raw OCR output.
* **Process:** Once paused, tap **✨ FIX OCR** to clean the text formatting, or tap **🚀 SOLVE** to have the AI generate an answer based on the scanned context.
* **Copy:** Tap the copy icon in the bottom right corner of the text box to instantly send the results to your clipboard.
