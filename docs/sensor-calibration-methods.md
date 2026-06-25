# Sensor Calibration Methods

> Research notes on scientifically rigorous calibration for ring sensor data against
> reference medical devices (cuff BP monitors, lab glucose tests).
>
> Current approach: simple subtraction offset (`corrected = raw + offset`).
> This document explores more accurate alternatives.

## Current Implementation

- **BP**: User enters a single cuff reading (systolic/diastolic). Stored as
  `bpAdjustSystolic`/`bpAdjustDiastolic`. Sent to ring via `0x33`
  (`CMD_SET_BP_ADJUST`). Simple subtraction.
- **Blood sugar**: `glucoseOffsetMgdl = reference - latestRaw`. Applied at
  display time only (not sent to ring). Simple subtraction.

## Why Simple Offset Is Limited

A single offset cannot correct **proportional bias**. Example: if the ring
systematically underestimates high BP and overestimates low BP, a single offset
calibrated at normal BP will be wrong at both extremes.

```
Ring:     80 ────────── 120 ────────── 160
              \           |           /
               \    calibrated here   /
                \         |         /
Cuff:        85 ──────── 120 ────── 145

After offset cal at 120 (offset=0): 80→80 ✗  160→160 ✗
After linear regression:            80→85 ✓  160→145 ✓
```

## Recommended Methods (ranked by practicality)

### 1. Multi-Point Linear Regression — **recommended for BP**

Collect **2–5 paired readings** at different times of day (and thus different
BP/glucose levels), then fit:

```
corrected = slope × raw + intercept
```

Fitted via ordinary least squares (OLS):

```
slope     = Σ((rawᵢ - raw̄)(refᵢ - ref̄)) / Σ((rawᵢ - raw̄)²)
intercept = ref̄ - slope × raw̄
```

**Why this is the sweet spot:**
- Corrects both offset AND proportional bias
- Only needs 2 points to fit (more = better)
- Computationally trivial, runs on-device
- Industry standard for low-cost sensor calibration (IEEE 1708)
- Works well with limited user compliance (3 readings over 3 days is practical)

**User flow:**
1. Take ring reading → note value
2. Within ~2 min, take cuff/lab reading → enter reference value
3. Repeat 2–3 times at different times of day (morning, afternoon, evening)
4. System fits linear regression across all paired points
5. Optionally show Bland-Altman plot of calibration accuracy

### 2. Deming Regression — **better but needs error estimates**

Accounts for measurement error in BOTH the ring AND the reference device.
The reference device (consumer cuff, lab test) is not perfect either.

```
slope = (S_yy - λ·S_xx + √((S_yy - λ·S_xx)² + 4λ·S_xy²)) / (2·S_xy)
intercept = ref̄ - slope × raw̄

where λ = σ²_ring / σ²_ref (error variance ratio)
      S_xx, S_yy, S_xy are sums of squares
```

**Practical issue:** need an estimate of `λ` (error ratio). Can use
published accuracy specs:
- Consumer arm cuff: ~±5 mmHg SD → λ ≈ (σ_ring/σ_cuff)²
- Lab glucose: ~±5% CV → estimated from ring's known variance

**When to use:** if user provides 5+ paired readings and we can estimate
error variances from historical data.

### 3. Weighted Least Squares — **handles sensor drift**

If calibration decays over time, weight recent readings more heavily:

```
wᵢ = e^(-α · tᵢ)   where tᵢ = time since calibration point i
```

Older readings gradually decay in influence. `α` controls decay rate.

**Practical issue:** need to determine decay rate from data. Studies suggest
BP calibration remains accurate for hours to days, not minutes.

### 4. Bland-Altman Analysis — **validation, not calibration**

Use to show the user how good their calibration is:

```
bias = mean(refᵢ - correctedᵢ)
limits of agreement = bias ± 1.96 × SD(refᵢ - correctedᵢ)
```

Display as a scatter plot with mean difference and LoA lines.
> 95% of points within LoA = calibration is reliable.

## Blood Sugar Specifics

The 56ff ring's blood sugar is **profile-derived**, not a real sensor reading:

```
raw_glucose_mgdl = (byte[7] / 10) × 18.016
```

The ring computes this from sex/age/height/weight (sent via `0x02` `CMD_SET_USER_INFO`),
NOT from a glucose sensor. Blood pressure (bytes[2]-[3] of the same `0x24` packet) is a
**direct PPG sensor reading** — it does NOT use user profile at all. The two metrics
share a packet but have completely different data sources. This means:

1. **Linear regression helps less** — the ring's "raw" is already a modeled
   estimate, not a noisy sensor. Calibration can only correct systematic offset.
2. **Multi-point calibration may not help** — the ring doesn't respond to
   actual glucose changes. If you test fasting vs post-meal, the ring's raw
   value stays the same because your profile didn't change.
3. **Simple offset is probably sufficient** — given the fundamental limitation.
4. **Real improvement requires a different ring** — one with an actual glucose
   sensor (none exist in this price range).

## Practical Recommendation

| Aspect | Current | Recommended |
|--------|---------|-------------|
| **BP calibration** | Single offset | 2–3 point linear regression |
| **BP calibration UI** | Two text fields | Guided multi-reading flow with calibration quality indicator |
| **BP re-calibration** | Manual only | Prompt every 2–4 weeks |
| **Glucose calibration** | Single offset | Keep single offset (fundamentally limited by ring hardware) |
| **Glucose accuracy note** | Not shown | Show that this is a profile estimate, not a real reading |

## References

- **IEEE 1708**: Standard for Wearable, Cuffless Blood Pressure Measuring Devices
- **AAMI/ISO 81060-2:2013**: Non-invasive sphygmomanometer validation (≤5 mmHg MAE threshold)
- Seo et al. (2023): "Blood pressure estimation and its recalibration assessment
  using wrist cuff blood pressure monitor" — recalibration with linear regression
  achieved MAE of 5.10 ± 3.45 mmHg (SBP) and 4.22 ± 2.19 mmHg (DBP)
- Barvik et al. (2022): "Noninvasive continuous blood pressure estimation from
  pulse transit time: a review of the calibration models" — comprehensive survey
  of linear, non-linear, and ML calibration approaches
- Bland & Altman (1986): "Statistical methods for assessing agreement between two
  methods of clinical measurement" — the standard for method comparison
