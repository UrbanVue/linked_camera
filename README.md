# Linked Camera

<div align="center">

**A lightweight, privacy-aware Android camera app designed for field data collection**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green.svg)](https://developer.android.com)
[![GitHub release](https://img.shields.io/github/v/release/UrbanVue/linked_camera)](https://github.com/UrbanVue/linked_camera/releases)
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.linkedcamera.app&label=IzzyOnDroid)](https://apt.izzysoft.de/packages/com.linkedcamera.app)

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="85" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=com.linkedcamera.app)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="70" alt="Get it on IzzyOnDroid">](https://apt.izzysoft.de/packages/com.linkedcamera.app)

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

### Google Play Store

The easiest way to install Linked Camera:

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="85" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=com.linkedcamera.app)

### IzzyOnDroid

Available on the IzzyOnDroid F-Droid repository:

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="70" alt="Get it on IzzyOnDroid">](https://apt.izzysoft.de/packages/com.linkedcamera.app)

### Download from GitHub Releases

1. Go to [Releases](https://github.com/UrbanVue/linked_camera/releases/latest)
2. Download the latest APK
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

---

## Troubleshooting

### Upload Issues

**"Upload failed" notification**
- Verify the share URL is correct (check for typos)
- Ensure "Allow upload and editing" is enabled on the share
- Check if the Nextcloud server is accessible
- Try opening the share URL in a web browser to verify it works

**Photos not uploading automatically**
- Check if WiFi is connected (if WiFi-only mode is enabled)
- Verify auto-upload is enabled in settings
- Check if the share password is correct (if set)
- Try manually processing the queue

**Large queue not processing**
- Connect to a stable WiFi network
- Keep the app open (or in recent apps) during upload
- Check available server storage quota

### Camera Issues

**Camera not starting**
- Grant camera permission in Android settings
- Close other camera apps
- Restart the device

**GPS location not appearing**
- Grant location permission
- Enable device location (GPS)
- Wait for GPS lock (may take a minute outdoors)
- Ensure geotagging is enabled in settings

**Bluetooth remote not working**
- Grant Bluetooth permissions
- Enable Bluetooth on device
- Ensure remote is in pairing mode
- Try rescanning for devices

---

## Building from Source

### Prerequisites

- Android Studio (latest stable version)
- Android SDK 35 (target) and SDK 21 (minimum)
- Java Development Kit 17

### Build Steps

```bash
# Clone the repository
git clone https://github.com/UrbanVue/linked_camera.git
cd linked_camera

# Open in Android Studio
# - File → Open → Select project folder

# Or build from command line
./gradlew assembleRelease
```

### Build Variants

- **Debug**: For development and testing
- **Release**: For distribution (requires signing)

The APK will be generated in `app/build/outputs/apk/`

---

## Field Workflow Tips

### Pre-Field Preparation

1. Configure Nextcloud connection and verify it works
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

### v1.2 (2025)

- 🔧 Release build configuration (non-debuggable)
- 🔐 Proper release signing certificate
- 📦 Added Fastlane metadata for F-Droid/IzzyOnDroid
- 🛒 Published on Google Play Store
- 🛒 Published on IzzyOnDroid repository

### v1.1 (2025)

- 🛒 Initial Google Play Store release
- 🔧 Build improvements and stability fixes

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
