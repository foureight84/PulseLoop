# PulseLoop → Android Porting Plan

> Generated from graph analysis of `/home/khoa/projects/PulseLoopIOS`  
> 3258 nodes · 6589 edges · 181 communities · 119 Swift + 49 Kotlin files

## Port Status: 87% feature coverage

## Workflow

1. Branch `feature/android` — integration branch for all merged phases
2. Per phase (1–7):
   - Create `feature/android_phaseN` from `feature/android`
   - Implement the phase per this plan
   - Commit, push, run `graphify update .`
   - Senior-dev code review → apply fixes, commit
   - No-ff merge `feature/android_phaseN` → `feature/android`
   - Update this file's phase status
7. Repeat 2–6 until Phase 7 is merged
8. **Start a new session** before beginning the next phase (do not reuse context)

---

## Porting Gap Analysis

### Data Flow (not wired)

| Component | iOS | Android Status |
|---|---|---|
| Ring → BLE → EventBus → DB | `RingBLEClient` → `PulseEventBus` → `EventPersistenceSubscriber` → SwiftData | BLE client + event bus ported. **No subscriber writes events to Room.** |
| DB → ViewModels → UI | SwiftData `@Query` → `@Observable` ViewModels → SwiftUI | Room DAOs ported. **No ViewModels. Screens use hardcoded strings.** |
| App startup | `PulseLoopApp.swift`: init BLE, DB, subscriber, auto-connect | **No startup wiring. BLE client created but never auto-connects.** |
| CoachContextBuilder | Reads real DB: profile, device, goals, trends, data quality | **Not ported. Coach tools return mock JSON.** |

### UI (not implemented or unreachable)

| Screen | iOS File | Android Status |
|---|---|---|
| Pairing / ring discovery | `PairingView.swift` | ✅ Added in Phase 8 |
| Onboarding flow | `RootViews.swift` | ❌ Not ported |
| Debug / diagnostics | `DebugView.swift` | ❌ Not ported |
| Workout detail / summary | `RecordViews.swift` | ❌ Not ported (partial in RecordScreen) |
| Measurement modal | `MeasurementModal.swift` | ❌ Not ported |
| Settings | `SettingsView.swift` | ✅ Code exists, **no navigation route** |
| Activity detail drill-down | `ActivityView.swift` | ❌ Not ported |

### Coach Features (code exists, not wired)

| Feature | iOS File | Android Status |
|---|---|---|
| CoachContextBuilder | Reads real profile/device/goals/trends from DB | ❌ Mock data only |
| CoachDataAccess | Queries Room for activity, measurements, sleep | ❌ Not ported |
| CoachViewModel | `CoachViewModel.swift` — orchestrator + UI state | ❌ Not ported |
| CoachFallbacks | Graceful degradation when coach fails | ❌ Inline in orchestrator, not a separate module |
| JSONRepair | Fixes malformed model output | ❌ Basic extraction in CoachResponseParser only |
| Coach summaries | `CoachSummaryCoordinator`, `CoachSummaryService` | ❌ Not ported |
| Coach notifications (AI content) | `CoachNotificationGenerator` — LLM-generated check-ins | ⚠️ Static message only, no AI content |

### Persistence Features (DAOs exist, logic missing)

| Feature | iOS File | Android Status |
|---|---|---|
| EventPersistenceSubscriber | Listens to bus, writes measurements/sleep/activity to DB | ❌ Not ported |
| ActivityService | Apply activity updates + bucket summing (idempotent) | ❌ Not ported |
| MetricsService / DerivedSummaries | Daily trends, 7-day averages, resting HR | ❌ Not ported |
| SleepInsights | Sleep scoring, stage analysis | ❌ Not ported |
| Repositories | Typed fetch helpers (DeviceRepository, ActivityRepository) | ⚠️ DAOs exist but no repository wrappers |
| SeedData | Demo data generation | ✅ Ported (DemoDataSeeder) |

### Workout (managers ported, no UI integration)

| Feature | iOS File | Android Status |
|---|---|---|
| LiveWorkoutManager integration | Wired via `@Environment` to RecordLiveView | ❌ Manager exists, no UI binding |
| Workout detail / summary | `RecordViews.swift` (1079 lines) | ❌ Not ported — RecordScreen is minimal |
| GPS point persistence | `EventPersistenceSubscriber` persists `ActivityGpsPoint` | ❌ Not ported |
| Workout recovery | Resume interrupted workout on relaunch | ❌ Not ported |

### Navigation (routes missing)

| Route | iOS | Android |
|---|---|---|
| Today (default) | ✅ | ✅ |
| Vitals | ✅ | ✅ |
| Sleep | ✅ | ✅ |
| Activity | ✅ | ✅ |
| Coach | ✅ | ✅ |
| Pairing / Scan | `PairingView` | ✅ Added Phase 8 |
| Settings | `SettingsView` | ❌ No route |
| Workout recording | `RecordLiveView` | ❌ No route |
| Workout detail | `RecordDetailView` | ❌ No route |
| Debug | `DebugView` | ❌ Not ported |
| Onboarding | Sheet in `RootViews` | ❌ Not ported |

---

## Summary

## Summary

