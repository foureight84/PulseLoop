# KeepFit BLE Protocol — Complete Reference

> **Source:** Decompiled official Jring APK (v1.9.84) + Gadgetbridge PR #5326 implementation
> **Protocol:** ShenXinRui (SXR / 深新锐) KeepFit SDK v3.0
> **Manufacturer:** keeprapid.com (OEM for Colmi, Jring, JYouPro, RWfit, etc.)
> **SoC:** Renesas DA14531 (Dialog DA145XX)

## BLE Service & Characteristic UUIDs

| UUID | Purpose |
|------|---------|
| `000056ff-0000-1000-8000-00805f9b34fb` | Primary communication service |
| `000033f3-0000-1000-8000-00805f9b34fb` | **Write** characteristic (app → ring) |
| `000033f4-0000-1000-8000-00805f9b34fb` | **Notify** characteristic (ring → app) |
| `000033f5-0000-1000-8000-00805f9b34fb` | Additional characteristic (observed in APK) |
| `000033f6-0000-1000-8000-00805f9b34fb` | Additional characteristic (observed in APK) |
| `0000fef5-0000-1000-8000-00805f9b34fb` | Renesas SUOTA Service (firmware OTA) |
| `000057ff-0000-1000-8000-00805f9b34fb` | Secondary service (observed in APK) |
| `00002902-0000-1000-8000-00805f9b34fb` | CCCD (enable notifications) |

Standard BLE services also exposed:
- `0000180a-...` — Device Information Service (DIS)
- `0000180d-...` — Heart Rate Service (standard HR characteristic `0x2A37`)
- `0000ffe5-...` / `0000ffe9-...` — ANCS (Apple Notification Center)

## Packet Format

Every command/response is exactly 20 bytes:

```
Byte 0: Command ID (opcode)
Bytes 1-19: Payload (zero-padded)
```

Multi-byte integers are **little-endian** unless noted.

## Complete Command Table

### Device Setup

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x01` | Write | `CMD_SET_TIME` | Time sync: u32le Unix epoch (bytes 1-4) + i8 timezone offset (byte 5) |
| `0x02` | Write | `CMD_SET_USER_INFO` | User profile (age, height, weight, gender, step length) |
| `0x21` | Write | `CMD_SET_LANG` | Set locale string (e.g., "en-US") |
| `0x1D` | Write | `CMD_SET_HOUR_FORMAT` | 12h (=0) / 24h (=1) format |
| `0x48` | Write | `CMD_SET_APP_ID` | Application identifier string |
| `0x0E` | Write | `CMD_SET_DEVICE_MODE` | Device operational mode |

### Health — Combined Sensor Measurement

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x23` | Write | `CMD_TOGGLE_BLOOD_PRESSURE` | **Trigger combined measurement** (NOT SpO₂!). byte[1] = 1 to start, 0 to stop. Measures: HR + systolic + diastolic + SpO₂ + fatigue |
| `0x24` | Notify | `CMD_RECEIVED_SENSOR_DATA` | **Combined measurement result**: byte[1]=HR, byte[2]=systolic, byte[3]=diastolic, byte[4]=SpO₂%, byte[5]=fatigue/stress |
| `0x27` | Notify | `CMD_NOTIFY_SENSOR_DATA` | Sensor measurement complete notification |
| `0x28` | Notify | `CMD_NOTIFY_BLOOD_DATA` | Blood-related data notification |
| `0x3E` | Write | `CMD_TOGGLE_SPO2` | **SpO₂-only measurement**: byte[1] = 1/0 |
| `0x3F` | Notify | `CMD_RECEIVED_SPO2_DATA` | SpO₂ result: byte[1] = percentage (80-100) |

### Health — Heart Rate

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x14` | Write | `CMD_SET_LIVE_HEART_RATE_ENABLED` | Start live HR streaming. byte[1-4] = interval/config, byte[5] = activity slot (1-5) |
| `0x15` | Write | `CMD_SET_LIVE_HEART_RATE_DISABLED` | Stop HR measurement. byte[5] = activity slot |
| `0x19` | Write | `CMD_SET_AUTO_HEART_MODE` | Configure auto-HR interval + cadence |
| `0x16` | Write | `CMD_TRIGGER_HEART_RATE_REPORT` | Request stored HR history by days |
| `0x26` | Write | `CMD_SET_HEART_RATE_AREA` | Set HR alert thresholds (min, max) |

**Live HR response (`0x14` notify):**
- bytes[1-4]: Unix timestamp (u32le)
- byte[5]: Heart rate BPM
- byte[6]: Sleep status flag

### Health — Activity

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x03` | Notify | `CMD_GET_CUR_SPORT_DATA` | Current activity (sent on connect): bytes[1-4]=timestamp, bytes[5-8]=steps(u32le), bytes[9-12]=distance(m), bytes[13-16]=calories(kcal) |
| `0x10` | Write | `CMD_TRIGGER_ACTIVITY_REPORT` | Request stored activity/sleep history |
| `0x11` | Notify | `CMD_TRIGGER_ACTIVITY_REPORT_SLEEP_RESULT` | Sleep timeline: 15× 1-min stages. 0x00=awake, 0x28=light, 0x63=deep |
| `0x13` | Notify | `CMD_SPORT_REPORT_RESULT_CURRENT` | Activity summary (companion to 0x03). bytes[1-4]=timestamp, bytes[5-8]=steps, bytes[9-12]=duration, bytes[13-16]=step time |
| `0x1A` | Write | `CMD_SET_STEP_GOAL` | Set daily step goal (u32le) |
| `0x25` | Write | `CMD_TRIGGER_SPORT_REPORT` | Request sport mode report |

