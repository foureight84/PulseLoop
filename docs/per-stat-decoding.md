# KeepFit Protocol — Per-Stat Decoding Reference

> **Source:** Gadgetbridge `KeepFitDeviceSupport.java` (mirrors official app's SXR KeepFit SDK)
> All multi-byte values are **little-endian** unless noted. All offsets are 0-based from a
> 20-byte notification after the ring normalizes the 0xFC header (if applicable for MRD SDK rings).

---

## 0x03 — Current Activity (CMD_GET_CUR_SPORT_DATA)

Sent automatically by the ring on connect. Contains current day's cumulative steps, distance, calories.

```
Offset:  0    1    2    3    4    5    6    7    8    9   10   11   12   13   14   15   16
        ───  ───────────────── ─────────────────── ────────────────── ──────────────────────
Value:  0x03  u32le timestamp   u32le steps          u32le distance(m)   u32le calories(kcal)
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x03` |
| 1-4 | u32le | Unix timestamp (seconds) |
| 5-8 | u32le | Steps |
| 9-12 | u32le | Distance in **meters** |
| 13-16 | u32le | **Calories in kcal** (not joules) |

> **Time zone correction:** The ring uses **UTC**. Gadgetbridge subtracts `timezoneOffset / 1000` to convert to local time before storing. PulseLoop currently stores UTC — this is fine if consistent.

---

## 0x0B — Battery (CMD_GET_BATTERY)

```
Offset:  0    1    2
Value:  0x0B  level  charging
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x0B` |
| 1 | u8 | Battery level (0–100) |
| 2 | u8 | Charging state: `1` = charging, `0` = not charging |

When level=100 and charging=1 → battery full (BATTERY_CHARGING_FULL)

**Status:** ✅ Implemented incl. byte[2] charging state (RingDecoder.kt:58-65).

---

## 0x0C — Device Info + Firmware (CMD_GET_DEVICE_INFO)

```
Offset:  0    1    2    3    4    5    6    7    8    9   10   11   12   13   14   15   16   17   18   19
        ───  ─────── ───────────────────────── ────────────── ────────────── ─────────────────── ───────
Value:  0x0C  status  MAC address (6 bytes)     fw part 1      fw part 2      padding              CRC
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x0C` |
| 1-2 | u16le | Status code (e.g., `0x008A` = 138) |
| 3-8 | bytes | MAC address (6 bytes) |
| 9-10 | u16le | Firmware revision part 1 (`(b[10]<<8\|b[9])`) |
| 11-12 | u16le | Firmware revision part 2 (`(b[12]<<8\|b[11])`) |
| 13-19 | — | Padding + CRC |

Combined firmware hex: `String.format("%04X%04X", part1, part2)` → e.g., `"003A002A"`

The Gadgetbridge code also logs:
- `CID` = u16le at bytes[9-10]
- `DID` = u16le at bytes[11-12]
- `CRC` = u32le at bytes[16-19]
- Version = u16le at bytes[1-2] → displayed as `"V138"`

---

## 0xF6 — Firmware Version Number

Two variants detected:

**Version variant:**
```
Offset:  0    1    2    3    4    5    6    7
Value:  0xF6  0x00 0x00 0x00 ver_lo ver_hi  ??   ??
```

| Bytes | Type | Value |
|-------|------|-------|
| 4-5 | u16le | Version number (e.g., `0x008A` = 138 → `"V138"`) |

**MAC variant:**
```
Offset:  0    1    2    3    4    5    6    7    8
Value:  0xF6  MAC[0] MAC[1] MAC[2] MAC[3] MAC[4] MAC[5]  0x00 ...
```
6-byte MAC address (reversed from 0x0C response order).

---

## 0x10 — Activity History (CMD_TRIGGER_ACTIVITY_REPORT)

**Multi-packet bulk transfer.** Each packet contains 15 × 1-minute samples. The ring sends
multiple packets covering the requested day range. End condition: timestamp = 23:45 local time.

```
Offset:  0    1    2    3    4    5    6    7  ...   19
        ───  ───────────────── ──── ──── ────       ────
Value:  0x10  u32le timestamp   s[0] s[1] s[2] ...   s[14]
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x10` |
| 1-4 | u32le | Base Unix timestamp (UTC) for first sample |
| 5 | u8 | Sample at `timestamp + 0 min` |
| 6 | u8 | Sample at `timestamp + 1 min` |
| ... | ... | ... |
| 19 | u8 | Sample at `timestamp + 14 min` |

Each sample byte encodes:
- **Steps + Sleep data**: The same `0x10` command returns both activity and sleep data.
  When `0x10` is triggered, the ring sends interleaved blocks: some packets are steps,
  some are sleep stages, some are activity counts.
- Sleep stages use the same block format (`0x11` response).
- End condition: timestamp hour=23, minute=45 → sync complete.

**Gadgetbridge `parseReportBlock`:**
```java
for (int i = 5; i < 20; i++) {
    entries.put(timestamp, data[i]);  // raw byte value
    timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
}
```

**Status:** ✅ Implemented — `RingDecoder.decodeActivityHistory` decodes 15× 1-min step buckets (RingDecoder.kt:92-103).

---

## 0x11 — Sleep Timeline (CMD_TRIGGER_ACTIVITY_REPORT_SLEEP_RESULT)

Same format as `0x10` — uses the same `parseReportBlock`. 15 × 1-minute stages per packet.

| Byte value | Stage |
|-----------|-------|
| `0x00` | Awake |
| `0x28` | Light sleep |
| `0x63` | Deep sleep |

**Current RingDecoder:** Decodes bytes[5-19] as stages but ignores byte[0] which could be `0x10` or `0x11` — both use the same block format.

---

## 0x14 — Live Heart Rate (CMD_SET_LIVE_HEART_RATE_ENABLED)

Streaming HR notifications during active measurement (~1/sec).

```
Offset:  0    1    2    3    4    5    6
Value:  0x14  u32le timestamp        HR   sleep_flag
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x14` |
| 1-4 | u32le | Unix timestamp (UTC) |
| 5 | u8 | Heart rate BPM (valid: 40–220) |
| 6 | u8 | Sleep status flag |

> `timestamp == 0` means measurement error — discard the reading.

**Status:** ✅ Implemented — decodes timestamp (bytes[1-4]) and sleep status (byte[6]); `timestamp == 0` discarded as a measurement error (RingDecoder.kt:120-134).

---

## 0x16 — Heart Rate History (CMD_TRIGGER_HEART_RATE_REPORT)

**Multi-packet protocol** with header/body packets:

**Header packet** (byte[1] = `0xF0`):
```
Offset:  0    1    2-5    6    7
Value:  0x16  0xF0  ...    tot_lo tot_hi
```
- `totTimes` = `(b[7]<<8 | b[6])` — total number of data packets expected

**Index packet** (byte[1] = `0xAA`):
```
Offset:  0    1    2       3-6    7    8
Value:  0x16  0xAA  curBlock ...    last_lo last_hi
```
- `curBlock` = b[2] — current block index
- `lastTotTimes` = `(b[8]<<8 | b[7])` — last block's sample count

**Data packet** (byte[1] = `0xA0`):
```
Offset:  0    1    2    3    4    5    6    7    8    9   10   11   12   13   14 ... 19
        ───  ───  ───────────────── ──── ──── ────── ────── ────── ────── ────── ──────
Value:  0x16  0xA0  u32le timestamp  done_lo done_hi  [6 HR samples, 1-min each]  [6 more]
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x16` |
| 1 | u8 | `0xA0` (data marker) |
| 2-5 | u32le | Base Unix timestamp |
| 6-7 | u16le | Done count + 1 (progress tracker) |
| 8-13 | 6 × u8 | 6 × 1-min HR samples → averaged to 1 value |
| 14-19 | 6 × u8 | Next 6 × 1-min HR samples → averaged to 1 value |

**Gadgetbridge `parseHrReportBlock`:**
```java
// Average first 6 samples → HR at timestamp
int round1 = Math.round((b[8]+b[9]+b[10]+b[11]+b[12]+b[13]) / 6.0f);
// Average next 6 samples → HR at timestamp + 1 min
int round2 = Math.round((b[14]+b[15]+b[16]+b[17]+b[18]+b[19]) / 6.0f);
```

**End packet** (byte[1] = `0xFF`): Sync finished.

**Status:** ✅ Implemented. `RingDecoder.decodeHeartRateHistory` handles the full multi-packet protocol — `0xF0` header, `0xAA` index, `0xA0` data blocks with 6-sample averaging, and `0xFF` end marker (RingDecoder.kt:144-197).

---

## 0x24 — Combined Sensor Data (CMD_RECEIVED_SENSOR_DATA) ★

Response to `CMD_TOGGLE_BLOOD_PRESSURE` (0x23). Contains 8 metrics in one packet
(official `onReceiveSensorData(i..i8)`).

```
Offset:  0    1    2         3          4       5        6       7            8
Value:  0x24 HR   systolic  diastolic  SpO2%   fatigue  stress  bloodSugar   HRV
```

| Bytes | Type | Metric | Range |
|-------|------|--------|-------|
| 0 | u8 | `0x24` | — |
| 1 | u8 | **Heart rate** (BPM) | 40–220 |
| 2 | u8 | **Systolic BP** (mmHg) | — |
| 3 | u8 | **Diastolic BP** (mmHg) | — |
| 4 | u8 | **Blood oxygen** SpO₂ (%) | 80–100 |
| 5 | u8 | **Fatigue** | 0–100 |
| 6 | u8 | **Stress** | 0–100 |
| 7 | u8 | **Blood sugar** (mmol/L ×10) | profile-derived estimate |
| 8 | u8 | **HRV** | — |

Values > 0 indicate valid readings. Only store if value > 0.

> **This is the blood pressure AND blood sugar source.** The official app hides BP from
> the UI but the ring sends it. Blood sugar (byte[7]) is a profile-derived estimate
> computed on the ring, not a real glucometer reading — `mg/dL = (byte7 / 10) × 18.016`.

**Status:** ✅ Implemented. `RingDecoder.decodeCombinedSensor` decodes all 8 fields
(RingDecoder.kt:210-249). Fatigue (byte[5]) and stress (byte[6]) are distinct metrics.

---

## 0x3F — SpO₂ Result (CMD_RECEIVED_SPO2_DATA)

Response to `CMD_TOGGLE_SPO2` (0x3E). Standalone SpO₂ measurement.

```
Offset:  0    1
Value:  0x3F  SpO2%
```

| Bytes | Type | Value |
|-------|------|-------|
| 0 | u8 | `0x3F` |
| 1 | u8 | SpO₂ percentage (valid: 80–100) |

Note from Gadgetbridge: "not used on the hardware/app I know. There they use the 0x24 combined measurement."

**Status:** ✅ Implemented. `RingDecoder.decodeSpo2Result` (RingDecoder.kt:254-262).

---

## Summary: RingDecoder vs. Official App

As of the current build, all command decoders below are implemented (see `RingDecoder.kt`).

| Stat | Official App Decoding | Current RingDecoder |
|------|----------------------|---------------------|
| **0x03 Activity** | Steps(u32), Distance(m)(u32), Kcal(u32) | ✅ Decoded |
| **0x0B Battery** | Level(u8) + Charging(u8) | ✅ Decoded incl. charging state at byte[2] |
| **0x0C Device Info** | MAC(6B) + FW(u16le×2) + CID/DID/CRC | ✅ Reads MAC + FW |
| **0x10 Activity History** | 15× 1-min samples, multi-packet | ✅ Decoded (15× 1-min step buckets) |
| **0x11 Sleep Timeline** | 15× 1-min stages, 0x00/0x28/0x63 | ✅ Decoded (bytes[5-19]) |
| **0x14 Live HR** | Timestamp(u32le) + HR(u8) + SleepFlag(u8) | ✅ Decoded incl. timestamp + sleep flag |
| **0x16 HR History** | Multi-packet: 0xF0 header, 0xAA index, 0xA0 data (6-sample avg), 0xFF end | ✅ Full multi-packet protocol decoded |
| **0x24 Combined** | HR + Systolic + Diastolic + SpO₂ + Fatigue + Stress + Sugar + HRV (8× u8) | ✅ All 8 fields decoded |
| **0x3F SpO₂ Result** | SpO₂% at byte[1] | ✅ Decoded |
| **0x4B Bind/Unbind** | Ring-side bind handshake (action/state/type) | ✅ Decoded + handshake driven |
| **0xF6 Firmware** | Version(u16le at bytes[4-5]) + MAC variant | ✅ Reads version |

## Sleep Detection — End-to-End Flow

### How the Official App Detects Sleep

The ring does NOT have a "sleep mode" or continuous sleep tracking. Sleep data is
**stored as per-minute activity samples in the ring's internal memory** and retrieved
in bulk when the app queries history. The official app does NOT poll for sleep in
real-time — it pulls historical sleep data once after the user wakes up.

### Trigger: 0x10 with Day Parameter

```
App:  10 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
Ring: [stream of 0x10 step packets] → [stream of 0x11 sleep packets] → end at 23:45
```

- byte[0] = `0x10` (CMD_TRIGGER_ACTIVITY_REPORT)
- byte[1] = number of days to fetch (0 = today, 1 = yesterday, etc., max 27)

The ring responds with interleaved 0x10 (steps per minute) and 0x11 (sleep stages per
minute) packets. The sync ends when a packet's timestamp hits 23:45 local time.

### Sleep Stage Encoding — Threshold-Based

The official app uses **threshold-based** decoding, NOT exact byte matching:

| Byte Value | Stage | Notes |
|-----------|-------|-------|
| `>= 0x50` (>= 80) | **Deep sleep** | 0x63 (99) is one example, but ANY value >= 80 is deep |
| `>= 0x01` (>= 1) | **Light sleep** | 0x28 (40) is one example, but ANY value >= 1 is light |
| `0x00` | **Awake** | Exact match |

The values 0x28 and 0x63 commonly seen in captures are just examples — the ring can
emit any value in range. Using exact-match decoding (as earlier code did) would miss
sleep from firmware variants that emit different values.

**Source:** Gadgetbridge `KeepFitDeviceSupport.java` lines 1358-1364:
```java
if (value >= 80)      rawKind = ActivityKind.DEEP_SLEEP;
else if (value >= 1)  rawKind = ActivityKind.LIGHT_SLEEP;
// else:              rawKind = ActivityKind.AWAKE_SLEEP (default)
```

### Each 0x11 Packet = 15 Minutes of Sleep

```
Offset:  0    1    2    3    4    5    6    7  ...   19
        ───  ───────────────── ──── ──── ────       ────
Value:  0x11  u32le timestamp   s[0] s[1] s[2] ...   s[14]
```

- Each `s[i]` is the sleep stage for minute `i`
- 15 minutes per packet × multiple packets = full night

The timestamp at bytes[1-4] is the **start** of the 15-minute window.

### Sleep Session Construction

The official app builds sleep sessions by:
1. Collecting all 0x11 packets for a day
2. Concatenating their 15-minute blocks into a continuous timeline
3. Run-length encoding consecutive same-stage minutes into stage blocks
4. Computing a **sleep quality score** (0-100):
   - `>= 20%` deep sleep → score 90
   - `>= 15%` deep → 75
   - `>= 10%` deep → 60
   - `< 10%` deep → 40

### No REM Stage

The Jring/KeepFit protocol does **not** distinguish REM sleep. Only awake, light, and
deep are decoded. The Colmi R02 protocol supports REM via its big-data V2 format.

### Common Pitfall: 0x10 with Days = 0

Sending `0x10` with byte[1] = 0 means "fetch 0 days of history" — effectively a no-op.
The ring responds but sends no data. To fetch sleep data, byte[1] must be >= 1.
This was a bug in earlier versions of the port.
