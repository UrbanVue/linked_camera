# GitHub Repository Setup Guide

This document contains all texts and configurations needed before making the Linked Camera repository public.

---

## 1. Repository Description (About Section)

### Short Description (160 characters max):
```
A lightweight, privacy-aware Android camera app for field data collection with automatic Nextcloud upload and offline queue management.
```

### Alternative (if shorter needed):
```
Privacy-focused Android camera with Nextcloud auto-upload for field data collection. Works offline with WiFi queue sync.
```

---

## 2. Repository Topics (Tags)

Add these topics to help users find your repository:

```
android
camera
nextcloud
field-data-collection
geotagging
photography
open-source
privacy
webdav
android-app
camera-app
data-collection
field-work
gps
hdri
manual-camera
offline-first
photo-stamping
surveying
open-camera
```

**How to add**: 
- Go to repository homepage
- Click ⚙️ icon next to "About"
- Add topics one by one

---

## 3. Repository Settings

### General Settings

**Default branch**: `main` ✅ (already set)

**Features to enable**:
- ✅ Issues
- ✅ Discussions (optional, for community support)
- ❌ Wiki (not needed, use README)
- ❌ Projects (not needed initially)

**Template repository**: ❌ No

**Preserve this repository**: ❌ No

**Sponsorships**: ❌ Disable (unless you want donations)

### Pull Requests

**Allow merge commits**: ✅ Yes
**Allow squash merging**: ✅ Yes
**Allow rebase merging**: ✅ Yes
**Always suggest updating pull request branches**: ✅ Yes
**Automatically delete head branches**: ✅ Yes

### Issue Templates

Create the following issue templates:

**Bug Report Template** (`/.github/ISSUE_TEMPLATE/bug_report.md`):
```markdown
---
name: Bug Report
about: Report a bug or issue with Linked Camera
title: "[BUG] "
labels: bug
assignees: ''
---

## Bug Description
A clear and concise description of what the bug is.

## Steps to Reproduce
1. Go to '...'
2. Click on '...'
3. Scroll down to '...'
4. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened.

## Screenshots
If applicable, add screenshots to help explain your problem.

## Device Information
- Device Model: [e.g., Samsung Galaxy S21]
- Android Version: [e.g., Android 13]
- App Version: [e.g., v1.0]
- Camera API: [Camera1 / Camera2]

## Nextcloud Information (if related to upload)
- Nextcloud Version: [e.g., 27.1.3]
- Self-hosted or provider: [e.g., self-hosted]
- WiFi only enabled: [Yes/No]

## Additional Context
Add any other context about the problem here.

## Logs
If possible, provide relevant logs:
```
adb logcat -s NextcloudUploadService:* ImageSaver:*
```
```

**Feature Request Template** (`/.github/ISSUE_TEMPLATE/feature_request.md`):
```markdown
---
name: Feature Request
about: Suggest a new feature for Linked Camera
title: "[FEATURE] "
labels: enhancement
assignees: ''
---

## Feature Description
A clear and concise description of the feature you'd like to see.

## Problem it Solves
Explain what problem this feature would solve or what use case it addresses.

## Proposed Solution
Describe how you envision this feature working.

## Alternatives Considered
Have you considered any alternative solutions or features?

## Additional Context
Add any other context, screenshots, or examples about the feature request.

## Use Case
Describe a specific scenario where this feature would be helpful.
```

---

## 4. Social Preview Image (Optional)

Create a social preview image (1280x640 recommended) showing:
- App logo
- "Linked Camera" text
- Key feature: "Nextcloud Auto-Upload for Field Data Collection"
- Screenshot of the app

Upload via: Settings → Social preview → Upload an image

---

## 5. Website Link

Add this to repository settings:
```
https://github.com/UrbanVue/linked_camera
```

Or if you create a dedicated landing page later, update accordingly.

---

## 6. License Badge

Already included in README.md:
```markdown
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
```

---

## 7. Code of Conduct (Optional but Recommended)

Create `CODE_OF_CONDUCT.md`:

```markdown
# Contributor Covenant Code of Conduct

## Our Pledge

We as members, contributors, and leaders pledge to make participation in our
community a harassment-free experience for everyone, regardless of age, body
size, visible or invisible disability, ethnicity, sex characteristics, gender
identity and expression, level of experience, education, socio-economic status,
nationality, personal appearance, race, religion, or sexual identity
and orientation.

## Our Standards

Examples of behavior that contributes to a positive environment:

* Using welcoming and inclusive language
* Being respectful of differing viewpoints and experiences
* Gracefully accepting constructive criticism
* Focusing on what is best for the community
* Showing empathy towards other community members

Examples of unacceptable behavior:

* The use of sexualized language or imagery
* Trolling, insulting/derogatory comments, and personal or political attacks
* Public or private harassment
* Publishing others' private information without explicit permission
* Other conduct which could reasonably be considered inappropriate

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported by creating an issue. All complaints will be reviewed and investigated
and will result in a response that is deemed necessary and appropriate to the
circumstances.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org),
version 2.0, available at https://www.contributor-covenant.org/version/2/0/code_of_conduct.html.
```

---

## 8. Security Policy

Create `SECURITY.md`:

```markdown
# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability in Linked Camera, please report it by:

1. **Creating a private security advisory** on GitHub
2. Or **emailing** us directly (create an issue for contact)

Please include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We take all security reports seriously and will respond within 48 hours.

## Security Considerations

### Data Privacy

- Linked Camera does not transmit data to any third party
- Only uploads to user-configured Nextcloud server
- No analytics, telemetry, or tracking

### Nextcloud Upload Security

- Uses HTTPS for all uploads
- Supports password-protected shares
- WebDAV authentication via share token
- All credentials stored locally in Android SharedPreferences

### Permissions

The app requests only necessary permissions:
- Camera: Required for photo/video capture
- Storage: Required to save files
- Location: Optional, only for geotagging
- Bluetooth: Optional, only for remote control
- Microphone: Optional, only for video audio

### Third-Party Dependencies

All dependencies are from trusted sources:
- AndroidX libraries (Google)
- Material Design icons (Google)

## Best Practices for Users

1. **Use password-protected Nextcloud shares** when possible
2. **Keep your Nextcloud server updated** to latest version
3. **Use WiFi-only mode** to prevent mobile data uploads
4. **Verify uploads** in Nextcloud after field work
5. **Keep the app updated** to latest version
```

---

## 9. GitHub Actions (Optional - CI/CD)

If you want automated builds, create `.github/workflows/build.yml`:

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: 
