# Linked Camera

<div align="center">

**A lightweight, privacy-aware Android camera app designed for field data collection**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green.svg)](https://developer.android.com)
[![GitHub release](https://img.shields.io/github/v/release/UrbanVue/linked_camera)](https://github.com/UrbanVue/linked_camera/releases)

[Features](#features) • [Installation](#installation) • [Nextcloud Setup](#nextcloud-setup-guide) • [Building](#building-from-source) • [Contributing](#contributing)

</div>

---

## Overview

**Linked Camera** captures high-quality photos and securely links them to your workflow with optional automatic upload to Nextcloud. Perfect for field researchers, surveyors, inspectors, and anyone collecting data in the field.

Built upon the excellent [Open Camera](https://opencamera.org.uk/) by Mark Harman, Linked Camera extends functionality with seamless Nextcloud integration and field-optimized features while maintaining the same commitment to privacy and open source principles.

### Why Linked Camera?

- 🌐 **Nextcloud Integration**: Automatic upload to your own cloud storage
- 📡 **Works Offline**: Queue photos when WiFi is unavailable, auto-upload when reconnected
- 🔒 **Privacy First**: No external data transmission except to your configured server
- 📸 **Full Camera Control**: Manual ISO, shutter speed, focus, white balance
- 🗺️ **Geotagging**: Embed GPS coordinates and compass direction in photos
- 🎯 **Field Ready**: Designed for real-world data collection workflows
- 🔓 **Open Source**: GPL v3 licensed, code available for review and contribution

---

## Features

### 🌐 Nextcloud Auto-Upload

**The standout feature for field data collection workflows.**

- ✅ **Automatic Upload**: Photos upload immediately after capture (when WiFi is available)
- ✅ **WiFi-Only Mode**: Queue photos when offline, auto-upload when WiFi reconnects
- ✅ **Queue Management**: Manual control to process pending uploads
- ✅ **Auto-Delete**: Optionally delete local photos after successful upload to save storage
- ✅ **Password Protection**: Support for password-protected Nextcloud public shares
- ✅ **Background Service**: Uploads happen in the background without interrupting workflow
- ✅ **Upload Notifications**: Real-time feedback on upload status and queue size

### 📸 Professional Camera Controls

- **Manual Modes**: Full control over ISO, shutter speed, focus distance, white balance
- **Advanced Photo Modes**:
  - HDR (High Dynamic Range)
  - Panorama (wide-angle stitching)
  - Exposure Bracketing (multiple exposures for HDR processing)
  - Focus Bracketing (focus stacking for macro photography)
  - Noise Reduction (multi-shot noise reduction)
  - Fast Burst (rapid continuous shooting)
- **RAW Support**: Capture DNG (RAW) images alongside JPEG for maximum editing flexibility
- **Image Formats**: JPEG, WebP, PNG, Ultra HDR JPEG

### 🎥 Video Recording

- Multiple resolutions including 4K UHD support
- Configurable bitrate and frame rate
- Digital video stabilization
- Slow motion video support
- Video picture profiles (Log, Gamma, REC709, sRGB)
- Audio recording with multiple source options (camcorder, external mic, unprocessed)
- Maximum duration and file size limits
- Video subtitles with GPS and timestamp data

### 🗺️ Location & Geotagging

- **GPS Coordinates**: Store latitude/longitude in photo EXIF data
- **Compass Direction**: Store GPS compass direction in photos
- **Photo Stamping**: Overlay date, time, GPS coordinates, and custom text directly on photos
- **Location Requirements**: Optionally require GPS lock before allowing photo capture
- **Customizable Format**: Choose coordinate format and units

### 🎛️ Advanced Controls

- **Bluetooth LE Remote**: Support for Bluetooth remote shutter devices (Kraken)
- **Audio Triggers**: Take photos via voice command ("cheese") or loud noise detection
- **Timer & Repeat Mode**: Automated time-lapse photography
- **Volume Keys**: Use volume buttons to take photos, zoom, or other functions
- **Touch to Capture**: Tap or double-tap preview to take photo

### 🎨 User Interface Customization

- **Immersive Mode**: Hide system UI for distraction-free shooting
- **UI Placement**: Left-handed or right-handed interface layouts
- **On-Screen Information**: Customizable display of zoom, ISO, battery, time, angle, GPS, etc.
- **Grid & Guides**: Rule of thirds, golden ratio, crop guides
- **Histogram**: RGB or luminance histogram overlay
- **Zebra Stripes**: Overexposure warning stripes
- **Focus Peaking**: Highlight in-focus edges for manual focus

---

## Installation

### Download from GitHub Releases

1. Go to [Releases](https://github.com/UrbanVue/linked_camera/releases/latest)
2. Download `linked-camera-v1.0.apk`
3. On your Android device:
   - Go to **Settings** → **Security** → **Install unknown apps**
   - Enable installation for your browser or file manager
4. Open the downloaded APK file
5. Follow the installation prompts
6. Grant required permissions when first opening the app

### Required Permissions

- **Camera** (required): To take photos and videos
- **Storage/Files** (required): To save photos and videos
- **Location** (optional): Only if using geotagging features
- **Bluetooth** (optional): Only if using Bluetooth remote control
- **Microphone** (optional): Only if recording video with audio or using audio triggers

---

## Nextcloud Setup Guide

### Prerequisites

- A Nextcloud server (self-hosted or provider-hosted)
- WiFi connection for initial setup and uploads (if WiFi-only mode is enabled)

### Step 1: Create a Nextcloud Public Share

1. **Log into Nextcloud** web interface
2. **Navigate** to the folder where you want photos uploaded
   - Example: `Photos/Field Work` or `Documents/Survey Data`
3. **Create the folder** if it doesn't exist
4. Click the **Share** icon (🔗) next to the folder
5. Under **"Share link"**, click **"Create public link"**
6. **Enable permissions**:
   - ✅ **Check "Allow upload and editing"** (critical!)
   - ⚠️ This allows the app to upload files to this folder
7. **(Optional) Set a password** for added security
   - Recommended if you're concerned about the share link being discovered
8. **Copy the share URL**
   - It will look like: `https://cloud.example.com/index.php/s/AbCdEfGh123456`
   - Save this URL - you'll need it for the app

> 💡 **Tip**: Use a dedicated folder for camera uploads to keep your photos organized

### Step 2: Configure Linked Camera

1. **Open Linked Camera**
2. **Go to Settings**:
   - Tap the ⚙️ Settings icon
   - Navigate to **"Linked Camera Settings"**
   - Tap **"Server settings..."**

3. **Enable Auto-Upload**:
   - Toggle **"Nextcloud Auto-Upload"** to ON

4. **Enter Share URL**:
   - Tap **"Nextcloud Share URL"**
   - Paste your share URL from Step 1
   - Ensure it includes the full path: `https://cloud.example.com/index.php/s/TOKEN`

5. **(Optional) Enter Password**:
   - If you set a password on the share, tap **"Share Password (optional)"**
   - Enter the password exactly as configured in Nextcloud

6. **Configure Upload Behavior**:
   - 📶 **"WiFi Only"** (recommended): Only upload when connected to WiFi
     - When disabled, will use mobile data (use with caution!)
   - 🗑️ **"Auto-Delete After Upload"**: Delete local copy after successful upload
     - Useful to save device storage
     - ⚠️ Only enable if you're confident uploads are working reliably

### Step 3: Test the Setup

1. **Take a test photo**
   - Return to camera view and take a photo
2. **Check notification area**:
   - If WiFi is connected: Should see "Photo uploaded to Nextcloud"
   - If WiFi is off (with WiFi-only enabled): "Queued for WiFi upload"
3. **Verify in Nextcloud**:
   - Open your Nextcloud folder in a web browser
   - The photo should appear within a few seconds
   - Filename format: `IMG_YYYYMMDD_HHMMSS.jpg`

> ✅ **Success!** If you see the photo in Nextcloud, your setup is complete!

---

## How Nextcloud Upload Works

### Immediate Upload (WiFi Connected)

```
Photo Captured
     ↓
WiFi Check → ✅ Connected
     ↓
Upload to Nextcloud (WebDAV)
     ↓
✅ Success Notification
     ↓
(Optional) Delete Local Copy
```

### Queue Mode (No WiFi / Offline)

```
Photo Captured
     ↓
WiFi Check → ❌ Not Connected
     ↓
Add to Upload Queue
     ↓
"Queued for WiFi upload" Notification
     ↓
[Later...] WiFi Reconnects → Auto-process Queue
     ↓
Upload All Queued Photos
     ↓
✅ Success Notifications
```

### Upload Settings Reference

| Setting | Description | Recommendation |
|---------|-------------|----------------|
| **Nextcloud Auto-Upload** | Master on/off toggle | ✅ Enable for field work |
| **Nextcloud Share URL** | Your public share link | ⚠️ Must have upload permissions |
| **Share Password** | Password for protected share | 🔒 Use for added security |
| **WiFi Only** | Only upload on WiFi | ✅ Enable to save mobile data |
| **Auto-Delete After Upload** | Delete local copy after upload | ⚠️ Enable if storage is limited |
| **Process Upload Queue** | Manual queue processing | 🔧 Use if WiFi disconnects |

---

## Troubleshooting

### Photos Aren't Uploading

**Check these items:**

1. ✅ **Share URL is correct**
   - Must include `/index.php/s/TOKEN`
   - Example: `https://cloud.example.com/index.php/s/AbCdEfGh123456`
   - ❌ Not: `https://cloud.example.com/s/AbCdEfGh123456` (missing `/index.php`)

2. ✅ **Share has upload permissions**
   - In Nextcloud share settings, "Allow upload and editing" must be checked
   - Try uploading a test file via web browser to verify

3. ✅ **WiFi is connected** (if WiFi-only mode is enabled)
   - Check your WiFi connection
   - Disable WiFi-only mode temporarily to test

4. ✅ **Password is correct** (if share is password-protected)
   - Password is case-sensitive
   - No extra spaces before/after

5. ✅ **Nextcloud server is accessible**
   - Try accessing the share URL in a web browser
   - Check if server is online

**Check Notifications:**
- Look for error messages in notification area
- Common errors: "Upload failed", "Server not reachable", "Authentication failed"

### Upload Queue Not Processing

**Solutions:**

1. **Check WiFi Connection**
   - Ensure WiFi is connected and working
   - Try opening a website to verify internet access

2. **Manually Trigger Queue**
   - Settings → Server settings → **"Process Upload Queue"**
   - This forces immediate queue processing

3. **Check Queue Size**
   - Queue size shown in notification area
   - If queue is very large, processing may take time

4. **Restart the App**
   - Close Linked Camera completely
   - Reopen - queue should auto-process if WiFi is available

### "Upload Failed" Errors

**Common causes:**

| Error Scenario | Possible Cause | Solution |
|----------------|----------------|----------|
| All uploads fail | Server offline | Check Nextcloud server status |
| Intermittent failures | Network issues | Check WiFi stability, try mobile data |
| After Nextcloud update | Share permissions changed | Re-verify "Allow upload and editing" |
| Large photos fail | Server upload limits | Check Nextcloud upload size limits |
| Authentication errors | Wrong password | Re-enter share password |

### Logs and Debugging

For advanced troubleshooting:

1. **Enable Android Developer Options**
2. **Connect via ADB**: `adb logcat -s NextcloudUploadService:* ImageSaver:*`
3. **Look for error messages** in the log output
4. **Share logs** when reporting issues on GitHub

---

## Building from Source

### Requirements

- **JDK 17 or higher** (JDK 21 recommended)
- **Android SDK** with API level 35 (Android 15)
- **Gradle 8.7.3+** (wrapper included in repository)
- **Git** for cloning the repository

### Build Steps

```bash
# Clone the repository
git clone https://github.com/UrbanVue/linked_camera.git
cd linked_camera

# Build debug APK
./gradlew assembleDebug

# Output location
# app/build/outputs/apk/debug/app-debug.apk

# OR build release APK (requires signing configuration)
./gradlew assembleRelease
```

### Setting Up Android SDK

If you don't have Android SDK installed:

1. Download [Android Studio](https://developer.android.com/studio)
2. Install Android SDK via Android Studio
3. Set `ANDROID_HOME` environment variable:
   ```bash
   # Linux/Mac
   export ANDROID_HOME=$HOME/Android/Sdk

   # Windows
   set ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
   ```

### Troubleshooting Build Issues

**JDK version errors:**
```bash
# Check your Java version
java -version

# Should show Java 17 or higher
# If not, download from: https://adoptium.net/
```

**SDK not found:**
- Ensure `ANDROID_HOME` is set correctly
- Or create `local.properties` in project root:
  ```properties
  sdk.dir=/path/to/your/android/sdk
  ```

**Permission denied on gradlew:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

**Gradle daemon issues:**
```bash
./gradlew --stop
./gradlew clean assembleDebug
```

---

## Usage Tips

### For Field Data Collection

**Pre-Field Setup:**
1. Configure Nextcloud upload with WiFi-only enabled
2. Enable geotagging (Settings → Location settings)
3. Configure photo stamping with your project name
4. Take test photos and verify uploads
5. Download test photos from Nextcloud to verify GPS data

**In the Field:**
1. Take photos normally - they'll queue automatically
2. GPS data embeds automatically (if enabled)
3. Check queue count in notification area
4. Photos upload when you return to WiFi

**Post-Field:**
1. Connect to WiFi at office/home
2. Photos upload automatically from queue
3. Verify all uploads in Nextcloud
4. Organize and process as needed

### Battery Optimization

- Disable unnecessary on-screen displays (Settings → On screen GUI)
- Use WiFi-only mode (saves battery vs constant mobile data)
- Turn off geotagging if GPS data isn't needed
- Consider enabling auto-delete to reduce storage writes
- Use airplane mode in the field, enable WiFi when ready to upload

### Storage Management

- Enable auto-delete if device storage is limited
- Monitor queue size - large queues can use significant storage
- Manually clear queue after verifying uploads: Settings → Process Upload Queue
- Check Nextcloud storage quota regularly

---

## Camera Features Reference

### Photo Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| **Standard** | Normal photo capture | General photography |
| **HDR** | High dynamic range | Scenes with varied lighting |
| **Panorama** | Wide-angle stitching | Landscapes, site overviews |
| **Expo Bracketing** | Multiple exposures | HDR post-processing |
| **Focus Bracketing** | Multiple focus distances | Macro, focus stacking |
| **Noise Reduction** | Multi-shot NR | Low light conditions |
| **Fast Burst** | Rapid continuous | Action shots |

### Video Features

- **Resolutions**: Up to 4K UHD (device-dependent)
- **Bitrate Control**: 100kbps to 200Mbps
- **Frame Rate**: Standard, high FPS, slow motion
- **Picture Profiles**: Standard, Log, Gamma, REC709, sRGB
- **Audio**: Multiple source options (camcorder, external mic, unprocessed)

### Manual Controls

- **ISO**: AUTO, 25-3200+ (device-dependent)
- **Shutter Speed**: 1/32000s to 30s (device-dependent)
- **Focus**: Auto, Continuous, Macro, Infinity, Manual
- **White Balance**: Auto, Daylight, Cloudy, Fluorescent, Incandescent, Manual
- **Exposure Compensation**: -3 to +3 EV

---

## System Requirements

- **Minimum**: Android 5.0 (Lollipop, API 21)
- **Target**: Android 15 (API 35)
- **Recommended**: Android 8.0+ for best camera features
- **Storage**: ~20 MB app size
- **Camera**: Device with camera (front and/or back)
- **Optional**: GPS for geotagging, Bluetooth for remote control

---

## FAQ

**Q: Is my data sent to any third parties?**
A: No. Linked Camera only uploads to the Nextcloud server YOU configure. There is no analytics, telemetry, or data collection of any kind.

**Q: Can I use this without Nextcloud?**
A: Yes! Nextcloud upload is completely optional. You can use Linked Camera as a regular camera app.

**Q: What happens if I lose internet connection while uploading?**
A: The current upload will fail, but the photo remains in the queue. It will retry when WiFi reconnects.

**Q: Can I upload videos to Nextcloud?**
A: Currently, only JPEG images are uploaded. Video support may be added in future versions.

**Q: Does this work with other cloud services (Dropbox, Google Drive)?**
A: No, currently only Nextcloud is supported via WebDAV public shares.

**Q: Is the GPS location accurate?**
A: GPS accuracy depends on your device and environmental conditions. Clear sky view provides best accuracy (typically 3-10m).

**Q: Can I customize the upload folder structure?**
A: Photos upload to the root of the configured Nextcloud share folder. You can organize them manually in Nextcloud afterward.

**Q: What format are the uploaded photos?**
A: JPEG format. RAW/DNG files are not uploaded (only stored locally).

---

## Contributing

We welcome contributions! Whether it's bug reports, feature requests, or code contributions, please engage with the project.

### Reporting Issues

Found a bug or have a feature request?

1. Check [existing issues](https://github.com/UrbanVue/linked_camera/issues)
2. Create a new issue with:
   - **Device model** and **Android version**
   - **Steps to reproduce** the issue
   - **Expected behavior** vs **actual behavior**
   - **Logs** if available (adb logcat)
   - **Screenshots** if applicable

### Contributing Code

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Test thoroughly on multiple Android versions
5. Follow existing code style and conventions
6. Commit your changes: `git commit -m 'Add amazing feature'`
7. Push to your branch: `git push origin feature/amazing-feature`
8. Open a Pull Request with:
   - Clear description of changes
   - Reasoning for the changes
   - Any related issue numbers

### Development Guidelines

- Maintain compatibility with Android 5.0+
- Test on both Camera1 and Camera2 APIs
- Preserve privacy-first principles (no analytics/telemetry)
- Follow Material Design guidelines for UI
- Add comments for complex logic
- Update documentation for user-facing changes

---

## License

This project is licensed under the **GNU General Public License v3.0** or later.

```
Copyright © 2025 UrbanVue B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

See [LICENSE](LICENSE) for the full license text.

### Based on Open Camera

Linked Camera is built upon [Open Camera](https://opencamera.org.uk/) by Mark Harman, which is also licensed under GPL v3. We are grateful for Mark's excellent work on the core camera functionality.

---

## Credits

- **Developed by**: [UrbanVue B.V.](https://github.com/UrbanVue)
- **Based on**: [Open Camera](https://opencamera.org.uk/) by Mark Harman
- **License**: GNU GPL v3
- **Dependencies**:
  - AndroidX libraries (Apache License 2.0)
  - Google Material Design icons (Apache License 2.0)

### Contributors

Thank you to all contributors who help improve Linked Camera!

<!-- Contributors list will auto-populate via GitHub -->

---

## Support

- 📖 **Documentation**: This README and in-app help
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/UrbanVue/linked_camera/issues)
- 💡 **Feature Requests**: [GitHub Issues](https://github.com/UrbanVue/linked_camera/issues)
- 📧 **Contact**: Create an issue for support

---

## Changelog

### v1.0 (Initial Release - 2025)

**Features:**
- ✨ Full Open Camera feature set
- ✨ Nextcloud auto-upload with WiFi queue management
- ✨ Geotagging and photo stamping
- ✨ Field data collection optimizations
- ✨ Privacy-focused design
- ✨ Rebranded as Linked Camera for UrbanVue B.V.

**Technical:**
- Minimum Android 5.0 (API 21)
- Target Android 15 (API 35)
- Camera2 API support
- WebDAV-based Nextcloud integration

---

<div align="center">

**Made with ❤️ for field researchers, surveyors, and data collectors**

[⬆ Back to Top](#linked-camera)

</div>
