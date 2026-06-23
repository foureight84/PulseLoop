# Ring Protocol Reference

> **Authority:** Decompiled official Jring APK v1.9.84 + Gadgetbridge PR #5326 +
> official SXR/KeepFit protocol docs (`深新锐蓝牙协议v3.0.docx`)
>
> The Jring app is made by **keeprapid.com** (a Chinese OEM that white-labels the same
> hardware + SDK across many brands: Jring, KeepFit, JYouPro, RWfit, Tag, etc.).
> The protocol library is **SXR KeepFit SDK** (SXR = ShenXinRui / 深新锐).
>
> Additional sources: [sakshambhutani.xyz](https://sakshambhutani.xyz/hacking/2_hacking/),
> [Smart-Ring-Protocol](https://github.com/saksham2001/Smart-Ring-Protocol/),
> [colmi_r02_client](https://tahnok.github.io/colmi_r02_client/)

## Transport

| Property | Jring (56ff) | Colmi R02 |
|---|---|---|
| Advertised name | `SMART_RING` / `SR08` | `SMART_RING` / `R02` / `R11` |
| SoC | Renesas DA14531 | Varies |
| Service UUID | `000056ff-0000-1000-8000-00805f9b34fb` | Nordic-UART (`6E40xxx`) or V2 |
| Write char | `000033f3-0000-1000-8000-00805f9b34fb` | Varies |
| Notify char | `000033f4-0000-1000-8000-00805f9b34fb` | Varies |
| SUOTA service | `0000fef5-0000-1000-8000-00805f9b34fb` | — |
| Frame size | Fixed 20 bytes | 16 bytes (padded to 15 + checksum) |
| Encryption | None | None |

Additional characteristics observed in the APK: `0x33F5`, `0x33F6` (purpose unknown).
A secondary service `0x57FF` also exists but is not part of the main health protocol.

Standard BLE services also exposed: Device Information Service (`0x180A`), Heart Rate Service (`0x180D`),
ANCS (`0xFFE5`/`0xFFE9`).

## Packet Format

Every command and response is exactly **20 bytes**. Byte 0 is the command ID (opcode),
bytes 1-19 are payload (zero-padded). Multi-byte integers are little-endian.

Error responses have the high bit set (opcode `| 0x80`).

## Complete Command Table

> **Corrections from prior docs marked ★**

### Time & Identity

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x01` | write | `CMD_SET_TIME` | Time sync: u32le Unix epoch (bytes 1-4) + i8 timezone offset (byte 5) |
| **★ `0x02`** | write | `CMD_SET_USER_INFO` | **Set user profile** (age, height, weight, gender, step length). NOT activity query. |
| `0x21` | write | `CMD_SET_LANG` | Set locale string (e.g., `"en-US"`) |
| `0x1D` | write | `CMD_SET_HOUR_FORMAT` | Clock format: 0 = 12h, 1 = 24h |
| `0x48` | write | `CMD_SET_APP_ID` | Application identifier string |
| `0x4B` | write/notify | `setBindedInfo` | Ring-side bind/unbind. Bytes: `[0x4B, action, state, type]`. action: 0=INIT, 1=APP_START, 2=ACK, 3=ACK_CANCEL, 4=SUCCESS, 5=UNBOND, 6=UNBOND_ACK; state: 0=NO, 1=YES; type=1. Same opcode is sent as a notification by the ring (see Binding under Connection Behavior). |
| `0x0E` | write | `CMD_SET_DEVICE_MODE` | Device operational mode |

### Device Info

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x0B` | write/notify | `CMD_GET_BATTERY` | Battery: byte[1] = level (0-100), byte[2] = charging (1=yes). Error: `0x8B` |
| `0x0C` | write/notify | `CMD_GET_DEVICE_INFO` | Device info: MAC address (bytes 3-8) + firmware hex (bytes 9-12). Error: `0x8C` |
| `0xF6` | notify | (firmware variant) | Version number: bytes[4-5] as LE u16 = version (e.g., `0x008A` = 138 → V138). Also sends MAC variant. |
| **★ `0x20`** | notify | `CMD_DEVICE_SUPPORTED_FUNCTIONS` | **Device capability bitfield** — which features the device supports. NOT static config. |

### Health — Combined Sensor Measurement ★ (Blood Pressure Source)

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| **★ `0x23`** | write | `CMD_TOGGLE_BLOOD_PRESSURE` | **Trigger combined measurement** (NOT SpO₂!). byte[1] = 1 to start, 0 to stop. Measures HR + BP + SpO₂ + fatigue + stress + blood sugar + HRV in one go (~30–45 s). Done: `0xA3` |
| **★ `0x24`** | notify | `CMD_RECEIVED_SENSOR_DATA` | **Combined measurement result** (NOT SpO₂ result). byte[1]=HR, byte[2]=systolic, byte[3]=diastolic, byte[4]=SpO₂%, byte[5]=fatigue, byte[6]=stress, byte[7]=blood sugar (mmol/L ×10), byte[8]=HRV. See full byte map below. |
| `0x27` | notify | `CMD_NOTIFY_SENSOR_DATA` | Sensor measurement complete |
| **★ `0x28`** | notify | `CMD_NOTIFY_BLOOD_DATA` | **Blood data notification** (NOT SpO₂ complete) |
| **★ `0x3E`** | write | `CMD_TOGGLE_SPO2` | **SpO₂-only measurement**: byte[1] = 1 (start) / 0 (stop) |
| **★ `0x3F`** | notify | `CMD_RECEIVED_SPO2_DATA` | **SpO₂ result**: byte[1] = percentage (valid 80-100) |

### Health — Heart Rate

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x14` | write/notify | `CMD_SET_LIVE_HEART_RATE_ENABLED` | Start live HR stream. bytes[1-4]=interval/config, byte[5]=activity slot (1-5). Notify: byte[5]=BPM, byte[6]=sleep flag |
| `0x15` | write | `CMD_SET_LIVE_HEART_RATE_DISABLED` | Stop HR measurement. byte[5]=activity slot |
| `0x19` | write | `CMD_SET_AUTO_HEART_MODE` | Configure auto-HR measurement interval + cadence |
| `0x16` | write | `CMD_TRIGGER_HEART_RATE_REPORT` | Request stored HR history by day range. Error: `0x96` |
| `0x26` | write | `CMD_SET_HEART_RATE_AREA` | Set HR alert thresholds (min, max BPM) |

### Activity & Sleep

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x03` | notify | `CMD_GET_CUR_SPORT_DATA` | Current activity (sent on connect/query): bytes[1-4]=timestamp(u32le), bytes[5-8]=steps, bytes[9-12]=distance(m), bytes[13-16]=calories |
| `0x10` | write | `CMD_TRIGGER_ACTIVITY_REPORT` | Request stored activity + sleep history. Error: `0x90` |
| `0x11` | notify | `CMD_TRIGGER_ACTIVITY_REPORT_SLEEP_RESULT` | Sleep timeline: 15 × 1-min stage samples per packet. 0x00=awake, 0x28=light, 0x63=deep |
| `0x13` | notify | `CMD_SPORT_REPORT_RESULT_CURRENT` | Activity summary (companion to `0x03`). bytes[1-4]=timestamp, bytes[5-8]=steps, bytes[9-12]=duration(s), bytes[13-16]=step time. Error: `0x83` |
| `0x1A` | write | `CMD_SET_STEP_GOAL` | Set daily step goal (u32le). Error: `0x9A` |
| `0x25` | write | `CMD_TRIGGER_SPORT_REPORT` | Request sport mode report (labeled activities: running, cycling, etc.) |

### Alerts, Reminders & Notifications

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x04` | write | `CMD_SEND_VIBRATION_SIGNAL` | Find ring / blink / vibrate |
| `0x05` | write | `CMD_TRIGGER_LOST` | Anti-lost mode. Done: `0x85` |
| `0x08` | write | `CMD_SET_IDLE_TIME` | Sedentary / idle movement reminder |
| `0x09` | write | `CMD_SET_SLEEP_TIME` | Sleep schedule (bedtime/wake) |
| `0x0D` | write | `CMD_SET_ALARM` | Set alarm |
| `0x12` | write | `CMD_ALERT_NOTIFICATION` | Push notification to device (call, SMS, app notifications — see subtype table) |
| `0x31` | write | `CMD_SET_REMINDER` | Medicine, drink, custom text alerts |

### Device Control

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x06` | notify | `CMD_GET_DEVICE_COMMAND` | **Commands FROM the device**: byte[1]=1(find phone), 2(snap photo), 4(end call), 5(request weather), 8(answer call), 16(play/pause), 32(next track), 64(prev track), 65(open camera), 66(close camera), 68(vol+), 69(vol-) |
| `0x07` | write | `CMD_SET_CAMERA_MODE` | Remote camera: byte[1] = 1 (open) / 0 (close) |
| `0x1B` | write | `CMD_SET_DEVICE_INFO` | Device settings: vibrate, backlight, quiet mode |
| `0x3A` | write/notify | `CMD_KEEPALIVE_PING` | Keepalive ping/pong (send after idle to prevent disconnect) |

### Extras (present in SDK, may not work on rings)

| Cmd | Direction | KeepFit Constant | Purpose |
|-----|-----------|------------------|---------|
| `0x22` | write | `CMD_SET_WEATHER_DATA` | Weather forecast data |
| `0x34` | read | `CMD_GET_DEVICE_WALLPAPER` | Watch face wallpaper |
| `0x44` | write | `CMD_SET_MENSTRUAL_CYCLE` | Menstrual cycle tracking |

### Error Response Opcodes

| Cmd | Meaning |
|-----|---------|
| `0x82` | User info set error |
| `0x83` | Sport report error |
| `0x85` | Anti-lost completed |
| `0x8B` | Battery read error |
| `0x8C` | Device info read error |
| `0x90` | Activity report error |
| `0x96` | HR report error |
| `0x9A` | Step goal set error |
| `0xA3` | Blood pressure + combined measurement completed |

## Combined Sensor Packet (`0x24`) — BP, blood sugar, stress, fatigue

Blood pressure **and blood sugar** come through the **custom 56FF protocol**, NOT standard BLE
services (`0x1810`/`0x1808` do not exist on the ring). One `0x24` notification carries every
spot metric. This maps directly to the official SDK callback
`onReceiveSensorData(i, i2, i3, i4, i5, i6, i7, i8)` (and its offline twin `onGetAdvSensorOfflineData`),
verified in the decompiled Jring app (`DupMainActivity.onReceiveSensorData`).

**Trigger:** Send `0x23` with byte[1] = 1; stop with byte[1] = 0. Result arrives ~30–45 s later.

| Byte | SDK arg | Value | Notes |
|------|---------|-------|-------|
| 0 | — | `0x24` (CMD_RECEIVED_SENSOR_DATA) | |
| 1 | `i`  | Heart rate (BPM) | |
| 2 | `i2` | **Systolic** (mmHg) | |
| 3 | `i3` | **Diastolic** (mmHg) | |
| 4 | `i4` | SpO₂ (%) | |
| 5 | `i5` | **Fatigue** (0–100) | `TYPE_FATIGUE = 13` |
| 6 | `i6` | **Stress** (0–100) | `TYPE_STRESS = 17`; levels ≤30 Good / <60 Normal / <80 Mid / ≥80 High |
| 7 | `i7` | **Blood sugar** = value ÷ 10 mmol/L | `TYPE_BLOOD_SUGAR = 18`; mg/dL = `(byte7 / 10) × 18.016`. e.g. `51 → 5.1 mmol/L → 91.88 mg/dL` |
| 8 | `i8` | HRV (ms) | |

Values only make sense when > 0. The official app gates each metric behind the `0x20`
capability bitfield (`FUNCTION_BLOOD`/`FUNCTION_HAS_BLOODSUGAR`/`FUNCTION_TEMPERATURE`...);
basic rings advertise BP/SpO₂/sugar/stress/fatigue but **not** temperature (no sensor — the
official UI shows a 0 °C / 32 °F placeholder).

## Notification Subtypes (0x12)

When sending `CMD_ALERT_NOTIFICATION` (0x12), the notification app is encoded as:

| ID | App | ID | App |
|----|-----|----|-----|
| 0 | Phone call | 13 | Instagram |
| 1 | SMS | 14 | LinkedIn |
| 2 | WeChat | 15 | Snapchat |
| 3 | QQ | 20 | Telegram |
| 4 | Facebook | 25 | YouTube |
| 5 | Skype | 27 | TikTok |
| 6 | Twitter | 11 | DingTalk |
| 7 | WhatsApp | 8 | Line |
| 9 | KakaoTalk | | |

## Blood Sugar (Glucose) — RESOLVED

The official app displays blood sugar (e.g. 91.88 mg/dL) and it comes from the **`0x24`
combined sensor packet, byte[7]** — NOT a standard BLE Glucose Service (`0x1808` does not
exist on the ring) and NOT the cloud. Confirmed in `DupMainActivity.onReceiveSensorData`:
the SDK passes the raw byte as `i7`, the app computes `sugar = i7 / 10` (mmol/L), then the UI
shows mg/dL via `× 18.016`. So **mg/dL = (byte7 / 10) × 18.016** — e.g. `51 → 5.1 → 91.88`.

The `com.google.blood_glucose` references in the APK are only for Google Health Connect export.
Blood sugar here is **not** a true glucometer reading — the ring computes it from the user
**profile** (sex/age/height/weight sent via `setUserInfo` 0x02; changing the profile changes the
value), gated by the `0x20` capability bit `FUNCTION_HAS_BLOODSUGAR`. The official app offers an
app-side **"Sugar Offset"** calibration (no BLE command — the offset is applied on the phone),
which this app mirrors via a settings field.

## SpO₂ Data Source

SpO₂ can be obtained two ways:

1. **Combined measurement** (`0x23` → `0x24`): Returns SpO₂ at byte[4] alongside HR + BP + stress
2. **SpO₂-only** (`0x3E` → `0x3F`): Standalone spot check. Result at byte[1] (80-100%)

The `0x24` combined measurement result is the one observed in live captures.

## Data Frequency — Critical Understanding

**The ring only sends data in response to commands. There is no continuous push.**

| Data Type | How It Arrives | When |
|---|---|---|
| **Device info** (firmware, MAC) | `0x0C` + `0xF6` notifications | On connect or explicit query |
| **Capabilities** | `0x20` notification | On connect (identifies supported features) |
| **Activity** (steps, cal, dist) | `0x03` notification | On connect, or after `0x02` user info set |
| **Live HR** | `0x14` notifications | ~1/sec during active measurement (~12s warm-up) |
| **Combined BP+HR+SpO₂+stress** | `0x24` notification | Spot check: `0x23` start → ~30s → result |
| **SpO₂-only** | `0x3F` notification | Spot check: `0x3E` start → 25-40s → result |
| **Sleep** | `0x11` bulk notifications | Only when app sends `0x10` query |
| **HR History** | `0x16` bulk stream | Only when app sends `0x16` query |
| **Battery** | `0x0B` notification | Only when app sends `0x0B` query |
| **Device commands** | `0x06` notification | When user interacts with ring (find phone, music, camera) |

**What the app's "2-second refresh" means:** The app polls the local Room database every 2-5 seconds.
This does NOT mean the ring sends data every 2 seconds. The ring only sends data when the app
explicitly queries it (startup sync, pull-to-refresh, sync button).

## Device Capabilities (0x20)

The `0x20` response tells the app which features the ring supports. This is how the app
shows/hides UI elements for different device models. The ring only supports a subset of
the full KeepFit protocol (smartwatches support more features like weather, wallpaper, etc.).

## Firmware Version & OTA Updates

### Reading Firmware from Device

Two sources:

1. **Standard BLE DIS** (`0x180A` service): Characteristics `0x2A26` (Firmware Revision String) and
   `0x2A28` (Software Revision String). The app scans ALL services for these on every connect.

2. **Custom protocol `0x0C`**: Response contains firmware revision hex at bytes[9-12] as two LE u16
   values (e.g., `0x003A` + `0x002A` = `"003A002A"`). The `0xF6` notification provides the version
   number (e.g., `0x008A` = 138 → `V138`). Combined: `"003A002AV138"`.

### Server-Side Firmware Update

The official app checks keeprapid.com for newer firmware:

| Endpoint | Purpose |
|----------|---------|
| `http://download.keeprapid.com/apps/smartband/jring/autoupdater/{device_id}/update.json` | Jring firmware update info |
| `http://download.keeprapid.com/apps/smartband/keepfit/fwupdater/{hw}/{ver}/update.json` | Generic KeepFit firmware update |
| `http://api.keeprapid.com:8081/ronaldo-gearcenter` | Device binding + `checkDialServerInfo` firmware check |
| `http://download.keeprapid.com:8181/docs/jring/an_{p1}_{p2}1` | Firmware binary download |

Flashing uses **Renesas SUOTA** via the `0xFEF5` service to the DA14531 chip.

### keeprapid.com API Endpoints

| URL | Purpose |
|-----|---------|
| `http://api.keeprapid.com:8081/ronaldo-gearcenter` | Device binding, auth, firmware check |
| `http://api.keeprapid.com:8081/ronaldo-member` | Login, logout, feedback, member management |
| `http://api.keeprapid.com:8081/ronaldo-dc` | Health data upload, GPS upload |
| `https://openapi.keeprapid.com/developer` | SDK developer validation |
| `http://applog.keeprapid.com/` | App logging/analytics |

> **Security:** ALL traffic to `api.keeprapid.com` is HTTP (not HTTPS). Data is sent unencrypted.

## Background Sync

The official JRing app runs in the background and periodically pulls data from the ring. PulseLoop replicates this via WorkManager:
- **Interval**: Every 30 minutes (15 min flex window)
- **Behavior**: Connects to last-known ring, runs startup sync sequence, waits 15s for data, disconnects
- **Timeout**: 45s per sync attempt with exponential backoff on failure
- **Constraints**: Battery not low
- Background sync is canceled when forgetting the ring

## Sleep Stage Decoding

The ring stores sleep data as per-minute activity samples. Each minute is a single byte
indicating sleep depth. The official app uses **threshold-based** decoding:

| Byte Value | Stage |
|-----------|-------|
| `>= 80` (>= 0x50) | **Deep sleep** |
| `>= 1` | **Light sleep** |
| `0` | **Awake** |

> The values `0x28` (40) and `0x63` (99) commonly seen in captures are examples, not
the only values. The ring can emit any value in range depending on firmware variant.
Newer implementations should use thresholds, not exact-match.

**No REM stage is decoded.** The Jring protocol only distinguishes awake/light/deep.
Colmi R02 rings support REM via their V2 big-data format.

### How It Works End-to-End

1. App sends `0x10` with **byte[1] = days** (1 = yesterday, 0 = today)
2. Ring streams `0x11` packets with 15 × 1-minute stages each
3. Sync ends when timestamp reaches 23:45 local time
4. Each byte is threshold-decoded (>=80→deep, >=1→light, 0→awake)
5. Consecutive same-stage minutes are run-length encoded into blocks
6. Sleep quality score = deep sleep ratio: >=20%→90, >=15%→75, >=10%→60, <10%→40

**Common bug:** Sending `0x10` with byte[1] = 0 fetches zero days — no data arrives.

## Connection Behavior

- **Idle timeout**: Ring disconnects after ~20s of inactivity. Use keepalive ping (`0x3A`) every 15s.
- **Binding (ring-side, NOT OS bonding)**: The official app binds via the `0x4B` `setBindedInfo`
  protocol, not `createBond()`. On connect the ring drives a handshake (`INIT(0)` → app `APP_START(1)`
  → ring `ACK(2)` → app `SUCCESS(4)`); on "Forget" the app sends `UNBOND(5)` and waits for the ring's
  `UNBOND_ACK(6)` before disconnecting, so the ring drops its binding and re-advertises for other apps.
  See the `0x4B` command below. (`removeBond()` is still called as a best-effort fallback if an OS bond exists.)
- **Standard HR service**: The ring also exposes standard BLE Heart Rate Service (`0x180D`/`0x2A37`).

## References

- **Official protocol doc**: `深新锐蓝牙协议v3.0.docx` — [keeprapid/krwatch GitHub](https://github.com/keeprapid/krwatch/tree/master/doc%202)
- **Gadgetbridge KeepFit implementation**: [PR #5326](https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5326)
- **Full APK decompilation notes**: `keepfit-protocol-complete.md` (this repo)
- **Open-source firmware skeleton**: [ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
- **Smart Ring Protocol**: [saksham2001/Smart-Ring-Protocol](https://github.com/saksham2001/Smart-Ring-Protocol/)
- **Colmi R02 client**: [tahnok/colmi_r02_client](https://tahnok.github.io/colmi_r02_client/)
- **Gadgetbridge issue (BLE service screenshots)**: [#5190](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5190)
