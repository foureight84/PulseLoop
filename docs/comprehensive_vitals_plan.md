# Comprehensive Vitals Plan — Threshold Bars & Tap-Through Detail Screens

> Status: **design / not yet implemented**. This doc covers points 2 and 3 of the
> Vitals redesign. Point 1 (BP & Blood Sugar panels matching the other cards' style)
> shipped separately in commit `8da555b`.

## Goal

Two additions to the Vitals experience:

1. **Threshold bar** — every metric panel on the Vitals screen gets a small,
   color-coded horizontal bar that shows where the current value sits across a
   good → average → concerning scale, with a consistent palette across all metrics.
2. **Tap-through detail screen** — tapping any panel opens a simplified,
   trend-focused detail view (inspired by the official ring app's detail pages, but
   deliberately *less* dense). Each detail screen shows the trend over Today/Week/Month,
   a plain-language read of whether the metric is trending up or down, a color legend,
   a short explainer, and a clear non-medical disclaimer.

**Guiding principle:** simplify. The reference screenshots (official app) are
information-dense. We want a user to glance and understand "is this good, and which
way is it heading?" without reading a data table. We never give medical advice and we
always state these are not definitive/clinical readings.

---

## Current state (what already exists)

| Piece | Location |
| --- | --- |
| Vitals screen with 8 panels | `ui/screens/Screens.kt` → `VitalsScreen` |
| Per-metric state + 24h polling | `ui/viewmodels/ViewModels.kt` → `VitalsViewModel` |
| `SimpleLineChart`, `SimpleDualLineChart` | `ui/components/Charts.kt` |
| Measurement storage | `data/dao/Daos.kt` → `MeasurementDao` (`range`, `latest`, `rangeFlow`) |
| Measurement kinds + units | `ring/RingDecodedEvent.kt` → `MeasurementKind` |
| Capability gating | `ring/WearableCapability.kt` |
| Navigation (`NavHost`) | `ui/PulseLoopApp.kt` |
| Brand colors / theme | `ui/theme/Theme.kt` |

`MeasurementDao` today only supports raw `range(kind, start, end)` and `latest(kind)`.
There is **no week/month aggregation** yet — that's added in Part 3.

---

# Part 2 — Color-coded threshold bars

## 2.1 Shared semantic palette

One palette, reused by every metric, so colors mean the same thing everywhere.
Define these in a new file `ui/theme/MetricColors.kt` (or extend `Theme.kt`):

| Token | Hex | Meaning |
| --- | --- | --- |
| `ZoneGood` | `0xFF43A047` | optimal / healthy |
| `ZoneNormal` | `0xFF00897B` | normal / acceptable |
| `ZoneBorderline` | `0xFFFFB300` | borderline / watch |
| `ZoneConcern` | `0xFFE53935` | high / concerning |
| `ZoneLow` | `0xFF1E88E5` | below normal range (low) |

Keep each metric to **≤4 zones** so the bar stays readable.

## 2.2 Threshold model

New file `ui/components/MetricThresholds.kt`:

```kotlin
data class ThresholdZone(
    val label: String,        // "Normal", "Elevated", …
    val start: Double,        // inclusive lower bound on the display scale
    val end: Double,          // exclusive upper bound
    val color: Color,
)

data class MetricThresholds(
    val displayMin: Double,            // left edge of the bar
    val displayMax: Double,            // right edge of the bar
    val zones: List<ThresholdZone>,    // contiguous, covering [displayMin, displayMax]
    val higherIsBetter: Boolean?,      // for the trend read in Part 3 (null = neutral/relative)
    val unitLabel: String,             // "bpm", "%", "mg/dL", "mmHg", …
) {
    fun zoneFor(value: Double): ThresholdZone? =
        zones.firstOrNull { value >= it.start && value < it.end } ?: zones.lastOrNull()
}

object MetricThresholdTable {
    fun forKind(kind: MeasurementKind): MetricThresholds? = when (kind) { … }
}
```

### Draft zone tables

All values are **general wellness reference ranges, not diagnostic thresholds** —
documented as such in the UI. Tune against the official app's bands where they exist
(stress/fatigue already follow the official 20/40/60/80 split in `Screens.kt`).

- **SpO₂ (%)** — `displayMin 85 … displayMax 100`, higherIsBetter = true
  - `<90` Concern (red) · `90–94` Low (amber) · `95–100` Good (green)
- **Stress (0–100)** — higherIsBetter = false
  - `<30` Relaxed (green) · `30–59` Normal (teal) · `60–79` Moderate (amber) · `80–100` High (red)
- **Fatigue (0–100)** — same split/colors as stress
- **Heart Rate (bpm, resting/avg)** — `displayMin 40 … displayMax 140`, higherIsBetter = false
  - `<50` Low (blue) · `50–90` Good (green) · `90–120` Elevated (amber) · `>120` High (red)
- **HRV (ms)** — `displayMin 0 … displayMax 120`, higherIsBetter = true, **flagged personal/relative**
  - `<20` Low (red) · `20–50` Below avg (amber) · `≥50` Good (green)
- **Skin Temperature (°C)** — wide normal green band with deviation flags
  - `<31` Low (blue) · `31–36` Normal (green) · `>36` Elevated (amber). Informational only;
    skin temp is not body temperature. Respect the user's C/F unit setting via `UnitConverter`.
- **Blood Pressure (mmHg)** — bar scaled on systolic `displayMin 80 … displayMax 180`,
  but zone selection uses the **worse** of systolic/diastolic category:
  - Normal `sys<120 & dia<80` (green) · Elevated `sys 120–129 & dia<80` (amber) ·
    Stage 1 `sys 130–139 or dia 80–89` (orange `0xFFFB8C00`) · High `sys≥140 or dia≥90` (red)
- **Blood Sugar (mg/dL)** — `displayMin 50 … displayMax 200`, higherIsBetter = false
  - `<70` Low (blue) · `70–99` Normal (green) · `100–125` Elevated (amber) · `≥126` High (red)
  - Note in UI: ranges differ for fasting vs. post-meal; treat as a rough guide only.

> If/when a unit toggle for glucose (mg/dL ↔ mmol/L) or temperature is in play, convert
> the value into the table's unit before `zoneFor`, or keep parallel tables per unit.

## 2.3 `ThresholdBar` composable

New composable in `ui/components/Charts.kt` (or `ThresholdBar.kt`):

```kotlin
@Composable
fun ThresholdBar(
    value: Double?,
    thresholds: MetricThresholds,
    modifier: Modifier = Modifier,
    showTicks: Boolean = true,
)
```

Rendering:
- A rounded-capsule `Canvas`, height ~14.dp, full width.
- Each zone drawn as a segment whose width ∝ `(zone.end - zone.start) / (displayMax - displayMin)`.
- A marker at the current value: a small downward triangle / vertical pill positioned at
  `(value - displayMin) / (displayMax - displayMin)`, clamped to `[0,1]`. Hidden when `value == null`.
- Optional min/max tick labels under the ends (`displayMin` … `displayMax`) and the
  current zone's `label` shown next to the value in the panel.
- Reuse the existing `Canvas` + rounded-rect drawing approach already used by the charts.

## 2.4 Wiring into the panels

In `VitalsScreen`, under each panel's value/range line, add:

```kotlin
MetricThresholdTable.forKind(kind)?.let { th ->
    Spacer(Modifier.height(8.dp))
    ThresholdBar(value = currentValue, thresholds = th)
}
```

- HR → `latestHr`, SpO₂ → `latestSpo2`, Stress → `latestStress`, Fatigue → `latestFatigue`,
  HRV → `latestHrv`, Temp → `latestTemp` (display unit), Glucose → `bloodSugar`.
- BP is special: pass systolic as the bar value but compute the zone from both sys+dia
  via a small `bpZone(sys, dia)` helper (returns the worse category).

**Touch nothing in the data layer for Part 2** — it only reads values already in `VitalsState`.

---

# Part 3 — Tap-through detail screens

## 3.1 Navigation

In `ui/PulseLoopApp.kt` `NavHost`, add a parameterized route:

```kotlin
composable("vitals/{metric}") { backStackEntry ->
    val metric = backStackEntry.arguments?.getString("metric") ?: return@composable
    VitalDetailScreen(
        metric = metric,
        onBack = { navController.popBackStack() },
        db = db,            // same DB instance the other VMs receive
        apiKeyStore = apiKeyStore,
    )
}
```

Make each panel `Card` clickable in `VitalsScreen`:

```kotlin
Card(Modifier.fillMaxWidth().clickable { navController.navigate("vitals/$metricKey") }) { … }
```

`metricKey` is a stable string per panel, e.g. the `MeasurementKind.key` (`"hr"`, `"spo2"`,
`"stress"`, `"fatigue"`, `"hrv"`, `"temp"`, `"glucose"`) plus a synthetic `"bp"` for blood
pressure (which spans two kinds). Map `"bp"` → systolic+diastolic inside the detail VM.

> `VitalsScreen` currently receives `coordinator` but not a `NavController`. Add a
> `navController` parameter (the other screens already take one) and pass it from `PulseLoopApp`.

## 3.2 Data layer — Week/Month aggregation

Today only 24h raw `range` exists. For Week/Month we want one point per hour (Day) or per
day (Week/Month) so charts stay legible and queries stay cheap. Add to `MeasurementDao`:

```kotlin
// Average per UTC-day bucket — used for Week (7 buckets) and Month (~30 buckets).
@Query("""
    SELECT CAST(timestamp / 86400000 AS INTEGER) * 86400000 AS bucket,
           AVG(value) AS avgValue, MIN(value) AS minValue, MAX(value) AS maxValue
    FROM measurements
    WHERE kindRaw = :kind AND timestamp BETWEEN :start AND :end
    GROUP BY bucket ORDER BY bucket ASC
""")
suspend fun dailyAggregates(kind: String, start: Long, end: Long): List<Bucket>

// Average per hour bucket — used for the Day view to smooth dense sampling.
@Query("""
    SELECT CAST(timestamp / 3600000 AS INTEGER) * 3600000 AS bucket,
           AVG(value) AS avgValue, MIN(value) AS minValue, MAX(value) AS maxValue
    FROM measurements
    WHERE kindRaw = :kind AND timestamp BETWEEN :start AND :end
    GROUP BY bucket ORDER BY bucket ASC
""")
suspend fun hourlyAggregates(kind: String, start: Long, end: Long): List<Bucket>
```

`Bucket` is a small POJO (`bucket: Long, avgValue: Double, minValue: Double, maxValue: Double`).
Day-bucketing on `timestamp / 86400000` is UTC; if local-day alignment matters, offset by the
device's UTC offset before bucketing (acceptable to defer — note it as a known simplification).

## 3.3 `VitalDetailViewModel`

New VM in `ViewModels.kt` (or its own file):

```kotlin
// Display labels: DAY → "Today", WEEK → "Week", MONTH → "Month". Default = DAY.
enum class Period(val label: String) { DAY("Today"), WEEK("Week"), MONTH("Month") }

class VitalDetailViewModel(
    private val db: PulseLoopDatabase,
    private val metric: String,
    private val apiKeyStore: ApiKeyStore?,
) : ViewModel() {
    data class DetailState(
        val period: Period = Period.DAY,
        val anchor: Long = startOfToday(),      // selected day/week/month start
        val points: List<Double> = emptyList(), // primary series (bucket averages)
        val secondary: List<Double> = emptyList(), // diastolic for BP, else empty
        val labels: List<String> = emptyList(),  // x-axis labels (hours or dates)
        val latest: Double? = null,
        val min: Double? = null,
        val avg: Double? = null,
        val max: Double? = null,
        val trend: Trend = Trend.FLAT,           // vs previous comparable window
        val thresholds: MetricThresholds? = null,
        val loading: Boolean = true,
    )
    enum class Trend { UP, DOWN, FLAT }
    // setPeriod(), prev()/next() to move the anchor, refresh() reusing the polling pattern.
}
```

Behavior:
- **Today** (`DAY`) → `hourlyAggregates` over the selected day; labels `00,06,12,18,23`.
- **Week** → `dailyAggregates` over 7 days; labels = weekday short names.
- **Month** → `dailyAggregates` over ~30 days; labels = day-of-month at intervals.
- **BP** loads both systolic and diastolic kinds; everything else single-series.
- **Glucose** applies `glucoseOffsetMgdl`; **temp** converts to the user's unit for display.
- **Trend**: compare this window's average to the previous equivalent window
  (`prev day`/`prev week`/`prev month`). `UP`/`DOWN` if the delta exceeds a small noise
  threshold (e.g. >2% of range), else `FLAT`. Combine with `thresholds.higherIsBetter`
  to phrase it as positive/negative in the UI (see 3.5).
- Poll/refresh on the same 5s cadence as `VitalsViewModel` so live syncs appear.

## 3.4 `VitalDetailScreen` — layout (kept simple)

New composable in `Screens.kt`. Top-to-bottom, generous spacing, **not** a dense table:

1. **Top bar** — back arrow + metric name (`Scaffold`/`TopAppBar`).
2. **Period selector** — a segmented control with three tabs in this order:
   **`Today` · `Week` · `Month`**, with **`Today` selected by default**. Use a
   `SingleChoiceSegmentedButtonRow` (or 3 `FilterChip`s). Drives `setPeriod`. The tab
   *labels* are `Today`/`Week`/`Month`; the underlying `Period` enum stays
   `DAY`/`WEEK`/`MONTH` (map `DAY → "Today"` for display).
3. **Date navigator** — `‹  <date label>  ›` row; arrows call `prev()/next()`, forward
   disabled at "today/this period".
4. **Hero trend chart** — one large chart (~180–220.dp tall). Extend `SimpleLineChart`
   into a `TrendChart` that adds: light gradient fill under the line, a few gridlines,
   x-axis labels from `state.labels`, and the value axis min/max. BP uses the dual-line
   variant with the Sys/Dia legend.
5. **Trend read** — a single prominent line: an up/down/flat arrow + plain text, e.g.
   *"Your average is trending down — that's usually a positive direction"* (see 3.5).
   Color the arrow with the semantic palette (green = favorable, amber/red = unfavorable).
6. **Stat tiles** — a row of compact tiles: **Latest · Avg · Min · Max** (reuse the
   `StagePill`/`MetricTile` styling already in the app).
7. **Threshold bar + legend** — the same `ThresholdBar` from Part 2, full width, followed
   by a compact legend (color swatch + zone label for each zone, reusing `LegendDot`).
8. **Explainer + disclaimer** — a short, plain-language paragraph about what the metric
   means (1–3 sentences, non-clinical), then the fixed disclaimer (3.6).

Empty state: if no data for the window, show a friendly "No <metric> data for this period —
sync your ring or take a measurement" and hide chart/stats.

## 3.5 Trend wording (no medical advice)

Map `(Trend, higherIsBetter)` → a neutral sentence. Examples:

| Trend | higherIsBetter | Copy |
| --- | --- | --- |
| DOWN | false (e.g. stress, resting HR) | "Trending down — generally a positive direction." |
| UP | false | "Trending up vs the previous period." |
| UP | true (e.g. HRV, SpO₂) | "Trending up — generally a positive direction." |
| DOWN | true | "Trending down vs the previous period." |
| FLAT | any | "Holding steady." |
| any | null (HRV personal, temp) | "Trending up/down vs your recent baseline." |

Rules: describe direction and a soft "generally positive/negative", never instruct
("you should…"), never diagnose, never reference conditions. Keep it one sentence.

## 3.6 Standard disclaimer (every detail screen)

> *For reference only — not a substitute for medical devices, and not a medical diagnosis.
> Wearable readings can vary; talk to a healthcare professional about any health concerns.*

Mirrors the existing "For reference only, not a substitute for medical devices" line in the
official BP screen and keeps us clear of medical-advice territory.

---

## Files touched (summary)

**Part 2**
- `ui/components/MetricThresholds.kt` *(new)* — zone tables + `zoneFor`.
- `ui/theme/MetricColors.kt` *(new)* — shared zone palette.
- `ui/components/Charts.kt` — add `ThresholdBar`.
- `ui/screens/Screens.kt` — render a `ThresholdBar` in each panel; `bpZone` helper.

**Part 3**
- `data/dao/Daos.kt` — `hourlyAggregates` / `dailyAggregates` + `Bucket` POJO.
- `ui/viewmodels/ViewModels.kt` — `VitalDetailViewModel`, `Period`, `Trend`.
- `ui/screens/Screens.kt` — `VitalDetailScreen`, `TrendChart`; make panels `clickable`;
  add `navController` param to `VitalsScreen`.
- `ui/PulseLoopApp.kt` — `vitals/{metric}` route + pass `navController` to `VitalsScreen`.

No Room schema/entity change is required (aggregates are read-only queries), so **no DB
migration** is needed.

---

## Suggested build order

1. Part 2 thresholds (self-contained, no nav/data changes) → ship + verify on device.
2. Part 3 data layer (`dailyAggregates`/`hourlyAggregates`) + `VitalDetailViewModel`.
3. Part 3 `VitalDetailScreen` + navigation wiring.
4. Polish: `TrendChart` axis labels/gradient, trend copy, empty states.

## Verification

- **Part 2**: open Vitals — every panel shows a threshold bar with the marker in the
  correct zone for the current value; colors are consistent across metrics; BP zone reflects
  the worse of sys/dia. Take a measurement and confirm the marker moves.
- **Part 3**: tap each panel → detail opens defaulting to the **Today** tab;
  Today/Week/Month switches the chart and stats;
  date arrows move the window (forward disabled at present); trend line reads sensibly;
  legend + disclaimer present; empty windows show the friendly empty state.
- `./gradlew assembleDebug` clean; install via `adb install -r` and smoke-test on device.
