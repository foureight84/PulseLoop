# Android Build Toolchain

## Prerequisites

| Tool | Version | Location |
|---|---|---|
| JDK (Java) | 17.0.9 (Temurin) | `/tmp/jdk-17.0.9+9` |
| Android SDK | API 35 | `/home/khoa/android-sdk` |
| Gradle | 8.9 (via wrapper) | `android/gradlew` |

## Environment

```bash
export JAVA_HOME=/tmp/jdk-17.0.9+9
export ANDROID_HOME=/home/khoa/android-sdk
export PATH=$JAVA_HOME/bin:$PATH
```

## Build Commands

```bash
# Debug APK (fast, unsigned)
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (minified, needs signing key)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run unit tests
./gradlew test

# List available tasks
./gradlew tasks
```

## Installed SDK Components

| Component | Version |
|---|---|
| Platform | android-35 |
| Build Tools | 35.0.0, 34.0.0 |

## Kotlin / Compose Versions

| Dependency | Version |
|---|---|
| Kotlin | 2.x (via kotlin-android plugin) |
| kotlinx-serialization-json | 1.7.3 |
| Compose BOM | 2024.06.00 |
| Room | 2.6.1 |
| OkHttp | 4.12.0 |
| WorkManager | 2.9.1 |
| KSP | via com.google.devtools.ksp |

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts          # Dependencies & build config
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/pulseloop/   # Kotlin source (77 files)
│   │   │   └── res/                  # Resources (icons, colors, XML)
│   │   └── test/                     # Unit tests (9 files)
│   └── build/outputs/apk/           # Built APKs
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Module settings
├── gradlew / gradlew.bat         # Wrapper scripts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── local.properties              # sdk.dir (auto-generated)
```

## Common Build Issues & Fixes

### "JAVA_HOME is not set"
→ Set `export JAVA_HOME=/tmp/jdk-17.0.9+9`

### "Android SDK not found"
→ Create/edit `local.properties`: `sdk.dir=/home/khoa/android-sdk`

### Gradle wrapper not found
```bash
# Generate wrapper (if gradlew is missing)
gradle wrapper --gradle-version 8.9
```

### Room schema / KSP errors
→ Run `./gradlew clean` then rebuild

### Compose Material3 API changes
→ The BOM `2024.06.00` pins API versions. Card() takes `onClick` as first parameter. Not all icons have AutoMirrored variants yet — the deprecation warnings are cosmetic.
