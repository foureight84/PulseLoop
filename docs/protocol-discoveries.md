# Jring 56ff Protocol — Decoding Discoveries

> Documented during the Android port. Based on live BLE packet captures from a Pixel 8
> connected to a Jring (SMART_RING, firmware 003A002AV138).

## Newly Decoded Packets

### 0x0C — Status Response (Firmware)

The status response contains the ring's MAC address AND firmware revision hex.

```
0c 8a 00 41 42 f5 90 0a dc 3a 00 2a 00 c5 78 99 70 1f 5e 30
│  └───┘ └─────────────┘ └───────────┘ └─────────────────────┘
│  status  MAC address    firmware hex   remaining addr bytes
└─ command
```

- **bytes[1-2]**: Status code (LE u16, typically `0x008a` = 138)
- **bytes[3-8]**: 6-byte MAC address (`41:42:F5:90:0A:DC`)
- **bytes[9-12]**: Firmware revision as two LE 16-bit values
  - Value 1: `(bytes[10]<<8 | bytes[9])` → `0x003A`
  - Value 2: `(bytes[12]<<8 | bytes[11])` → `0x002A`
  - Combined: `"003A002A"`

### 0xF6 — Firmware Version Number

The 0xF6 notification has two variants:

**Version variant** (firmware version number):
```
f6 00 00 00 8a 00 2a 00 00 00 00 00 00 00 00 00 00 00 00 00
│  └─────────┘ └──┘ └──┘
│  padding    ver  unknown
└─ command
```
- **bytes[4-5]**: Version number as LE u16 (`0x008a` = **138** → `V138`)
- **bytes[6-7]**: Unknown parameter (`0x002a` = 42)

**MAC variant** (device info):
```
f6 41 42 f5 90 0a dc 00 00 00 00 00 00 00 00 00 00 00 00 00
│  └─────────────────┘
│  6-byte MAC reversed
└─ command
```

**Full firmware**: `003A002AV138` = status `003A002A` + version `V138`

### 0x20 — Static Configuration

```
20 62 ec 07 38 09 0a be bb 02 00 1b 00 00 00 00 00 00 00 00
```
This response is **constant across all captures** — device configuration, not dynamic health data.
- bytes[1-4]: Timestamp or config ID
- bytes[5+]: Static parameter bytes (purpose unknown)

### 0x13 — Activity Summary Companion

```
13 36 f3 38 6a 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```
Companion to `0x03` (current activity). Timestamp at bytes[1-4], rest is padding.
Always arrives alongside `0x03`.

## Connection Behavior Discoveries

### Idle Timeout
The ring disconnects after **~20 seconds of inactivity**. After the sync burst
(status → 0x20 → battery → activity → 0x13 → 0xF6), if no further commands
are sent, the ring disconnects to save power.

**Fix**: `autoConnect=true` in `connectGatt()` matches the official app's
background connection behavior. Combined with a 15-second keepalive ping
(sending `0x0C` status query), the ring stays connected silently without
showing the Bluetooth status bar icon.

### OS-Level Bonding
The official app triggers `createBond()` on connect, showing the Android
system pairing dialog. Without this bond, Android caches the device and
requires a phone restart to re-discover it after "Forget".

**Fix**: Call `gatt.device.createBond()` on first connect (skipped if
`bondState == BOND_BONDED`). `removeBond()` in the disconnect flow clears
the bond on "Forget Ring".

## Blood Pressure & Blood Sugar

The official Jring app displays blood pressure (110/70 mmHg) and blood sugar
(111.70 mg/dL). These values **do not come through the custom 0x56FF protocol**.

### Standard BLE Health Services
The ring likely exposes standard BLE health device services:
- **Blood Pressure Service** (`0x1810`) → Measurement (`0x2A35`) — IEEE 11073 SFLOAT
- **Glucose Service** (`0x1808`) → Measurement (`0x2A18`) — IEEE 11073 SFLOAT, kg/L

The app now subscribes to these services and decodes SFLOAT values on notification.
SFLOAT format: byte[0] = exponent (4-bit signed), byte[1] = mantissa (12-bit signed).
Value = mantissa × 10^exponent.

**Verification pending**: Confirm whether the ring exposes 0x1810/0x1808 by checking
the services list in the diagnostics export. If not present, BP/glucose may come
through an undecoded 0x56FF command.

## Previously Confirmed (from reverse engineering)

| Command | Direction | Purpose |
|---|---|---|
| `0x01` | write | Time sync (u32le epoch + i8 timezone) |
| `0x02` | write | Activity query → triggers 0x03/0x13 |
| `0x03` | notify | Current activity (steps, distance, calories) |
| `0x04` | write | Find ring (buzz) |
| `0x0B` | notify | Battery percentage (custom protocol) |
| `0x0C` | write/notify | Status query → response with MAC + firmware |
| `0x10` | write | Sleep/history query → triggers 0x11 |
| `0x11` | notify | Sleep timeline (15×1-min stages) |
| `0x13` | notify | Activity summary companion to 0x03 |
| `0x14` | write/notify | Live HR (start/stop, BPM streaming) |
| `0x15` | write | Stop HR measurement |
| `0x16` | write/notify | History measurement query → bulk HR/temp data |
| `0x19` | write | Configure auto-HR window + cadence |
| `0x1A` | write | Set daily step goal |
| `0x21` | write | Set locale (e.g., "en-US") |
| `0x23` | write/notify | SpO₂ measurement (start/stop/result) |
| `0x24` | notify | SpO₂ result percentage |
| `0x27` | notify | HR measurement complete |
| `0x28` | notify | SpO₂ measurement complete |
| `0x48` | write | App identifier |
| `0xF6` | notify | Firmware version number + device info |

## Still Unknown

| Command | Observed | Hypothesis |
|---|---|---|
| `0x20` | Static config bytes | Device configuration, not health data |
| `0x13` bytes 5+ | Always zero | Activity summary detail (unused by ring) |
| `0x16` bytes 9+ | Pattern varies (`52 52...` or `01 00...`) | May encode temperature or other metrics |
| BP/glucose source | Not yet confirmed | Likely standard BLE 0x1810/0x1808 services |