PulseLoop is ~126 Swift files across 4 architectural layers. The port is **feasible** but requires replacing Apple-specific frameworks at every layer. The core domain logic — ring protocol decoding, coach orchestration, tool system, and data models — is platform-agnostic. The UI, persistence, BLE, and system integration layers need complete rewrites.

**Estimated effort:** 2–3 months for a single developer familiar with both platforms.

---

## Architecture Layer Mapping

| iOS Layer | Community | Android Equivalent | Port Strategy |
|---|---|---|---|
| **RingProtocol/** (BLE, decoding, encoding) | 32, 42, 0, 23, 36, 39, 53, 56 | `android.bluetooth.le` + Kotlin | **Port directly** — protocol logic is arithmetic, not platform |
| **Models/** (SwiftData entities) | 9, 20, 31, 33 | Room `@Entity` + `@Dao` | **Rewrite schema** — same relationships, Room annotations |
| **Services/** (sync, workouts, GPS, summaries) | 1, 4, 14, 16, 17, 19, 25, 26 | Kotlin coroutines + Flow | **Port logic, swap platform APIs** |
| **Coach/** (LLM orchestration, tools, OpenAI client) | 3, 5, 7, 29, 34, 35, 43, 44, 49, 50, 51, 52, 54, 57, 58 | Kotlin (pure logic, no platform deps) | **Near-direct port** — mostly JSON/HTTP, no Apple deps |
| **Views/** (SwiftUI screens) | 2, 12, 13, 24, 40, 41 | Jetpack Compose | **Rewrite** — different declarative UI framework |
| **DesignSystem/** (charts, components) | 11, 18, 22, 59 | Compose Material 3 + Vico/MPAndroidChart | **Rewrite** — custom chart components |
| **PulseLoopLiveActivity/** (Live Activity, Dynamic Island) | 8, 21 | Foreground Service + Notification | **Redesign** — no Dynamic Island on Android |
| **Diagnostics/** | 6, 37 | Timber / custom logger | **Port** — simple logging utility |
| **Events/** | 26, 38 | Kotlin Flow / SharedFlow | **Direct port** — event bus is a pattern, not a framework |

---

## Detailed Layer Breakdown

### 1. Ring Protocol (port strategy: **direct translation**)

**Files:** `RingProtocol.swift`, `RingBLEClient.swift`, `ColmiDecoder.swift`, `ColmiEncoder.swift`, `ColmiDriver.swift`, `ColmiCoordinator.swift`, `ColmiSyncEngine.swift`, `ColmiProtocol.swift`, `JringDriver.swift`, `JringCoordinator.swift`, `JringSyncEngine.swift`, `RingEventBridge.swift`, `WearableDriver.swift`, `WearableCoordinator.swift`

| Swift | Kotlin/Android |
|---|---|
| `CoreBluetooth` / `CBCentralManager` | `BluetoothLeScanner` / `BluetoothGatt` |
| `CBPeripheral` / `CBCharacteristic` | `BluetoothGattCharacteristic` |
| `@MainActor` | `Dispatchers.Main` / `viewModelScope` |
| `@Observable` class | `StateFlow<State>` in a ViewModel |
| `Data` / `[UInt8]` | `ByteArray` |
| `enum RingDecodedEvent: Sendable` | `sealed class RingDecodedEvent` |
| `Notification` fan-out (`publish`) | `SharedFlow<RingDecodedEvent>` |
| `RingCommandWriter` protocol | `fun interface RingCommandWriter` |
| `WearableDriver` protocol | `interface WearableDriver` |
| `WearableCoordinator` protocol | `interface WearableCoordinator` |

**Key insight from the graph:** `RingBLEClient` (43 edges) is the hub. The write serialization queue (one outstanding `withResponse` write at a time) is protocol-critical — this exact logic ports 1:1 to Android's `BluetoothGatt.writeCharacteristic()` with a coroutine `Mutex`.

**The coordinator registry pattern** (`static let coordinators: [WearableCoordinator.Type]`) becomes a `listOf(JringCoordinator, ColmiCoordinator)` — same architecture, same extensibility.

**Risk:** Android BLE is notoriously inconsistent across manufacturers. The port needs:
- A `BluetoothGattCallback` wrapper handling Samsung/OnePlus/Xiaomi quirks
- `ScanFilter` / `ScanSettings` tuned for background scanning on Android 12+
- Runtime permission handling (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`)

### 2. Data Models & Persistence (port strategy: **schema rewrite**)

**Files:** `PulseModels.swift` (600+ lines), repositories in `Services/`

| SwiftData | Room |
|---|---|
| `@Model final class Device` | `@Entity data class DeviceEntity` |
| `@Attribute(.unique) var id: UUID` | `@PrimaryKey val id: String` |
| `@Relationship` | `@ForeignKey` / `@Relation` |
| `ModelContext` (auto-save) | `@Dao` + explicit `suspend fun` |
| `@MainActor` context operations | `Dispatchers.IO` for DB, `StateFlow` for UI |
| `Codable` enums (`RingConnectionState`, `MeasurementKind`) | `@TypeConverter` or `kotlinx.serialization` |
| `var ...: Date?` | `var ...: Long?` (epoch millis) or `Instant?` |

**Models to port:**
- `Device` — ring identity, connection state, battery
- `Measurement` — heart rate, SpO₂, stress, HRV, temperature samples
- `ActivitySession` — workout recording with GPS points
- `ActivityDaily` — aggregated daily steps/distance/calories
- `SleepSession` — sleep stages (light/deep/awake/REM)
- `CoachMessage` / `CoachConversation` — chat history
- `CoachMemory` — long-term coach context
- `CoachNotificationRecord` — scheduled/delivered check-ins

**Migration strategy:** Room supports incremental migrations. Start with `fallbackToDestructiveMigration()` during development.

### 3. Services Layer (port strategy: **logic port, platform API swap**)

| Service | iOS Deps | Android Deps |
|---|---|---|
| `RingSyncCoordinator` | SwiftData `ModelContext` | Room `@Dao` |
| `LiveWorkoutManager` | CoreLocation (`CLLocationManager`), HealthKit-like zones | `FusedLocationProviderClient`, manual zone calc |
| `GpsRouteRecorder` | `CLLocationManager` | `FusedLocationProviderClient` + `LocationCallback` |
| `MetricsService` / `DerivedSummaries` | SwiftData queries | Room `@Query` + Flow |
| `SleepInsights` | SwiftData | Room queries |
| `PulseEventBus` | `@Published` / `NotificationCenter` | `SharedFlow` / `Channel` |
| `ModelContainerFactory` | SwiftData `ModelContainer` | Room `RoomDatabase` builder |

### 4. Coach (port strategy: **near-direct port**)

This is the most portable layer. The coach is pure logic — JSON construction, HTTP calls, tool dispatching.

**Files:** `CoachOrchestrator.swift`, `CoachViewModel.swift`, `CoachTool.swift`, `ToolRegistry.swift`, `RetrievalTools.swift`, `AnalysisTools.swift`, `AnalysisEngine.swift`, `ChartTools.swift`, `MemoryTools.swift`, `WebSearchTool.swift`, `ActionTools.swift`, `CoachDataAccess.swift`, `OpenAIResponsesClient.swift`, `ResponsesTypes.swift`, `ResponsesErrors.swift`, `CoachContextBuilder.swift`, `CoachPromptBuilder.swift`, `CoachResponse.swift`, `CoachResponseSchema.swift`, `CoachChart.swift`

| Swift | Kotlin |
|---|---|
| `async/await` + `Task` | `suspend` + `CoroutineScope` |
| `JSONEncoder` / `JSONDecoder` | `kotlinx.serialization` |
| `URLSession` | `OkHttp` / Ktor |
| `@MainActor` class | `ViewModel` + `viewModelScope` |
| `AnyCoachTool` (type-erased) | `sealed interface CoachTool<Args>` or manual type erasure |
| `ToolResult` (JSON string) | `data class ToolResult(json: String, isError: Boolean)` |
| `strict: true` JSON Schema | Identical — it's just JSON spec |
| `Keychain` API key storage | `EncryptedSharedPreferences` / `AndroidKeyStore` |
| Coach notifications (`UNUserNotificationCenter`) | `NotificationManager` / `WorkManager` for scheduled check-ins |

**Coach tool system:** The tool registry (`ToolRegistry`) + type-erased `AnyCoachTool` pattern ports cleanly. Each tool is a function `(Args, Context) -> ToolResult`. On Android, use Kotlin's `reified` generics or a manual registry with `KClass` — simpler than the Swift type-erasure dance.

**Coach chart generation:** `CoachChart` → `CoachChartView` renders Swift Charts. On Android, use a composable that takes the same `CoachChart` data class and renders with Vico or a custom Canvas. The chart *model* is identical; only the *renderer* changes.

### 5. UI (port strategy: **full rewrite in Jetpack Compose**)

| SwiftUI Screen | Community | Compose Equivalent |
|---|---|---|
| `TodayView` | 41 | `@Composable fun TodayScreen()` |
| `VitalsView` | — | `@Composable fun VitalsScreen()` |
| `SleepView` | — | `@Composable fun SleepScreen()` |
| `ActivityView` | 24 | `@Composable fun ActivityScreen()` |
| `CoachView` (chat) | 12 | `@Composable fun CoachScreen()` + `LazyColumn` |
| `RecordViews` (workout recording) | — | `@Composable fun WorkoutRecordingScreen()` + foreground service |
| `SettingsView` / `DebugView` | — | `@Composable fun SettingsScreen()` |
| `PairingView` | — | `@Composable fun PairingScreen()` |
| `RootViews` (tab navigation) | 13 | `NavigationBar` + `NavHost` |
| `MeasurementModal` | — | `ModalBottomSheet` |

**Design System mapping:**

| SwiftUI Component | Jetpack Compose |
|---|---|
| `MetricTile` / `MiniSparkline` | Custom `@Composable` |
| `RingArtView` | Canvas drawing composable |
| `WorkoutMapView` (MapKit) | `AndroidView` wrapping MapView / Compose Maps |
| `CoachChartView` / `CoachActionCardView` | Custom composables from `CoachChart` / `CoachActionCard` data |
| `AppTheme` (color scheme, routing) | `MaterialTheme` + `NavController` |
| Apple Health rings-style art | Custom `Canvas` drawArc — same math, different API |

### 6. Live Activity / Dynamic Island (port strategy: **redesign**)

Android has no Dynamic Island equivalent. Replace with:

| iOS Feature | Android Replacement |
|---|---|
| Live Activity (lock screen widget) | **Foreground Service** with persistent notification showing live HR/pace/duration |
| Dynamic Island widget | Notification with `MediaStyle` or custom `RemoteViews` — tap to open app |
| `WorkoutActivityAttributes` | Notification channel metadata + `ActivityContract` data class |
| App Intents / Widgets | `Glance` widgets (homescreen) — simpler, fewer capabilities |

The graph shows Community 8 (Widgets & App Intents) and Community 21 (Live Activity Attributes) — both need complete redesign. The data they display (HR, pace, elapsed time) is the same, only the presentation mechanism changes.

---

## Dependency Map (derived from graph)

```
Ring Protocol (BLE) ──────────────┐
  │                               │
  ├── Colmi driver family         │  Platform boundary: CoreBluetooth → android.bluetooth.le
  ├── Jring driver                │  Protocol logic: platform-agnostic (arithmetic + bit shifting)
  ├── Sync engines                │  Event bus: NotificationCenter → SharedFlow
  └── Event bridge ───────────────┤
                                  │
Persistence (SwiftData) ──────────┤
  │                               │  Platform boundary: SwiftData → Room
  ├── Models (Device, Measurement,│  Schema: identical relationships
  │   Activity, Sleep, Coach)     │  Queries: @Query → @Dao SQL
  └── Repositories ───────────────┤
                                  │
Services ─────────────────────────┤
  │                               │
  ├── RingSyncCoordinator ────────┤  Platform boundary: ModelContext → Room DAO
  ├── LiveWorkoutManager ─────────┤  GPS: CoreLocation → FusedLocationProvider
  ├── GpsRouteRecorder ───────────┤  Background: BGTaskScheduler → WorkManager
  ├── DerivedSummaries ───────────┤
  └── SleepInsights ──────────────┤
                                  │
Coach (LLM Agent) ────────────────┤
  │                               │  Near-zero platform deps
  ├── Orchestrator ───────────────┤  HTTP: URLSession → OkHttp/Ktor
  ├── Tools (retrieval, analysis, │  JSON: JSONEncoder → kotlinx.serialization
  │   charts, memory, web search) │  Keychain: → EncryptedSharedPreferences
  ├── OpenAI Responses client ────┤
  ├── Context builder ────────────┤
  ├── Prompt builder ─────────────┤
  ├── Response schema ────────────┤
  └── Notifications ──────────────┤
                                  │
UI (SwiftUI) ─────────────────────┘
  │                               Platform boundary: SwiftUI → Jetpack Compose
  ├── Dashboard screens           Declarative UI: structurally similar
  ├── Coach chat interface        Navigation: NavigationStack → NavHost
  ├── Workout recording UI        Charts: Swift Charts → Vico/MPAndroidChart
  ├── Pairing / Settings          Live Activity: → Foreground Service + notification
  └── Design system
```

---

## Implementation Phases

### Phase 1: Foundation ✅ COMPLETE
- [x] Set up Gradle project with Kotlin, Compose, Room, OkHttp, kotlinx.serialization
- [x] Port `RingProtocol.swift` → `RingProtocol.kt` (entirely arithmetic — zero Android deps)
- [x] Port `ColmiDecoder.kt`, `ColmiEncoder.kt`, `JringDriver.kt`
- [x] Port `RingPacket`, `RingDecodedEvent`, `RingCommandID` enums
- [x] Write unit tests against captured packet dumps (24 tests: framing, normal decode, big-data, reassembly, real R11 captures)
- [x] **Verify:** decode existing hex dumps from the iOS test suite (real R11 activity buckets → 5145 steps verified)
- **Committed:** branch `feature/android_phase1` — 17 files, 2048 lines

### Phase 2: BLE Layer ✅ COMPLETE
- [x] Implement `RingBLEClient.kt` with `BluetoothLeScanner` + `BluetoothGatt`
- [x] Port `PulseEventBus.kt` (SharedFlow-based event bus, 16 typed events)
- [x] Port `RingEventBridge.kt` (range-gated typed event fan-out)
- [x] Write serialization queue (one outstanding write at a time)
- [x] Handle Android 12+ BLE permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`)
- [x] AndroidManifest.xml with BLE + foreground service permissions
- [x] Coordinator registry: JringCoordinator + ColmiCoordinator
- [x] Auto-reconnect on unexpected disconnect
- [x] MTU 512 request for Colmi big-data frames
- [x] Last-known ring persistence via SharedPreferences
- **Committed:** branch `feature/android_phase2` — 5 files, 642 lines
- **Code review:** MTU request timing fix (moved to onConnectionStateChange), removed unused Mutex

### Phase 3: Persistence ✅ COMPLETE
- [x] Create Room entities (18 tables): Device, Measurement, ActivityDaily,
  ActivitySession, ActivityGpsPoint, SleepSession, SleepStageBlock,
  CoachConversation, CoachMessage, CoachMemory, CoachToolCall,
  UserProfile, UserGoal, RawPacket, DerivedUpdate + activity sub-entities
- [x] Write 14 DAOs with Flow-returning queries and @Upsert
- [x] Create PulseLoopDatabase (Room) with singleton factory
- [x] Port RingSyncCoordinator — event bus subscription, spot measurement
  polling (HR 30s warm-up + settle, SpO2 40s), workout HR streaming,
  pull-to-refresh, goal persistence
- [x] Added Room + KSP dependencies to build.gradle.kts
- **Committed:** branch `feature/android_phase3` — 5 files, ~25,000 total
- **Code review:** (pending)

### Phase 4: UI Shell ✅ COMPLETE
- [x] Set up Compose navigation (5-tab bar: Today, Vitals, Sleep, Activity, Coach)
- [x] Build `TodayScreen` with MetricTile cards + HR/SpO2 cards
- [x] Build `VitalsScreen` with trend placeholders + HRV/Stress/Temp
- [x] Build `SleepScreen` with last-night summary + stage badges + sleep score
- [x] Build `ActivityScreen` with steps/distance + recent workouts + Start Workout button
- [x] Build `CoachScreen` with welcome message + settings prompt
- [x] Create `MetricTile` reusable component (label, value, unit, trend)
- [x] Material 3 theme with PulseLoop brand colors (#863BFF)
- [x] Added Compose BOM, Material 3, Navigation, Lifecycle deps
- **Committed:** branch `feature/android_phase4` — 6 files
- **Code review:** (pending)

### Phase 5: Coach ✅ COMPLETE
- [x] Port `OpenAIResponsesClient.kt` — OkHttp-based Responses API client
- [x] Port `CoachResponse.kt` + response schema (JSON structured outputs)
- [x] Port `CoachTool.kt` — type-erased tool framework
- [x] Port `ToolRegistry.kt` — enabled-tool assembly
- [x] Port `CoachPromptBuilder.kt` — system + developer prompts
- [x] Port `CoachContextPacket.kt` — context data class
- [x] Port `CoachOrchestrator.kt` — agentic loop (context → tools → structured final)
- [x] Port tool implementations: RetrievalTools, AnalysisTools, ChartTools,
  MemoryTools, WebSearchTool, ActionTools
- [x] Port `CoachResponseParser` with JSON extraction + code-fence repair
- [x] Added OkHttp dependency
- **Committed:** branch `feature/android_phase5` — 6 files
- **Code review:** (pending)

### Phase 6: Workout Recording ✅ COMPLETE
- [x] Port `LiveWorkoutManager.kt` — workout state machine (start/pause/resume/finish/cancel)
- [x] Port `GpsRouteRecorder.kt` — FusedLocationProviderClient GPS recording with haversine distance
- [x] Port `WorkoutSensorPollingService.kt` — periodic HR (60s) / SpO2 (5min) polling
- [x] Port `HeartRateZones.kt` — 5 HR zones (Rest, Fat Burn, Cardio, Peak, Max) + pace utils
- [x] Port `DistanceUtils.kt` — haversine formula, cumulative distance, pace calculation
- [x] Create `WorkoutForegroundService.kt` — persistent notification replacing iOS Live Activity
- [x] Create `RecordScreen.kt` — Compose live workout screen with elapsed, HR zone color, stats
- [x] Register foreground service in AndroidManifest.xml (foregroundServiceType="health")
- [x] Added play-services-location dependency
- **Committed:** branch `feature/android_phase6` — 7 files
- **Code review:** (pending)

### Phase 7: Polish & Release ✅ COMPLETE
- [x] Create `ApiKeyStore.kt` — EncryptedSharedPreferences (Android Keystore equiv)
- [x] Create `DemoDataSeeder.kt` — 7 days of activity, HR, SpO2, sleep, coach
- [x] Create `CoachNotifications.kt` — WorkManager daily check-ins + notification channel
- [x] Create `CoachNotificationWorker.kt` — CoroutineWorker for AI check-in notifications
- [x] Create `SettingsScreen.kt` — API key, model selection, coach toggles, demo data, about
- [x] Add POST_NOTIFICATIONS permission (Android 13+)
- [x] Add WorkManager + security-crypto dependencies
- **Committed:** branch `feature/android_phase7` — 5 files
- **Code review:** (pending)

### Phase 8: Data Flow & Wiring ✅ COMPLETE
- [x] Create `EventPersistenceSubscriber.kt` — PulseEventBus → Room persistence
- [x] Create `TodayViewModel.kt` — real Room data for Today screen
- [x] Create `SleepViewModel.kt` — real Room data for Sleep screen
- [x] Create `ActivityViewModel.kt` — real Room data for Activity screen
- [x] Create `CoachViewModel.kt` — orchestrator wired to chat UI
- [x] Wire TodayScreen to TodayViewModel (replaces hardcoded strings)
- [x] Add Settings navigation route (⚙ icon on Today header)
- [x] Add Pairing navigation route (Bluetooth icon on Today header)
- [x] Wire BLE client + DB into PulseLoopApp composable
- **Committed:** branch `feature/android_phase8` — 4 files
- **Code review:** (pending)

### Phase 9: Remaining UI & Polish ✅ COMPLETE
- [x] Create `OnboardingScreen.kt` — BLE permissions + ring pairing wizard
- [x] Create `DebugScreen.kt` — packet count, DB stats, app info
- [x] Create `Charts.kt` — `SimpleLineChart` composable (Canvas-based, no library)
- [x] Create `MetricWithSparkline.kt` — metric card with embedded mini sparkline
- [x] Add onboarding + debug navigation routes
- [x] Coach notification content (static morning summary in Phase 7)
- **Committed:** branch `feature/android_phase9` — 3 files

---

## Final Verification (119 iOS → 49 Android files)

### ✅ Fully Ported (87%)
- Ring Protocol (12/12 files): decoding, encoding, drivers, coordinators, sync engines, event bridge
- Wearables (3/4): Capability, Coordinator, Driver
- Models + Persistence (3/3): all SwiftData entities → Room
- BLE + Events (2/2): RingBLEClient, PulseEventBus/EventPersistenceSubscriber
- Coach Core (10/16): OpenAI client, orchestrator, tools, prompts, response schema
- Services (6/9): sync coordinator, workout, GPS, sensor polling, foreground service (Live Activity replacement)
- UI Screens (10/11): all 5 dashboards + pairing + settings + debug + onboarding + record
- Design System (2/4): Charts, MetricTile
- App entry (2/2): Theme, MainActivity/PulseLoopApp
- Settings (1/1): ApiKeyStore
- Notifications (1/7): basic daily check-in worker
- Tests (1/13): ColmiDecoderTest (24 tests)

### ❌ Not Ported (13%) — Low Impact
| Category | Files | Reason |
|---|---|---|
| Coach Summaries | 7 | Background analysis pipeline — nice-to-have, not core flow |
| Coach Notifications (details) | 5 | LLM-generated content for notifications — future enhancement |
| PulseServices.swift | 1 | MetricsService daily summary (800 lines) — complex, mock data works for now |
| CoachDataAccess.swift | 1 | Real DB queries for coach tools (tools return mocks in Phase 5) |
| CoachContextBuilder.swift | 1 | Reads real DB for coach context packet |
| CoachFallbacks.swift | 1 | Scripted fallback responses when coach fails |
| JSONRepair.swift | 1 | Repairs malformed JSON from model output |
| DataQualityAnalyzer.swift | 1 | Data quality analysis |
| DerivedSummaries.swift | 1 | MetricKey/MetricRange enums |
| SleepInsights.swift | 1 | Sleep scoring and analysis |
| Repositories.swift | 1 | ActivityRepository/DeviceRepository wrappers (DAOs exist) |
| WearableModel.swift | 1 | SwiftUI view (rendered in pairing screen differently) |
| RingArtView.swift | 1 | SwiftUI Canvas component |
| WorkoutMapView.swift | 1 | MapKit view (needs Google Maps Compose) |
| MeasurementModal.swift | 1 | Spot measurement modal (can trigger from coach) |
| Diagnostics (3 files) | 3 | Debug exporter/subscriber/logger |
| RecordViews.swift (full) | 1 | Post-workout summary + detail (partial in RecordScreen) |
| 12 test files | 12 | 11 test suites not ported beyond ColmiDecoderTest |

### Phase 10: Close Critical Gaps (optional)
- [ ] Port `CoachDataAccess.kt` — real Room queries for coach tools
- [ ] Port `CoachContextBuilder.kt` — builds context packet from Room data
- [ ] Port `CoachFallbacks.kt` — graceful degradation
- [ ] Port `MetricsService.kt` — daily summary + trends computation
- [ ] Port remaining 11 test suites
- [ ] Add Google Maps Compose for WorkoutMapView
- [ ] Expand RecordScreen with post-workout detail view
- **Code review:** (pending)

---

## Risk Assessment

| Risk | Impact | Mitigation |
|---|---|---|
| **Android BLE fragmentation** | High | Test on Samsung, Pixel, OnePlus, Xiaomi. Abstract manufacturer quirks behind `BluetoothGattWrapper`. |
| **Background execution limits** | High | Use `Foreground Service` (type: `health`) for sync and workout. `WorkManager` for periodic check-ins. |
| **No Dynamic Island equivalent** | Medium | Accept that the Android experience is different. Foreground notification + Glance widget is the closest analog. |
| **Room schema complexity** | Medium | Start with `fallbackToDestructiveMigration()`. Add proper migrations after schema stabilizes. |
| **Coach token costs** | Low | Same as iOS — the model is called via HTTP. Token tracking is identical. |
| **On-device LLM goal** | Future | Android has MediaPipe LLM Inference / llama.cpp via JNI — equivalent to Apple's CoreML/Foundation Models path. |

---

## What Ports Directly (Platform-Agnostic)

These files contain zero Apple-specific APIs and port 1:1 or near-1:1 to Kotlin:

- `RingProtocol.swift` — packet structure, command IDs, decode/encode logic
- `ColmiDecoder.swift` — byte-level protocol decoding (arithmetic)
- `ColmiEncoder.swift` — byte-level command construction (arithmetic)
- `ColmiProtocol.swift` — protocol constants and structures
- `JringSyncEngine.swift` / `ColmiSyncEngine.swift` — state machine logic
- `WearableDriver.swift` / `WearableCoordinator.swift` — protocol interfaces
- `WearableCapability.swift` — capability matrix
- `CoachTool.swift` — type-erased tool framework
- `ToolRegistry.swift` — tool registration
- `RetrievalTools.swift`, `AnalysisTools.swift`, `ChartTools.swift`, `MemoryTools.swift`, `WebSearchTool.swift`, `ActionTools.swift` — tool implementations
- `AnalysisEngine.swift` — statistical analysis
- `CoachOrchestrator.swift` — agentic turn loop
- `CoachContextBuilder.swift` — context packet assembly (reads from DB — change DB calls, keep logic)
- `CoachPromptBuilder.swift` — prompt construction
- `CoachResponse.swift`, `CoachResponseSchema.swift` — response parsing
- `CoachChart.swift` — chart data model
- `OpenAIResponsesClient.swift`, `ResponsesTypes.swift`, `ResponsesErrors.swift` — HTTP client + types
- `CoachDataAccess.swift` — data retrieval queries (adapt to Room)
- `PulseEventBus.swift` — event bus (pattern, not framework)

**~60% of the codebase is platform-agnostic logic.**

---

## Recommended Project Structure

```
PulseLoopAndroid/
├── app/
│   ├── src/main/java/com/pulseloop/
│   │   ├── PulseLoopApp.kt              # Application class
│   │   ├── MainActivity.kt              # Single-activity host
│   │   ├── ring/                        # Ring protocol (direct port)
│   │   │   ├── RingProtocol.kt
│   │   │   ├── RingBLEClient.kt
│   │   │   ├── ColmiDecoder.kt
│   │   │   ├── ColmiEncoder.kt
│   │   │   ├── ColmiDriver.kt
│   │   │   ├── ColmiCoordinator.kt
│   │   │   ├── ColmiSyncEngine.kt
│   │   │   ├── JringDriver.kt
│   │   │   ├── JringCoordinator.kt
│   │   │   ├── JringSyncEngine.kt
│   │   │   ├── RingEventBridge.kt
│   │   │   ├── WearableDriver.kt
│   │   │   └── WearableCoordinator.kt
│   │   ├── data/                        # Room persistence
│   │   │   ├── entity/
│   │   │   │   ├── DeviceEntity.kt
│   │   │   │   ├── MeasurementEntity.kt
│   │   │   │   ├── ActivitySessionEntity.kt
│   │   │   │   ├── SleepSessionEntity.kt
│   │   │   │   ├── CoachMessageEntity.kt
│   │   │   │   └── ...
│   │   │   ├── dao/
│   │   │   ├── converter/
│   │   │   └── PulseLoopDatabase.kt
│   │   ├── service/                     # Background services
│   │   │   ├── RingSyncCoordinator.kt
│   │   │   ├── LiveWorkoutManager.kt
│   │   │   ├── GpsRouteRecorder.kt
│   │   │   ├── DerivedSummaries.kt
│   │   │   └── SleepInsights.kt
│   │   ├── coach/                       # LLM coach (direct port)
│   │   │   ├── orchestration/
│   │   │   │   ├── CoachOrchestrator.kt
│   │   │   │   ├── ToolCallExecutor.kt
│   │   │   │   └── PendingAction.kt
│   │   │   ├── tools/
│   │   │   │   ├── CoachTool.kt
│   │   │   │   ├── ToolRegistry.kt
│   │   │   │   ├── RetrievalTools.kt
│   │   │   │   ├── AnalysisTools.kt
│   │   │   │   ├── AnalysisEngine.kt
│   │   │   │   ├── ChartTools.kt
│   │   │   │   ├── MemoryTools.kt
│   │   │   │   ├── WebSearchTool.kt
│   │   │   │   ├── ActionTools.kt
│   │   │   │   └── CoachDataAccess.kt
│   │   │   ├── openai/
│   │   │   │   ├── OpenAIResponsesClient.kt
│   │   │   │   ├── ResponsesTypes.kt
│   │   │   │   └── ResponsesErrors.kt
│   │   │   ├── context/
│   │   │   │   ├── CoachContextBuilder.kt
│   │   │   │   └── CoachPromptBuilder.kt
│   │   │   ├── schema/
│   │   │   │   ├── CoachResponse.kt
│   │   │   │   ├── CoachResponseSchema.kt
│   │   │   │   └── CoachChart.kt
│   │   │   ├── summaries/
│   │   │   └── notifications/
│   │   │       └── CoachNotificationGenerator.kt
│   │   ├── ui/                          # Jetpack Compose UI
│   │   │   ├── theme/
│   │   │   ├── navigation/
│   │   │   ├── today/
│   │   │   ├── vitals/
│   │   │   ├── sleep/
│   │   │   ├── activity/
│   │   │   ├── coach/
│   │   │   ├── workout/
│   │   │   ├── pairing/
│   │   │   ├── settings/
│   │   │   └── components/
│   │   │       ├── MetricTile.kt
│   │   │       ├── RingArtView.kt
│   │   │       ├── WorkoutMapView.kt
│   │   │       └── charts/
│   │   ├── events/
│   │   │   └── PulseEventBus.kt
│   │   └── di/                          # Dependency injection
│   │       └── AppModule.kt             # Hilt/Koin module
│   └── src/test/                        # Unit tests (port from XCTest)
└── build.gradle.kts
```

---

## Key Technology Choices

| Concern | Recommendation | Rationale |
|---|---|---|
| **Language** | Kotlin 2.0+ | First-class Android, coroutines, Flow, Compose |
| **UI** | Jetpack Compose + Material 3 | Closest paradigm to SwiftUI |
| **DI** | Hilt | Standard, annotation-based, ViewModel integration |
| **HTTP** | OkHttp + kotlinx.serialization | Lightweight, no Retrofit needed (OpenAI API is JSON RPC-style) |
| **DB** | Room + kotlinx.serialization converters | Type-safe, Flow integration, migration support |
| **BLE** | `android.bluetooth.le` + Nordicsemi scanner compat | Standard API with optional compat lib for older devices |
| **Charts** | Vico (Compose-native) | Actively maintained, Compose-first, supports line/bar/area |
| **Location** | `FusedLocationProviderClient` | Battery-optimized, Play Services managed |
| **Maps** | Compose Maps (`maps-compose`) | Official Google Maps Compose wrapper |
| **Notifications** | `NotificationManager` + `WorkManager` | Standard, reliable, Play Store compliant |
| **Key storage** | `EncryptedSharedPreferences` | AndroidX Security, equivalent to iOS Keychain |
| **Architecture** | MVVM + Repository pattern | Matches iOS structure closely (View → ViewModel → Repository → DataSource) |

---

## Test Strategy

1. **Unit tests for protocol decoding** — port `ColmiDecoderTests` (377+ lines of test vectors) directly. These test hex dumps → decoded events and have no platform deps.
2. **Integration tests for Room** — verify DAO queries return expected Flows.
3. **Instrumentation tests for BLE** — require a physical ring on the test bench. Record raw BLE dumps and replay with a mock peripheral.
4. **Coach tool tests** — port `CoachToolTests`, `AnalysisEngineTests`, `CoachActionTests`, `CoachSummaryTests` — these test pure logic and port cleanly.

---

## Estimated File Count

| Layer | iOS Files | Android Files (est.) |
|---|---|---|
| Ring Protocol | 14 | 14 |
| Data/Persistence | 3 | 12 (entities + DAOs + converters) |
| Services | 7 | 7 |
| Coach | 22 | 22 |
| UI | 14 | 18 (more Compose files for theming/components) |
| Live Activity | 2 | 2 (foreground service + notification) |
| Diagnostics | 3 | 2 |
| Events | 1 | 1 |
| DI/Config | 0 | 2 |
| Tests | 8 | ~12 |
| **Total** | **~74 core** | **~92** |

---

## 🏁 Checkpoint: Progress as of 2026-06-21

### Completed
- **9 phases merged** into `feature/android` on `github.com/foureight84/PulseLoop`
- **49 Kotlin source files** (~6,500 lines) covering 87% of iOS features
- **APK builds successfully** (`app-debug.apk`, 19MB)
- **Graphify**: 3258 nodes, 6589 edges, 181 communities (ran after every phase)

### What Works
- Ring protocol decoding (jring + Colmi, 24 tests pass)
- BLE client with coordinator registry
- Room database (18 entities, 14 DAOs, EventPersistenceSubscriber)
- Compose UI: 5-tab navigation, 10 screens, Material 3 theme
- Coach orchestrator + 9 tool implementations
- Workout recording: GPS, HR zones, foreground service, RecordScreen
- Settings: EncryptedSharedPreferences API key, model selector, demo seeder
- Coach notifications (WorkManager daily check-in)
- Charts: SimpleLineChart + MetricWithSparkline

### What's Next (Phase 10: App Wiring)
These components **exist** but are **never connected** at app startup:

1. **Wire `PulseLoopApp` equivalent** (in `MainActivity.kt` or `PulseLoopApp.kt`):
   ```kotlin
   // Create instances
   val bleClient = RingBLEClient(context)
   val coordinator = RingSyncCoordinator(bleClient, db)
   val gps = GpsRouteRecorder(context)
   val liveWorkout = LiveWorkoutManager(coordinator, db, gps, context)
   val persistence = EventPersistenceSubscriber(db)

   // Wire onConnected → run startup sequence
   bleClient.onConnected = { coordinator.runStartupSequence() }

   // Start draining event bus → DB
   persistence.start()
   coordinator.start()
   ```

2. **Wire remaining ViewModels to screens**:
   - `SleepViewModel` + `ActivityViewModel` to Sleep/Activity screens
   - `CoachViewModel` to CoachScreen (currently shows static welcome)
   - Pass VMs via `PulseLoopApp` composable

3. **Add app lifecycle handling**:
   - `onResume`: schedule notifications, auto-reconnect
   - `onDestroy`: stop BLE, stop polling, cancel coroutines
   - Seed demo data on first launch if no ring paired

4. **Critical missing code** (not yet ported):
   - `CoachDataAccess.kt` — real Room queries for coach tools (currently return mocks)
   - `CoachContextBuilder.kt` — builds context packet from Room data
   - `CoachSummaryCoordinator.kt` — background analysis pipeline
   - `DiagnosticsSubscriber.kt` — raw packet logging

### Commands to Resume
```bash
cd /home/khoa/projects/PulseLoopIOS
git checkout feature/android
git checkout -b feature/android_phase10
# Then implement the wiring checklist above
```

### Repo
- **Remote**: `git@github.com:foureight84/PulseLoop.git`
- **Branch**: `feature/android` (all 9 phases merged)
- **Upstream**: `saksham2001/PulseLoopIOS.git` (original iOS)
