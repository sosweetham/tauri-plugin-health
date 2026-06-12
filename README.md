# tauri-plugin-health

Read-only health & fitness data for [Tauri 2](https://tauri.app) apps:

- **iOS** — Apple **HealthKit**
- **Android** — **Health Connect** (`androidx.health.connect`), Google Fit's
  replacement. Google Fit's APIs shut down at the end of 2026 and have been
  closed to new apps since 2024 — Health Connect is built into Android 14+
  and available as a Play app on Android 9–13.
- **Desktop** — graceful stubs: `isAvailable()` resolves
  `{ available: false, platform: 'unsupported' }`, queries reject.

v1 metrics (read-only): **steps, distance, active/total calories, heart
rate, resting heart rate, heart-rate variability, workouts, sleep with
stages**.

> **HRV platform contract.** iOS reads **SDNN**
> (`heartRateVariabilitySDNN`), Android reads **RMSSD**
> (`HeartRateVariabilityRmssdRecord`) — both in `ms` but **not comparable
> across platforms or methods**. Treat HRV as baseline-relative only:
> compare a value against the same user's rolling baseline on the same
> device. Health Connect has no native HRV aggregate, so on Android
> `queryAggregated` reads the raw records and buckets them itself (same
> day/hour semantics).

## Install

```toml
# src-tauri/Cargo.toml
[dependencies]
tauri-plugin-health = { path = "..." }
```

```rust
// src-tauri/src/lib.rs
tauri::Builder::default()
    .plugin(tauri_plugin_health::init())
```

```json
// src-tauri/capabilities/default.json
{ "permissions": ["health:default"] }
```

```bash
pnpm add tauri-plugin-health-api
```

## Consumer app setup (required)

### iOS

After `tauri ios init`:

1. `src-tauri/gen/apple/<app>_iOS/<app>_iOS.entitlements`:
   ```xml
   <key>com.apple.developer.healthkit</key>
   <true/>
   ```
2. `src-tauri/gen/apple/<app>_iOS/Info.plist`:
   ```xml
   <key>NSHealthShareUsageDescription</key>
   <string>Reads your activity and sleep to plan your day.</string>
   ```
3. Enable the HealthKit capability for your App ID if signing complains.

The iOS **simulator** supports HealthKit but starts empty — add samples
manually in the Health app (Browse → metric → Add Data).

### Android

1. **`tauri.conf.json`**: Health Connect requires API 26+ (Tauri defaults
   to 24):
   ```json
   "bundle": { "android": { "minSdkVersion": 26 } }
   ```
2. Permissions, provider visibility, and the mandatory permissions-rationale
   activity merge in automatically from the plugin. Optionally point the
   rationale screen at your privacy policy in
   `src-tauri/gen/android/app/src/main/AndroidManifest.xml`:
   ```xml
   <meta-data
     android:name="app.tauri.health.PRIVACY_POLICY_URL"
     android:value="https://example.com/privacy" />
   ```
   The rationale title/body can be overridden by redefining the
   `tauri_health_rationale_title` / `tauri_health_rationale_text` string
   resources in your app.
3. If you only use a subset of metrics, strip the unused health permissions
   (Google Play policy requires declaring only what you use):
   ```xml
   <uses-permission
     android:name="android.permission.health.READ_HEART_RATE"
     tools:node="remove" />
   ```
   Production health apps also need the Play Console health declaration.

## Usage

```ts
import {
  isAvailable, requestPermissions, queryAggregated,
  querySleep, queryWorkouts, queryHeartRateSamples, openSettings,
} from 'tauri-plugin-health-api';

const { available, platform, reason } = await isAvailable();

const { granted, state } = await requestPermissions({
  read: ['steps', 'sleep', 'heartRate', 'workouts'],
});

// Daily steps for the past week:
const dayMs = 86_400_000;
const { buckets } = await queryAggregated({
  metric: 'steps',
  start: Date.now() - 7 * dayMs,
  end: Date.now(),
  bucket: 'day',
});

// Last night's sleep with stages:
const { sessions } = await querySleep({
  start: Date.now() - dayMs,
  end: Date.now(),
});
for (const session of sessions) {
  for (const stage of session.stages) {
    console.log(stage.stage, new Date(stage.start), new Date(stage.end));
  }
}
```

All timestamps are **epoch milliseconds**. Units: steps `count`, distance
`m`, calories `kcal`, heart rate / resting heart rate `bpm`, heart-rate
variability `ms`.

## Platform contract — read these

- **iOS cannot report read-permission grants.** HealthKit hides read
  denials by design: `requestPermissions`/`checkPermissions` return
  `state: 'unknown'` on iOS and a denied metric just returns empty data.
  Treat "no data" as possibly-denied and offer `openSettings()`. Android
  returns the real grant set (`state: 'exact'`).
- **Health Connect history window**: by default an app can only read data
  from up to 30 days before its **first** permission grant. (Newer Health
  Connect versions add `READ_HEALTH_DATA_HISTORY` to lift this — not
  exposed in v1.)
- **Sleep sessions on iOS are reconstructed** (HealthKit has no session
  object): samples are grouped per source and split on >60-minute gaps,
  which may differ slightly from the Health app's own grouping. Android
  returns Health Connect's native sessions.
- **`totalCalories` on iOS** = active + basal energy summed; basal data is
  sparse without an Apple Watch.
- **Workout `activityType`** is a curated cross-platform union (~20 names,
  `'other'` fallback); the platform-native enum value is always present as
  `rawActivityType`.
- Heart-rate aggregates are integer bpm on Android, fractional on iOS.
- **HRV is method-specific**: SDNN on iOS vs RMSSD on Android (see the
  banner up top) — baseline-relative comparisons only, never
  cross-platform. Android HRV aggregates are computed by the plugin from
  raw records (Health Connect has no HRV aggregate).

## Wire types are generated — single source of truth

Every type that crosses a language boundary (TypeScript ⇄ Rust ⇄
Kotlin/Swift) is defined once, in `src/models.rs`, and generated into the
other three languages with [typeshare]:

| Output | Consumed by |
| --- | --- |
| `guest-js/bindings.ts` | the published JS API (`guest-js/index.ts` re-exports it) |
| `ios/Sources/HealthTypes.generated.swift` | Swift plugin (`Codable`, resolved via Tauri's `Encodable` overload) |
| `android/src/main/java/HealthTypes.generated.kt` | Kotlin plugin (serialized through `WireJson`, which maps the generated enums' wire names onto Tauri's Jackson bridge) |

To change or add a wire type:

```bash
cargo install typeshare-cli   # once (or brew install typeshare)
# edit src/models.rs, then:
pnpm generate-types
```

Never edit the generated files by hand. Vocabulary fields are real enums
(`Metric`, `SleepStage`, `ActivityType`, `HealthUnit`, …), so adding a
variant in `models.rs` surfaces as a compile error in the Kotlin/Swift
`when`/`switch` mappings until both platforms handle it.

[typeshare]: https://github.com/1Password/typeshare

## Example app

`examples/tauri-app` — Svelte 5 demo with availability banner, permission
flow, Today/7d/30d presets, bar charts per metric, a sleep-stage timeline,
and a workouts list.

```bash
cd examples/tauri-app
pnpm install
pnpm tauri dev                       # desktop: validates the unsupported path
pnpm tauri ios init                  # then apply the iOS setup above
pnpm tauri ios dev "iPhone 17 Pro"   # boot the simulator first
pnpm tauri android init
pnpm tauri android dev               # use an API 34+ emulator (HC built in)
```
