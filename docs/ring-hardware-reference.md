# Smart Ring Hardware Reference

> Compiled from project documentation, web research, product pages, and teardowns.
> Last updated: 2026-06-22

---

## Platform Overview

Three distinct hardware platforms have been identified across the cheap smart ring market:

| Platform | SoC | Protocol | App | Price |
|---|---|---|---|---|
| **56ff / Jring** | Renesas DA14531 | Custom 56ff (SXR KeepFit SDK) | Jring / KeepFit | $7–12 |
| **Colmi / Yawell (QRing)** | Realtek RTL8762 family | Nordic-UART (QRing) | QRing | $15–30 |
| **Colmi R11 ("Da Rings")** | AB2026 (Actions/Airoha) | Unknown (different from QRing) | Da Rings | ~$15–25 |
| **SIMSONLAB** | Phyplus PHY6222 | Unknown | SIMSONLAB app | ~$10–20 |

---

## 1. 56ff / Jring Platform

### Manufacturer

- **keeprapid.com** (ShenXinRui / 深新锐) — Chinese OEM
- White-labels under brands: **Jring, KeepFit, JYouPro, RWfit, Tag**
- SDK: SXR KeepFit SDK (`com.sxr.sdk.ble.keepfit`)

### Hardware

| Component | Detail |
|---|---|
| **SoC** | Renesas DA14531 (Dialog DA145XX family) |
| **SoC architecture** | ARM Cortex-M0 |
| **PPG sensor** | Unknown (PPG HR/SpO₂, no skin temperature) |
| **Accelerometer** | Yes (steps, sleep stages, activity) |
| **Memory** | Unknown |
| **Weight** | Unknown |
| **Waterproof** | Unknown (varies by seller) |

### Protocol

| Property | Value |
|---|---|
| **Service UUID** | `000056ff-0000-1000-8000-00805f9b34fb` |
| **Write characteristic** | `000033f3-...` |
| **Notify characteristic** | `000033f4-...` |
| **Additional chars** | `0x33F5`, `0x33F6` (purpose unknown) |
| **Secondary service** | `0x57FF` |
| **SUOTA service** | `0000fef5-...` (firmware OTA) |
| **Frame size** | Fixed 20 bytes |
| **Encryption** | None (cleartext) |
| **Standard BLE services** | DIS (`0x180A`), Heart Rate Service (`0x180D`) |

### Capabilities

- Heart rate (BPM) — spot + live stream + history
- SpO₂ (%) — spot + history
- Steps / distance / calories
- Sleep stages: awake, light, deep (no REM)
- Blood pressure: systolic + diastolic (via `0x23`/`0x24` combined measurement)
- Blood sugar (profile-derived estimate, not real glucometer)
- Stress (0–100)
- Fatigue (0–100)
- HRV (ms)
- Battery level
- Find device

### What the 56ff ring CANNOT do

- REM sleep detection
- Body temperature (no skin temperature sensor — official app shows 0°C/32°F placeholder)
- Continuous streaming (command-response only, ~20s idle timeout)

### Known models

- **SR08** — open-source reference hardware ([atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring))
- Generic "SMART_RING" — sold on AliExpress for $7–12

### Firmware

- Firmware check: `http://download.keeprapid.com/apps/smartband/jring/autoupdater/{device_id}/update.json`
- Binary download: `http://download.keeprapid.com:8181/docs/jring/an_{p1}_{p2}1`
- Flashing: Renesas SUOTA (`0xFEF5` service) to DA14531
- Version format: `"003A002AV138"` (status hex + V + version number)
- **All API traffic is HTTP (not HTTPS) — unencrypted**

### Background behavior

- Idle timeout: ~20 seconds of inactivity
- Keepalive: `0x3A` ping/pong
- Binding: `0x4B` custom protocol (NOT OS `createBond()`)
- Official app runs periodic background sync

---

## 2. Colmi / Yawell — QRing Platform

### Manufacturer

