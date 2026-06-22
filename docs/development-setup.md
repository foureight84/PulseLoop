# Development Setup

> Last updated: 2026-06-22

## Prerequisites

### Java (OpenJDK 21)

Temurin OpenJDK 21 is installed at `~/.local/lib/jdk-21`.

`JAVA_HOME` and `PATH` are configured in `~/.bashrc`. After opening a new terminal:

```bash
java -version
# openjdk version "21.0.11" 2026-04-21 LTS
```

To install/reinstall Java manually:

```bash
# Download
curl -sLo /tmp/temurin-21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk"

# Extract
tar xzf /tmp/temurin-21.tar.gz -C /tmp
mkdir -p ~/.local/lib
cp -r /tmp/jdk-21.* ~/.local/lib/jdk-21

# Add to ~/.bashrc
cat >> ~/.bashrc << 'EOF'

# Java (Temurin OpenJDK 21)
export JAVA_HOME="$HOME/.local/lib/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
EOF
```

### Android SDK

Android SDK is at `~/Android/Sdk` (installed via Android Studio).

Gradle wrapper at `./gradlew` handles the rest automatically on first build.

## Building

```bash
cd android

# Compile only (fast, for checking errors)
./gradlew compileDebugKotlin

# Full debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Run tests
./gradlew testDebugUnitTest
```

## Installing on Device

```bash
# Via ADB (USB connected)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or transfer & install manually
```

## Project Structure

```
android/
├── app/
│   ├── src/main/java/com/pulseloop/
│   │   ├── ring/           # BLE protocol, decoder, encoder, drivers
│   │   ├── service/        # Background sync, sleep insights, metrics
│   │   ├── data/           # Room database, entities, DAOs
│   │   ├── coach/          # AI coach orchestration
│   │   ├── ui/             # Compose screens, viewmodels, theme
│   │   ├── notifications/  # Android notification scheduling
│   │   ├── wearables/      # Wearable model catalog
│   │   ├── diagnostics/    # Raw packet logging, JSON export
│   │   └── settings/       # API key store, unit system
│   └── src/test/           # Unit tests
├── build.gradle.kts        # Root build config
├── settings.gradle.kts
└── gradlew                 # Gradle wrapper
```

## Pre-existing Test Failures

These tests fail on both iOS and Android — not related to the port:

| Test Class | Failures |
|-----------|----------|
| `CoachResponseParserTest` | 5 tests — JSON parsing edge cases |
| `SleepInsightsTest` | 2 tests — duration format + ideal night scoring |
| `ColmiDecoderTest` | 1 test — temperature big data |
