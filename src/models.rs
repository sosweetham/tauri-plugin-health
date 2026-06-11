use serde::{Deserialize, Serialize};

/// A readable health metric. Serialized camelCase to match the TS union.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Metric {
    Steps,
    Distance,
    ActiveCalories,
    TotalCalories,
    HeartRate,
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
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Bucket {
    Day,
    Hour,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum HealthPlatform {
    Ios,
    Android,
    Unsupported,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Availability {
    pub available: bool,
    pub platform: HealthPlatform,
    /// Android: "providerUpdateRequired" | "providerUnavailable";
    /// iOS: "healthDataUnavailable"; desktop: explanation string.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPermissionsOptions {
    pub read: Vec<Metric>,
}

/// Whether `granted` reflects real per-metric grants.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PermissionAccuracy {
    /// Android: the actual granted set from Health Connect.
    Exact,
    /// iOS: HealthKit hides read grants by design; `granted` is best-effort.
    Unknown,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionsResponse {
    pub granted: Vec<Metric>,
    pub state: PermissionAccuracy,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryAggregatedOptions {
    /// Must be an aggregatable metric (not `workouts` / `sleep`).
    pub metric: Metric,
    /// Epoch milliseconds, inclusive start.
    pub start: i64,
    /// Epoch milliseconds, exclusive end.
    pub end: i64,
    pub bucket: Bucket,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AggregatedBucket {
    pub start: i64,
    pub end: i64,
    /// steps: count; distance: meters; calories: kcal; heartRate: avg bpm.
    pub value: f64,
    /// "count" | "m" | "kcal" | "bpm"
    pub unit: String,
    /// heartRate only.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub min: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub max: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryAggregatedResponse {
    pub buckets: Vec<AggregatedBucket>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryRangeOptions {
    pub start: i64,
    pub end: i64,
}

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

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SleepStageSample {
    pub stage: SleepStage,
    pub start: i64,
    pub end: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SleepSession {
    pub start: i64,
    pub end: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
    pub stages: Vec<SleepStageSample>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuerySleepResponse {
    pub sessions: Vec<SleepSession>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Workout {
    pub start: i64,
    pub end: i64,
    /// Mapped common name ("running", "cycling", …) or "other".
    pub activity_type: String,
    /// Platform-native enum value (HKWorkoutActivityType rawValue /
    /// ExerciseSessionRecord.EXERCISE_TYPE_*), for exact-type needs.
    pub raw_activity_type: i64,
    pub duration_sec: f64,
    /// kcal
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub calories: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub distance_meters: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryWorkoutsResponse {
    pub workouts: Vec<Workout>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryHeartRateSamplesOptions {
    pub start: i64,
    pub end: i64,
    /// Max samples returned (most recent kept). Default 1000.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub limit: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HeartRateSample {
    pub timestamp: i64,
    pub bpm: f64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub source: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueryHeartRateSamplesResponse {
    pub samples: Vec<HeartRateSample>,
}
