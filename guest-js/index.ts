import { invoke } from '@tauri-apps/api/core';

/**
 * A readable health metric.
 *
 * `heartRateVariability` is method-specific per platform — SDNN on iOS,
 * RMSSD on Android (both in ms). The two are NOT comparable; only compare
 * HRV against the same user's own baseline on the same device.
 */
export type Metric =
  | 'steps'
  | 'distance'
  | 'activeCalories'
  | 'totalCalories'
  | 'heartRate'
  | 'restingHeartRate'
  | 'heartRateVariability'
  | 'workouts'
  | 'sleep';

/** Metrics usable with {@link queryAggregated}. */
export type AggregatableMetric = Exclude<Metric, 'workouts' | 'sleep'>;

/** Bucket size for aggregated queries (device-local calendar alignment). */
export type Bucket = 'day' | 'hour';

export type SleepStage =
  | 'awake'
  | 'light'
  | 'deep'
  | 'rem'
  | 'inBed'
  /** Platform reported sleep without a stage. */
  | 'asleep'
  | 'outOfBed'
  | 'unknown';

export interface Availability {
  available: boolean;
  platform: 'ios' | 'android' | 'unsupported';
  /**
   * Android: `providerUpdateRequired` (Health Connect app needs an update —
   * offer {@link openSettings}) or `providerUnavailable` (device has no
   * Health Connect). iOS: `healthDataUnavailable` (e.g. old iPads).
   */
  reason?: string;
}

export interface PermissionsResponse {
  granted: Metric[];
  /**
   * `exact` on Android — the real grant set from Health Connect.
   * `unknown` on iOS — HealthKit hides read denials by design; `granted`
   * echoes the requested metrics and denied ones silently return empty
   * data. Treat "no data" as possibly-denied and surface
   * {@link openSettings}.
   */
  state: 'exact' | 'unknown';
}

export interface AggregatedBucket {
  /** Epoch ms, device-local bucket bounds. */
  start: number;
  end: number;
  /**
   * steps: count; distance: meters; calories: kcal; heartRate /
   * restingHeartRate: avg bpm; heartRateVariability: avg ms (SDNN on iOS,
   * RMSSD on Android — baseline-relative comparisons only).
   */
  value: number;
  unit: 'count' | 'm' | 'kcal' | 'bpm' | 'ms';
  /** Heart metrics only (heartRate / restingHeartRate / heartRateVariability). */
  min?: number;
  max?: number;
}

export interface SleepStageSample {
  stage: SleepStage;
  start: number;
  end: number;
}

export interface SleepSession {
  start: number;
  end: number;
  /** Recording app/device name (iOS) or package (Android). */
  source?: string;
  stages: SleepStageSample[];
}

export interface Workout {
  start: number;
  end: number;
  /**
   * Mapped common name — "running", "walking", "cycling", "swimming",
   * "strength", "hiit", "yoga", "hiking", "elliptical", "rowing",
   * "tennis", "basketball", "soccer", "dance", "pilates", "stairs",
   * "golf", "core", "crossTraining" — or "other".
   */
  activityType: string;
  /**
   * Platform-native enum value (HKWorkoutActivityType rawValue on iOS,
   * ExerciseSessionRecord.EXERCISE_TYPE_* on Android) for exact needs.
   */
  rawActivityType: number;
  durationSec: number;
  /** kcal */
  calories?: number;
  distanceMeters?: number;
  source?: string;
}

export interface HeartRateSample {
  timestamp: number;
  bpm: number;
  source?: string;
}

/**
 * Reports whether health data is available on this device. Never rejects —
 * desktop resolves `{ available: false, platform: 'unsupported' }`.
 */
export async function isAvailable(): Promise<Availability> {
  return await invoke('plugin:health|is_available');
}

/**
 * Shows the OS permission UI for the requested metrics.
 *
 * - iOS: the HealthKit sheet appears once per type; later calls are no-ops.
 * - Android: launches the Health Connect grant flow.
 *
 * See {@link PermissionsResponse.state} for the accuracy contract.
 */
export async function requestPermissions(options: {
  read: Metric[];
}): Promise<PermissionsResponse> {
  return await invoke('plugin:health|request_permissions', { options });
}

/** Current grant state — see {@link PermissionsResponse.state}. */
export async function checkPermissions(): Promise<PermissionsResponse> {
  return await invoke('plugin:health|check_permissions');
}

/**
 * Bucketed aggregates for steps / distance / calories / heart rate /
 * resting heart rate / heart-rate variability. `start` inclusive, `end`
 * exclusive, epoch ms. Buckets align to the device-local calendar (`day`)
 * or fixed hours (`hour`).
 *
 * Note: Health Connect has no native HRV aggregate, so on Android the
 * plugin reads raw RMSSD records and buckets them itself (same bucket
 * semantics).
 */
export async function queryAggregated(options: {
  metric: AggregatableMetric;
  start: number;
  end: number;
  bucket: Bucket;
}): Promise<{ buckets: AggregatedBucket[] }> {
  return await invoke('plugin:health|query_aggregated', { options });
}

/**
 * Sleep sessions overlapping the range, with stage timelines.
 *
 * Android returns Health Connect's native sessions. iOS reconstructs
 * sessions from HealthKit samples (grouped per source, split on >60 min
 * gaps) — grouping may differ slightly from the Health app's.
 */
export async function querySleep(options: {
  start: number;
  end: number;
}): Promise<{ sessions: SleepSession[] }> {
  return await invoke('plugin:health|query_sleep', { options });
}

/** Workouts / exercise sessions in the range. */
export async function queryWorkouts(options: {
  start: number;
  end: number;
}): Promise<{ workouts: Workout[] }> {
  return await invoke('plugin:health|query_workouts', { options });
}

/**
 * Raw heart-rate samples in the range, chronological. `limit` keeps the
 * most recent N (default 1000).
 */
export async function queryHeartRateSamples(options: {
  start: number;
  end: number;
  limit?: number;
}): Promise<{ samples: HeartRateSample[] }> {
  return await invoke('plugin:health|query_heart_rate_samples', { options });
}

/** Opens the Health app (iOS) / Health Connect settings (Android). */
export async function openSettings(): Promise<void> {
  await invoke('plugin:health|open_settings');
}
