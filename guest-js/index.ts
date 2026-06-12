import { invoke } from '@tauri-apps/api/core';
import type {
  Availability,
  Bucket,
  Metric,
  PermissionsResponse,
  QueryAggregatedResponse,
  QueryHeartRateSamplesResponse,
  QuerySleepResponse,
  QueryWorkoutsResponse,
} from './bindings';

// All wire types are generated from the plugin's src/models.rs by typeshare
// (`pnpm generate-types`) — edit the Rust types, not bindings.ts.
export * from './bindings';

/** Metrics usable with {@link queryAggregated}. */
export type AggregatableMetric = Exclude<Metric, Metric.Workouts | Metric.Sleep>;

/**
 * Input positions accept either the generated enum member or its literal
 * wire value (`Metric.Steps` or `'steps'`) — both serialize identically.
 */
type Like<T extends string> = T | `${T}`;

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
  read: Like<Metric>[];
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
  metric: Like<AggregatableMetric>;
  start: number;
  end: number;
  bucket: Like<Bucket>;
}): Promise<QueryAggregatedResponse> {
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
}): Promise<QuerySleepResponse> {
  return await invoke('plugin:health|query_sleep', { options });
}

/** Workouts / exercise sessions in the range. */
export async function queryWorkouts(options: {
  start: number;
  end: number;
}): Promise<QueryWorkoutsResponse> {
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
}): Promise<QueryHeartRateSamplesResponse> {
  return await invoke('plugin:health|query_heart_rate_samples', { options });
}

/** Opens the Health app (iOS) / Health Connect settings (Android). */
export async function openSettings(): Promise<void> {
  await invoke('plugin:health|open_settings');
}
