# Jring 56ff Protocol — Decoding Discoveries

> **Updated:** 2026-06-22 — APK decompilation + Gadgetbridge cross-reference
>
> This document tracks discoveries made during reverse engineering. The
> authoritative protocol reference is now `ring-protocol.md`.

## Source of Truth

The official Jring app (v1.9.84) was decompiled and cross-referenced with the
open-source Gadgetbridge KeepFit implementation (PR #5326). Key findings:

- **Manufacturer**: keeprapid.com (Chinese OEM, white-labels across many brands)
- **SDK**: SXR KeepFit SDK (`com.sxr.sdk.ble.keepfit`) — SXR = ShenXinRui (深新锐)
- **SoC**: Renesas DA14531 (Dialog DA145XX)
- **Manufacturer's GitHub**: [keeprapid/krwatch](https://github.com/keeprapid/krwatch) — official SDK, server,
  and the protocol doc `深新锐蓝牙协议v3.0.docx` (under `doc 2/` and `doc.zip`)
- **Open-source ring hardware/firmware**: [atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
  (the SR08 ring is a PPG HR/SpO₂ + accelerometer device — **no skin-temperature sensor**)

## Major Corrections

### ★ Blood Pressure: `0x23`/`0x24`, NOT Standard BLE

Blood pressure does **NOT** come from standard BLE services `0x1810`/`0x1808`.
It comes through the custom 56FF protocol:

- **`0x23`** (`CMD_TOGGLE_BLOOD_PRESSURE`) triggers a combined measurement
- **`0x24`** (`CMD_RECEIVED_SENSOR_DATA`) returns ALL of: HR (byte[1]), systolic (byte[2]), diastolic (byte[3]), SpO₂ (byte[4]), stress (byte[5])

The official app **hides blood pressure from the UI**, but the ring does send it.
Gadgetbridge's open-source implementation successfully extracts and displays both
systolic and diastolic values.

### ★ SpO₂: `0x3E`/`0x3F` for Standalone, `0x23`/`0x24` for Combined

| Prior understanding | Corrected |
|---|---|
| `0x23` = SpO₂ measurement | `0x23` = **Combined BP + HR + SpO₂ + stress** measurement |
| `0x24` = SpO₂ result | `0x24` = **Combined sensor result** (all 5 metrics) |
| — | `0x3E` = **SpO₂-only** toggle |
| — | `0x3F` = **SpO₂-only** result (byte[1] = %) |
| `0x27` = HR complete | `0x27` = `CMD_NOTIFY_SENSOR_DATA` (generic) |
| `0x28` = SpO₂ complete | `0x28` = `CMD_NOTIFY_BLOOD_DATA` |

### ★ `0x02` = User Profile, NOT Activity Query

`0x02` (`CMD_SET_USER_INFO`) sets the user's age, height, weight, gender, and step length.
Activity data (`0x03`) arrives automatically on connect, no explicit query needed.

### ★ `0x20` = Device Capabilities, NOT Static Config

The `0x20` response is a **bitfield of supported features** — the app uses this to
show/hide UI elements. This is why the same JRing app can control smartwatches with
many more features than the basic ring.

## Newly Decoded Packets (from APK decompilation)

### `0x24` — Combined Sensor Data ★

```
24 HH SS DD OO FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
│  │  │  │  │  │
│  │  │  │  │  └─ Fatigue / stress level
│  │  │  │  └─── Blood oxygen SpO₂ (%)
│  │  │  └────── Diastolic pressure (mmHg)
│  │  └───────── Systolic pressure (mmHg)
│  └──────────── Heart rate (BPM)
└─ 0x24 command
```

All five values arrive in one 20-byte notification. Values > 0 indicate valid readings.

### `0x06` — Device Commands (ring → app)

```
06 CC 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
│  └─── Command code
└─ 0x06 command
```

Command codes:
- `1` = Find my phone
- `2` = Snap photo (camera remote)
- `4` = End phone call
- `5` = Request weather sync
- `8` = Answer phone call
- `16` = Music play/pause
- `32` = Music next track
- `64` = Music previous track
- `65` = Open camera
- `66` = Close camera
- `68` = Volume up
- `69` = Volume down

### `0x0C` — Device Info Response

```
0c 8a 00 41 42 f5 90 0a dc 3a 00 2a 00 c5 78 99 70 1f 5e 30
│  └───┘ └─────────────┘ └───────────┘ └─────────────────────┘
│  status  MAC address    firmware hex   remaining addr bytes
└─ command
```

- bytes[1-2]: Status code (LE u16, typically `0x008a` = 138)
- bytes[3-8]: 6-byte MAC address
- bytes[9-12]: Firmware revision as two LE 16-bit values → combined `"003A002A"`

### `0xF6` — Firmware Version Number

**Version variant:**
```
f6 00 00 00 8a 00 2a 00 00 00 00 00 00 00 00 00 00 00 00 00
│  └─────────┘ └──┘ └──┘
│  padding    ver  unknown
└─ command
```
- bytes[4-5]: Version number as LE u16 (`0x008a` = 138 → `V138`)

**MAC variant:**
```
f6 41 42 f5 90 0a dc 00 00 00 00 00 00 00 00 00 00 00 00 00
│  └─────────────────┘
│  6-byte MAC reversed
└─ command
```

Full firmware: `"003A002AV138"` = status hex `"003A002A"` + version `"V138"`

### `0x3E` / `0x3F` — SpO₂-Only Measurement

Discovered in the APK: the KeepFit SDK has separate commands for SpO₂-only measurement,
distinct from the combined `0x23`/`0x24` measurement.

- `0x3E` (`CMD_TOGGLE_SPO2`): byte[1] = 1 to start, 0 to stop
- `0x3F` (`CMD_RECEIVED_SPO2_DATA`): byte[1] = SpO₂ percentage (80-100)

### `0x3A` — Keepalive Ping

The ring disconnects after ~20s of inactivity. The SDK uses `0x3A` as a keepalive
ping/pong to maintain the connection without showing the Bluetooth status bar icon.

## New Commands Not Previously Documented

| Command | Direction | Purpose |
|---------|-----------|---------|
| `0x05` | write | Anti-lost mode (triggers alarm if ring moves out of range) |
| `0x06` | notify | Device-originated commands (find phone, music, camera) |
| `0x07` | write | Camera remote toggle |
| `0x08` | write | Sedentary / idle movement reminder |
| `0x09` | write | Sleep schedule (bedtime / wake time) |
| `0x0D` | write | Set alarm |
| `0x0E` | write | Device operational mode |
| `0x12` | write | Push notification to device (call, SMS, app alerts) |
| `0x1B` | write | Device settings: vibrate, backlight, quiet mode |
| `0x1D` | write | 12h / 24h clock format |
| `0x22` | write | Weather forecast data (smartwatch feature) |
| `0x25` | write | Sport mode report (labeled activities) |
| `0x26` | write | Heart rate alert thresholds |
| `0x31` | write | Medicine, drink, custom text reminders |
| `0x34` | read | Watch face wallpaper (smartwatch feature) |
| `0x3A` | write/notify | Keepalive ping/pong |
| `0x44` | write | Menstrual cycle tracking |

## Firmware Update Discovery

The official app checks these URLs for firmware updates:

| URL | Purpose |
|-----|---------|
| `http://download.keeprapid.com/apps/smartband/jring/autoupdater/{device_id}/update.json` | Firmware version check |
| `http://download.keeprapid.com:8181/docs/jring/an_{p1}_{p2}1` | Firmware binary download |

Flashing uses Renesas SUOTA (`0xFEF5` service) to the DA14531 SoC.

The app also reads firmware from standard BLE DIS characteristics (`0x2A26`, `0x2A28`) and
via `0x0C`/`0xF6` custom protocol commands.

## Connection Behavior

### Idle Timeout
The ring disconnects after ~20 seconds of inactivity. The official app uses `0x3A` keepalive
pings to maintain the connection. Combined with `autoConnect=true`, the ring stays connected
silently in the background.

### OS-Level Bonding
The official app triggers `createBond()` on connect, showing the Android system pairing dialog.
Without this bond, Android caches the device and requires a phone restart to re-discover it
after "Forget". Call `removeBond()` on disconnect to clear the bond.

## Blood Sugar

The official app displays blood sugar (e.g., 111.70 mg/dL). The APK decompilation found:

1. **NO standard BLE Glucose Service** (`0x1808`) or Blood Pressure Service (`0x1810`) UUIDs
2. `com.google.blood_glucose` references are for **Google Health Connect export**, not BLE
3. The app has blood sugar adjustment/calibration UI (`BloodAdjust_*` strings)
4. Most likely source: `ACTION_NOTIFY_BLOOD_DATA` broadcast (`0x28` command), or server-side
   calculation via keeprapid.com API, or manual entry

## Still Unknown

| Item | Observed | Hypothesis |
|------|----------|------------|
| `0x33F5` / `0x33F6` characteristics | Present in APK UID strings | Additional 56FF service characteristics (purpose unknown) |
| `0x57FF` service | Present in APK UID strings | Secondary communication service |
| Blood sugar source | Not standard BLE | Likely `0x28` notification or server-side |
| `0x13` bytes 5+ | Always zero in captures | Sport report detail (unused by ring, used by smartwatches) |
| `0x16` bytes 9+ | Pattern varies (`52 52...` or `01 00...`) | May encode temperature or other stored metrics |
| Ring firmware behavior differences | Rings have fewer features than watches | `0x20` capability bitfield determines what works |

## References

- **Full APK decompilation + Gadgetbridge analysis**: `keepfit-protocol-complete.md`
- **Authoritative protocol reference**: `ring-protocol.md`
- **Gadgetbridge KeepFit PR**: [#5326](https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5326)
- **Official protocol doc**: `深新锐蓝牙协议v3.0.docx` (keeprapid/krwatch GitHub)
- **Open-source ring firmware**: [ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