### Device Info

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x0B` | Write/Notify | `CMD_GET_BATTERY` | Battery: byte[1]=level(0-100), byte[2]=charging(1=yes) |
| `0x0C` | Write/Notify | `CMD_GET_DEVICE_INFO` | Device info: MAC address, firmware version |
| `0x06` | Write/Notify | `CMD_GET_DEVICE_COMMAND` | Device-originated commands: byte[1]=1(find phone),2(snap pic),16(music play/pause),32(next),64(prev) |
| `0x20` | Notify | `CMD_DEVICE_SUPPORTED_FUNCTIONS` | **Device capability bitfield** (which features are supported) |

### Alerts & Reminders

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x04` | Write | `CMD_SEND_VIBRATION_SIGNAL` | Find ring (blink/vibrate) |
| `0x05` | Write | `CMD_TRIGGER_LOST` | Anti-lost mode |
| `0x08` | Write | `CMD_SET_IDLE_TIME` | Idle/movement reminder |
| `0x09` | Write | `CMD_SET_SLEEP_TIME` | Sleep schedule |
| `0x0D` | Write | `CMD_SET_ALARM` | Set alarm |
| `0x12` | Write | `CMD_ALERT_NOTIFICATION` | Push notification to device (call, SMS, app notifications) |
| `0x31` | Write | `CMD_SET_REMINDER` | Medicine, drink, custom text alerts |

### Other

| Cmd | Direction | Constant | Description |
|-----|-----------|----------|-------------|
| `0x07` | Write | `CMD_SET_CAMERA_MODE` | Remote camera: byte[1] = 1 (open) / 0 (close) |
| `0x1B` | Write | `CMD_SET_DEVICE_INFO` | Device settings: vibrate, backlight, quiet mode |
| `0x22` | Write | `CMD_SET_WEATHER_DATA` | Weather forecast data |
| `0x34` | Read | `CMD_GET_DEVICE_WALLPAPER` | Get current wallpaper |
| `0x3A` | Write/Notify | `CMD_KEEPALIVE_PING` | Keepalive ping/pong |
| `0x44` | Write | `CMD_SET_MENSTRUAL_CYCLE` | Menstrual cycle tracking |

### Error Responses

Commands with the high bit set (0x80) indicate errors:
- `0x82` — User info set error
- `0x83` — Sport report error
- `0x85` — Anti-lost done
- `0x8B` — Battery read error
- `0x8C` — Device info read error
- `0x90` — Activity report error
- `0x96` — HR report error
- `0x9A` — Step goal set error
- `0xA3` — Blood pressure measurement done

## Key Discoveries (Corrections to Prior Docs)

### 1. `0x23` is Blood Pressure + Combined Measurement, NOT SpO₂

The command `0x23` starts a combined health measurement that returns ALL of:
- Heart rate (BPM)
- Systolic blood pressure (mmHg)
- Diastolic blood pressure (mmHg)
- Blood oxygen SpO₂ (%)
- Fatigue/stress level

The result arrives as `0x24` notification.

**This explains where BP data comes from** — it's part of the custom 56FF protocol, not standard BLE 0x1810/0x1808 services. The official app's `ACTION_NOTIFY_BLOOD_DATA` broadcast corresponds to `CMD_NOTIFY_BLOOD_DATA` (0x28).

### 2. SpO₂-Only Measurement Uses `0x3E` / `0x3F`

For SpO₂ without blood pressure, toggle with `0x3E` and receive results on `0x3F`.

### 3. `0x20` is Device Capabilities, Not Static Config

The `0x20` response is a bitfield describing which features the device supports. This is how the app knows whether to show BP/SpO₂/HR/camera/etc. UI elements.

### 4. `0x02` is User Profile, Not Activity Query

Setting user info (age, height, weight, gender) is `0x02`, not requesting activity.

### 5. Blood Pressure Source

Blood pressure readings **do NOT come from standard BLE services** (0x1810 Blood Pressure Service or 0x1808 Glucose Service). They come through the KeepFit custom protocol (`0x23` → `0x24`). The Google Health Connect `com.google.blood_pressure` references in the APK are for health data export, not for BLE data acquisition.

## Notification Subtypes

When sending `CMD_ALERT_NOTIFICATION` (0x12), the notification type is encoded as:

