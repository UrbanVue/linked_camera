# Development Environment

Development environment setup for **Linked Camera**.

## System Requirements

- **OS**: Windows 10/11, macOS 10.14+, Linux Ubuntu 18.04+
- **RAM**: 8 GB minimum (16 GB recommended)
- **Disk**: 10 GB free space

## Development Tools

### JDK 17+
Required for building the project.

Download: https://adoptium.net/temurin/releases/

### Android SDK
- Platform API 35 (Android 15)
- Build-Tools 35.0.0
- Platform-Tools (latest)
- Min SDK: API 21 (Android 5.0)

### Android Studio
Version: Hedgehog (2023.1.1) or later
Download: https://developer.android.com/studio

### Gradle 8.7.3
Wrapper included in project. Use:


## Project Configuration

### SDK Versions
- compileSdk: 35 (Android 15)
- targetSdk: 35
- minSdk: 21 (Android 5.0)

### Application
- Package: com.linkedcamera.app
- Version: 1.0 (code 1)

### Build Tools
- Gradle Plugin: 8.7.3
- Java Toolchain: JDK 17

## Dependencies

### AndroidX
- appcompat 1.7.1
- legacy-support-v4 1.0.0
- exifinterface 1.4.1

### Testing
- junit 4.13.2
- androidx.test.ext:junit 1.3.0
- espresso-core 3.7.0

## Building

### Clone


### Configure SDK
Create local.properties:


### Build Debug

Output: app/build/outputs/apk/debug/app-debug.apk

### Build Release


## Development

### Import Project
1. Android Studio ? Open ? Select project
2. Gradle sync
3. Build ? Make Project

### Run
1. Create AVD or connect device
2. Enable USB debugging
3. Run ? Run app

### Debug Logs


## Camera APIs

### Camera1 (Legacy)
- All Android versions
- Older device support

### Camera2
- Android 5.0+
- Enhanced controls
- Toggle in settings

## Testing



## Common Issues

**SDK not found**: Set ANDROID_HOME or create local.properties

**Gradle fail**: Check JDK 17+, run ./gradlew clean

**Slow build**: Add to gradle.properties:


## Resources

- Android: https://developer.android.com/
- Project: https://github.com/UrbanVue/linked_camera
- Camera2: https://developer.android.com/training/camera2

---
**Updated**: November 2025 | **Version**: v1.0