- **Shenzhen Yawell Intelligent Technology Co., Ltd.** (est. 2016, Shenzhen)
- 3 factories, 16 assembly lines, 500+ workers, 100+ R&D engineers
- Largest smart ring factory in South China (5,000 m², established 2024)
- 150K+ monthly smart ring shipments
- OEM/ODM for Lenovo, Nokia, Skyworth, Noise, Titan, Fire Boltt
- First smart ring launched: 2023
- Official app: **QRing** (by Yawell)
- **Colmi** (Shenzhen Colmi Technology Co., Ltd.) is the most popular licensed brand selling Yawell's QRing rings
- Website: [yawellfit.com](https://www.yawellfit.com/), [colmi.com](https://www.colmi.com/)

### Protocol

| Property | Value |
|---|---|
| **BLE family** | Nordic-UART (`6e40fff0` / `de5bf728`) |
| **App** | QRing |
| **Encryption** | None |

### Models — QRing platform

| Model | CPU | Bluetooth | Battery | Waterproof | Display | Sensors | Notes |
|---|---|---|---|---|---|---|---|
| **R02** | Realtek RTL8762 | BLE 5.0 | Varies | IP68/3ATM | No | Unknown | Entry-level, "highly supported" per Gadgetbridge |
| **R03** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R06** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R07** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R09** | Realtek RTL8762 | BLE 5.0 | Varies | Unknown | No | Unknown | |
| **R10** | RTL8762 ESF | BLE 5.0 | 17 mAh | 5ATM | No | Vcare VC30F + STK8321 | Charging case: 200 mAh |
| **R12** | Realtek RTL8762 | BLE 5.0 | 15/18 mAh | IP68 + 1ATM | Yes | Vcare VC30F + ST LIS2DOC | Newest (2025), 4g weight |

### Yawell-branded QRing models

- R05, R10, R11, H59 — all use the same QRing protocol

### Colmi R11 — "Da Rings" Platform (ODDBALL)

The Colmi R11 is **not** a QRing ring. It uses a completely different stack:

| Component | Detail |
|---|---|
| **CPU** | AB2026 (Actions Semiconductor / Airoha) |
| **Bluetooth** | BLE 5.2 |
| **Battery** | 15 mAh |
| **Charging case** | 200 mAh |
| **Waterproof** | 5ATM |
| **App** | **Da Rings** (NOT QRing) |
| **Sensors** | Unknown |

The R11 is notably absent from the Colmi FAQ's QRing model list.

---

## 3. Colmi/Yawell — Capabilities

### Sensor details: Vcare VC30F

The VC30F is the PPG bio-sensor used in R10 and R12:

- **Red + green LED emitters** — dual wavelength for HR and SpO₂
- **Integrated photodiode** — detects reflected light with ambient light rejection
- **Analog front-end (AFE)** — filters and amplifies raw signal
- **Digital controller** — outputs processed pulse data
- Available on JLCPCB's parts library (traceable component)
- Real-world accuracy: within 1 BPM of medical-grade BP monitor (per R12 review)

### Sensor details: ST LIS2DOC (R12) / STK8321 (R10)

3-axis MEMS accelerometer for:
- Step counting and gesture detection
- Wear detection (wake on motion)
- Raw acceleration data for sleep and activity algorithms

### Capabilities per model

| Capability | R10 | R12 | R11 (Da Rings) | Other QRing¹ |
|---|---|---|---|---|
| **Heart rate — spot** | ✅ | ✅ | 🧪 | 🧪 |
| **Heart rate — history** | ✅ | ✅ | 🧪 | 🧪 |
| **Heart rate — live** | ✅ | ✅ | 🧪 | 🧪 |
| **SpO₂ — history** | ✅ | ✅ | 🧪 | 🧪 |
| **SpO₂ — spot** | —² | —² | —² | —² |
| **Steps / distance / calories** | ✅ | ✅ | 🧪 | 🧪 |
| **Sleep stages** (light/deep/awake) | ✅ | ✅ | 🧪 | 🧪 |
| **REM sleep** | ✅ | ✅ | 🧪 | 🧪 |
| **HRV** | ✅ | ✅ | 🧪 | 🧪 |
| **Stress** | ✅ | ✅ | 🧪 | 🧪 |
| **Body temperature** | ✅ | ✅ | 🧪 | 🧪 |
| **Battery level** | ✅ | ✅ | 🧪 | 🧪 |
| **Find device** | ✅ | ✅ | 🧪 | 🧪 |

¹ R02, R03, R06, R07, R09 + Yawell R05, R10, R11, H59
² Colmi family has no on-demand SpO₂ reading; SpO₂ is all-day background only

### What the Colmi family CAN do (that 56ff cannot)

- REM sleep detection
- Body temperature (skin temperature sensor)
- HRV
- Stress scoring
- Continuous background sync (autonomous notifications while worn)

---

## 4. SIMSONLAB Platform

### Manufacturer

- **SHARE AUDIO HONG KONG LIMITED** (developer of SIMSONLAB app)
- Product appears on Shein, AliExpress, TikTok
- Model: **LA380-YJ** (2025)

### Hardware

| Component | Detail |
|---|---|
| **CPU** | Phyplus PHY6222 |
| **SoC architecture** | ARM Cortex-M0 32-bit |
| **Bluetooth** | BLE 5.1 |
| **Memory** | 512 KB built-in, 64 KB SRAM, 128 KB–8 MB flash |
| **HR sensor** | HX3602 |
| **Battery** | 15 mAh (magnetic charging) |
| **Standby** | 10–15 days |
| **Weight** | ~5 g |
| **Material** | Stainless steel outer, epoxy resin body |
| **Waterproof** | IP68 |
| **App** | SIMSONLAB app (iOS + Android) |

### Protocol

- **Unknown** — completely different from both 56ff and QRing
- Custom BLE protocol (likely)
- Not compatible with PulseLoop's existing drivers

### HX3602 Sensor

- Generic/low-cost heart rate sensor
- Used across many cheap Chinese wearables (SIMSONLAB rings, NKX19 watches, DaintyDelight smartwatches)
- No standalone public datasheet found
- LED configuration unknown (likely single wavelength vs VC30F's dual red+green)
- No independent accuracy testing available

---

## 5. Quick Comparison

### Sensor quality

| | VC30F (Colmi R10/R12) | HX3602 (SIMSONLAB) | Unknown PPG (56ff) |
|---|---|---|---|
| **LEDs** | Red + green (dual) | Unknown | Unknown |
| **SpO₂ capable** | ✅ (red LED) | Unknown | ✅ |
| **Datasheet** | Public (JLCPCB) | None found | None found |
| **Verified accuracy** | ±1 BPM vs medical device | No testing found | No testing found |

### Chipset comparison

| | DA14531 (56ff) | RTL8762 (Colmi QRing) | AB2026 (Colmi R11) | PHY6222 (SIMSONLAB) |
|---|---|---|---|---|
| **Vendor** | Renesas | Realtek | Actions/Airoha | Phyplus |
| **Architecture** | ARM Cortex-M0 | ARM | ARM | ARM Cortex-M0 |
| **Bluetooth** | BLE 5.x | BLE 5.0 | BLE 5.2 | BLE 5.1 |
| **Memory** | Unknown | Unknown | Unknown | 512 KB built-in |
| **Known from** | Jring, KeepFit, RWfit, Tag | Colmi R02–R10, R12, Yawell | Colmi R11 only | SIMSONLAB LA380-YJ, various watches |

### Which to choose?

| If you want... | Pick |
|---|---|
| **Cheapest possible** ($7–12) | 56ff / Jring |
| **Most sensors** (temp, HRV, REM, stress) | Colmi R10 or R12 ($15–30) |
| **Best battery** | Colmi R10 (17 mAh + 200 mAh case, no display) |
| **On-ring display** | Colmi R12 |
| **Best waterproofing** | Colmi R10 or R11 (5ATM) |
| **Best HR accuracy** | Colmi R10/R12 (VC30F sensor, verified accuracy) |
| **Works with PulseLoop today** | 56ff Jring or Colmi QRing family |

---

## 6. References

- **PulseLoop protocol docs**: `docs/ring-protocol.md`, `docs/protocol-discoveries.md`, `docs/keepfit-protocol-complete.md`
- **Gadgetbridge Yawell/Colmi page**: [gadgetbridge.org/gadgets/wearables/yawell/](https://gadgetbridge.org/gadgets/wearables/yawell/)
- **Gadgetbridge KeepFit PR**: [#5326](https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/5326)
- **Open-source ring firmware**: [atc1441/ATC_SR08_Ring](https://github.com/atc1441/ATC_SR08_Ring)
- **Official KeepFit SDK/protocol**: [keeprapid/krwatch](https://github.com/keeprapid/krwatch)
- **Ring reverse engineering**: [ringverse/protocol](https://github.com/ringverse/protocol)
- **Colmi R12 review** (Walter Shillington, Medium, 2026-03-05)
- **Colmi R10 review** (ShaunChng.com)
- **SIMSONLAB LA380-YJ user manual** (device.report)
- **PHY6222 datasheet** (Phyplus Technologies)
- **Yawell company profile**: [yawellfit.com](https://www.yawellfit.com/p/about.html)
- **Colmi official**: [colmi.com](https://www.colmi.com/), [colmi.info](https://www.colmi.info/)