| ID | App |
|----|-----|
| 0 | Phone call |
| 1 | SMS |
| 2 | WeChat |
| 3 | QQ |
| 4 | Facebook |
| 5 | Skype |
| 6 | Twitter |
| 7 | WhatsApp |
| 8 | Line |
| 9 | KakaoTalk |
| 11 | DingTalk |
| 13 | Instagram |
| 14 | LinkedIn |
| 15 | Snapchat |
| 20 | Telegram |
| 25 | YouTube |
| 27 | TikTok |

## Device Command Subtypes (0x06)

When the device sends `CMD_GET_DEVICE_COMMAND` (0x06):
- byte[1]=1: Find my phone
- byte[1]=2: Take picture (camera remote)
- byte[1]=4: End phone call
- byte[1]=5: Request weather sync
- byte[1]=8: Answer phone call
- byte[1]=16: Music play/pause
- byte[1]=32: Music next
- byte[1]=64: Music previous
- byte[1]=65: Open camera
- byte[1]=66: Close camera
- byte[1]=68: Volume up
- byte[1]=69: Volume down

## Firmware Version & OTA Updates

### Reading Firmware Version from Device

Two sources for firmware version:

**1. Standard BLE Device Information Service (DIS)**

The ring exposes these standard DIS characteristics:
- `0x2A26` — Firmware Revision String (e.g., `"003A002AV138"`)
- `0x2A28` — Software Revision String

The official app scans ALL services for these characteristics on every connect:
```
found ORG_BLUETOOTH_CHARACTERISTIC_FIRMWARE_REVISION_STRING
found ORG_BLUETOOTH_CHARACTERISTIC_SOFTWARE_REVISION_STRING
```

**2. Custom Protocol `0x0C` (CMD_GET_DEVICE_INFO)**

Send `0x0C` to the ring, it responds with:
- bytes[3-8]: 6-byte MAC address
- bytes[9-12]: Firmware revision as two LE 16-bit values
  - bytes[9-10] → first part (e.g., `0x003A`)
  - bytes[11-12] → second part (e.g., `0x002A`)
  - Combined: `"003A002A"`

The version number (V138) comes from `CMD_GET_DEVICE_INFO` notification variant:
- bytes[4-5]: Version number as LE u16 (e.g., `0x008A` = 138 → `V138`)

Full firmware string = `{status_hex}V{version_number}` = e.g., `"003A002AV138"`

### Server-Side Firmware Update Check

The app checks for newer firmware via these HTTP endpoints:

| Endpoint | Purpose |
|----------|---------|
| `http://download.keeprapid.com/apps/smartband/jring/autoupdater/{device_id}/update.json` | Jring-specific firmware update info |
| `http://download.keeprapid.com/apps/smartband/keepfit/fwupdater/{hw_type}/{fw_version}/update.json` | Generic KeepFit firmware update info |
| `http://api.keeprapid.com:8081/ronaldo-gearcenter` | Device binding, auth, firmware check (`checkDialServerInfo`) |

### Firmware Download

Firmware binary download URL pattern:
```
http://download.keeprapid.com:8181/docs/jring/an_{param1}_{param2}1
```

### OTA Flashing

The ring uses **Renesas SUOTA** (Software Update Over The Air):
- **SUOTA Service UUID**: `0000fef5-0000-1000-8000-00805f9b34fb`
- **SoC**: Renesas DA14531 (Dialog DA145XX)
- The app's `SuotaManager` class handles the OTA flow (`OtaActivity`)
- Broadcasts: `ACTION_NOTIFY_DEVICE_OTA_ENABLE`, `ACTION_NOTIFY_SERVER_FIRMWARE_UPGRADE_*`

### API Endpoints (Full List)

| URL | Purpose |
|-----|---------|
| `http://api.keeprapid.com:8081/ronaldo-gearcenter` | Device binding, auth, firmware check |
| `http://api.keeprapid.com:8081/ronaldo-member` | Login, logout, feedback, member management |
| `http://api.keeprapid.com:8081/ronaldo-dc` | Health data upload, GPS upload |
| `https://openapi.keeprapid.com/developer` | SDK developer validation |
| `http://download.keeprapid.com:8181/docs/jring/an_%s_%s1` | Firmware binary download |
| `http://download.keeprapid.com/apps/smartband/jring/autoupdater/%s/update.json` | Jring firmware update check |
| `http://download.keeprapid.com/apps/smartband/keepfit/fwupdater/%s/%s/update.json` | Generic firmware update check |
| `http://download.keeprapid.com/apps/smartband/keepfit/dialinfo/%s/dail.json` | Watch face / dial info |
| `http://applog.keeprapid.com/` | App logging/analytics |

**Security note:** All `api.keeprapid.com` traffic is HTTP (not HTTPS) — data is sent unencrypted to the server.

## References

- Gadgetbridge PR: https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5326
- Official protocol doc: `深新锐蓝牙协议v3.0.docx` (keeprapid/krwatch GitHub)
- Open-source firmware skeleton: https://github.com/atc1441/ATC_SR08_Ring
- nRF Connect screenshots of BLE services (Gadgetbridge issue #5190)
