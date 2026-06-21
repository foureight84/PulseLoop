# PulseLoop → Android Porting Plan

> Generated from graph analysis of `/home/khoa/projects/PulseLoopIOS`  
> 3209 nodes · 6518 edges · 176 communities · 126 Swift + 45 Kotlin files

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
