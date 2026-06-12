//! Wire types for the plugin — the single source of truth for every shape
//! that crosses a language boundary (Kotlin/Swift → Rust → TypeScript).
//!
//! The TypeScript, Swift and Kotlin copies of these types are GENERATED with
//! typeshare — do not edit them by hand. After changing anything here, run:
//!
//! ```sh
//! pnpm generate-types
//! ```
//!
//! Doc comments on `#[typeshare]` items are carried into the generated code,
//! so consumer-facing documentation lives here too.

use serde::{Deserialize, Serialize};
use typeshare::typeshare;

/// A readable health metric.
///
/// `heartRateVariability` is method-specific per platform — SDNN on iOS,
/// RMSSD on Android (both in ms). The two are NOT comparable; only compare
/// HRV against the same user's own baseline on the same device.
#[typeshare(swift = "CaseIterable")]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Metric {
    Steps,
    Distance,
    ActiveCalories,
    TotalCalories,
    HeartRate,
    RestingHeartRate,
    HeartRateVariability,
    Workouts,
    Sleep,
}

impl Metric {
    /// Metrics usable with `query_aggregated`.
    pub fn is_aggregatable(self) -> bool {
        !matches!(self, Metric::Workouts | Metric::Sleep)
    }
}

/// Bucket size for aggregated queries (device-local calendar alignment).
#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Bucket {
    Day,
    Hour,
}

/// Unit of an aggregated value.
#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum HealthUnit {
    Count,
    /// Meters.
    M,
    Kcal,
    Bpm,
    /// Milliseconds (HRV).
    Ms,
}

#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum HealthPlatform {
    Ios,
    Android,
    Unsupported,
}

/// Why health data is unavailable on this device.
#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum AvailabilityReason {
    /// Android: the Health Connect app needs an update — offer `openSettings`.
    ProviderUpdateRequired,
    /// Android: the device has no Health Connect provider.
    ProviderUnavailable,
    /// iOS: HealthKit reports no health data (e.g. old iPads).
    HealthDataUnavailable,
    /// Desktop: health data is only available on iOS and Android.
    UnsupportedPlatform,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Availability {
    pub available: bool,
    pub platform: HealthPlatform,
    /// Present when `available` is false.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<AvailabilityReason>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPermissionsOptions {
    pub read: Vec<Metric>,
}

/// Whether `granted` reflects real per-metric grants.
#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PermissionAccuracy {
    /// Android: the actual granted set from Health Connect.
    Exact,
    /// iOS: HealthKit hides read grants by design; `granted` echoes the
    /// requested metrics and denied ones silently return empty data. Treat
    /// "no data" as possibly-denied and surface `openSettings`.
    Unknown,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionsResponse {
    pub granted: Vec<Metric>,
    pub state: PermissionAccuracy,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryAggregatedOptions {
    /// Must be an aggregatable metric (not `workouts` / `sleep`).
    pub metric: Metric,
    /// Epoch milliseconds, inclusive start.
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    /// Epoch milliseconds, exclusive end.
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
    pub bucket: Bucket,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AggregatedBucket {
    /// Epoch ms, device-local bucket bounds.
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
    /// steps: count; distance: meters; calories: kcal; heartRate /
    /// restingHeartRate: avg bpm; heartRateVariability: avg ms (SDNN on
    /// iOS, RMSSD on Android — baseline-relative comparisons only).
    pub value: f64,
    pub unit: HealthUnit,
    /// Heart metrics only (heartRate / restingHeartRate /
    /// heartRateVariability).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub min: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub max: Option<f64>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryAggregatedResponse {
    pub buckets: Vec<AggregatedBucket>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryRangeOptions {
    /// Epoch milliseconds, inclusive start.
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    /// Epoch milliseconds, exclusive end.
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
}

#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SleepStage {
    Awake,
    Light,
    Deep,
    Rem,
    InBed,
    /// Platform reported sleep without a stage.
    Asleep,
    OutOfBed,
    Unknown,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SleepStageSample {
    pub stage: SleepStage,
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SleepSession {
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
    /// Recording app/device name (iOS) or package (Android).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
    pub stages: Vec<SleepStageSample>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuerySleepResponse {
    pub sessions: Vec<SleepSession>,
}

/// Cross-platform workout activity name. Platform exercise types without a
/// common mapping report `other`; the platform-native value is always
/// available in `Workout::raw_activity_type`.
#[typeshare]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum ActivityType {
    Running,
    Walking,
    Cycling,
    Swimming,
    Strength,
    Hiit,
    Yoga,
    Hiking,
    Elliptical,
    Rowing,
    Tennis,
    Basketball,
    Soccer,
    Dance,
    Pilates,
    Stairs,
    Golf,
    Core,
    /// iOS only — HealthKit `.crossTraining` has no Health Connect analog.
    CrossTraining,
    Other,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Workout {
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
    pub activity_type: ActivityType,
    /// Platform-native enum value (HKWorkoutActivityType rawValue on iOS,
    /// ExerciseSessionRecord.EXERCISE_TYPE_* on Android), for exact-type
    /// needs.
    #[typeshare(serialized_as = "I54")]
    pub raw_activity_type: i64,
    pub duration_sec: f64,
    /// kcal
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub calories: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub distance_meters: Option<f64>,
    /// Recording app/device name (iOS) or package (Android).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryWorkoutsResponse {
    pub workouts: Vec<Workout>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryHeartRateSamplesOptions {
    /// Epoch milliseconds, inclusive start.
    #[typeshare(serialized_as = "I54")]
    pub start: i64,
    /// Epoch milliseconds, exclusive end.
    #[typeshare(serialized_as = "I54")]
    pub end: i64,
    /// Max samples returned (most recent kept). Default 1000.
    // I54 keeps the Kotlin type a plain Long — jackson-module-kotlin cannot
    // construct data classes with value-class (UInt) constructor params.
    #[typeshare(serialized_as = "I54")]
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HeartRateSample {
    #[typeshare(serialized_as = "I54")]
    pub timestamp: i64,
    pub bpm: f64,
    /// Recording app/device name (iOS) or package (Android).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
}

#[typeshare]
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryHeartRateSamplesResponse {
    pub samples: Vec<HeartRateSample>,
}
