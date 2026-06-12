// Health Connect bridge. All connect-client APIs are suspend functions, so
// commands run inside a CoroutineScope and resolve the Invoke from the
// coroutine. The permission flow goes through Health Connect's
// ActivityResultContract via Tauri's startActivityForResult, and re-queries
// getGrantedPermissions() on return rather than parsing the result intent.
//
// Args and responses use the typeshare-generated wire types
// (HealthTypes.generated.kt), parsed/serialized through WireJson so the
// payload shape is compiler-checked against models.rs.

package app.tauri.health

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Resolves with a typeshare wire type serialized through WireJson. */
private fun Invoke.resolveWire(value: Any) {
    resolve(JSObject(WireJson.stringify(value)))
}

@TauriPlugin
class HealthPlugin(private val activity: Activity) : Plugin(activity) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val client: HealthConnectClient?
        get() = if (HealthConnectClient.getSdkStatus(activity) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(activity)
        } else {
            null
        }

    private var pendingPermissionMetrics: List<Metric> = emptyList()

    @Command
    fun isAvailable(invoke: Invoke) {
        val status = HealthConnectClient.getSdkStatus(activity)
        invoke.resolveWire(
            Availability(
                available = status == HealthConnectClient.SDK_AVAILABLE,
                platform = HealthPlatform.Android,
                reason = when (status) {
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                        AvailabilityReason.ProviderUpdateRequired
                    HealthConnectClient.SDK_UNAVAILABLE -> AvailabilityReason.ProviderUnavailable
                    else -> null
                },
            ),
        )
    }

    @Command
    override fun requestPermissions(invoke: Invoke) {
        val args = WireJson.parse<RequestPermissionsOptions>(invoke.getRawArgs())
        if (client == null) {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        if (args.read.isEmpty()) {
            invoke.reject("no metrics in request")
            return
        }
        pendingPermissionMetrics = args.read
        val permissions = args.read.map { HealthMapping.readPermission(it) }.toSet()
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent: Intent = contract.createIntent(activity, permissions)
        startActivityForResult(invoke, intent, "onHealthPermissionResult")
    }

    @ActivityCallback
    fun onHealthPermissionResult(invoke: Invoke, result: ActivityResult) {
        // The most robust source of truth is re-querying the grant set.
        scope.launch {
            try {
                val healthClient = client
                    ?: throw IllegalStateException("Health Connect is not available")
                val granted = healthClient.permissionController.getGrantedPermissions()
                val metrics = pendingPermissionMetrics.filter {
                    HealthMapping.readPermission(it) in granted
                }
                invoke.resolveWire(PermissionsResponse(metrics, PermissionAccuracy.Exact))
            } catch (e: Exception) {
                invoke.reject(e.message ?: "permission check failed")
            }
        }
    }

    @Command
    override fun checkPermissions(invoke: Invoke) {
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val granted = healthClient.permissionController.getGrantedPermissions()
                val metrics = Metric.values().filter {
                    HealthMapping.readPermission(it) in granted
                }
                invoke.resolveWire(PermissionsResponse(metrics, PermissionAccuracy.Exact))
            } catch (e: Exception) {
                invoke.reject(e.message ?: "permission check failed")
            }
        }
    }

    @Command
    fun queryAggregated(invoke: Invoke) {
        val args = WireJson.parse<QueryAggregatedOptions>(invoke.getRawArgs())
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val unit = HealthMapping.unit(args.metric)
                val buckets = mutableListOf<AggregatedBucket>()
                val zone = ZoneId.systemDefault()

                // Health Connect has no HRV aggregate — read the raw RMSSD
                // records and bucket them here with the same semantics.
                if (args.metric == Metric.HeartRateVariability) {
                    invoke.resolveWire(
                        QueryAggregatedResponse(aggregateHrvManually(healthClient, args, zone)),
                    )
                    return@launch
                }

                if (args.bucket == Bucket.Day) {
                    val timeRange = TimeRangeFilter.between(
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(args.start), zone),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(args.end), zone),
                    )
                    val rows = healthClient.aggregateGroupByPeriod(
                        AggregateGroupByPeriodRequest(
                            metrics = aggregateMetricsFor(args.metric),
                            timeRangeFilter = timeRange,
                            timeRangeSlicer = Period.ofDays(1),
                        ),
                    )
                    for (row in rows) {
                        bucketFor(
                            args.metric, unit, row.result,
                            start = row.startTime.atZone(zone).toInstant().toEpochMilli(),
                            end = row.endTime.atZone(zone).toInstant().toEpochMilli(),
                        )?.let { buckets.add(it) }
                    }
                } else {
                    val timeRange = TimeRangeFilter.between(
                        Instant.ofEpochMilli(args.start),
                        Instant.ofEpochMilli(args.end),
                    )
                    val rows = healthClient.aggregateGroupByDuration(
                        AggregateGroupByDurationRequest(
                            metrics = aggregateMetricsFor(args.metric),
                            timeRangeFilter = timeRange,
                            timeRangeSlicer = Duration.ofHours(1),
                        ),
                    )
                    for (row in rows) {
                        bucketFor(
                            args.metric, unit, row.result,
                            start = row.startTime.toEpochMilli(),
                            end = row.endTime.toEpochMilli(),
                        )?.let { buckets.add(it) }
                    }
                }
                invoke.resolveWire(QueryAggregatedResponse(buckets))
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "queryAggregated failed")
            }
        }
    }

    private fun aggregateMetricsFor(metric: Metric) = when (metric) {
        Metric.Steps -> setOf(StepsRecord.COUNT_TOTAL)
        Metric.Distance -> setOf(DistanceRecord.DISTANCE_TOTAL)
        Metric.ActiveCalories -> setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        Metric.TotalCalories -> setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        Metric.HeartRate -> setOf(
            HeartRateRecord.BPM_AVG,
            HeartRateRecord.BPM_MIN,
            HeartRateRecord.BPM_MAX,
        )
        Metric.RestingHeartRate -> setOf(
            RestingHeartRateRecord.BPM_AVG,
            RestingHeartRateRecord.BPM_MIN,
            RestingHeartRateRecord.BPM_MAX,
        )
        // HRV is bucketed manually above; workouts/sleep are rejected by
        // the Rust command before they reach the bridge.
        Metric.HeartRateVariability, Metric.Workouts, Metric.Sleep ->
            throw IllegalArgumentException("not an aggregatable metric: ${metric.string}")
    }

    /**
     * Manual bucketing for HRV (no Health Connect aggregate exists): mean /
     * min / max of `heartRateVariabilityMillis` per bucket, mirroring the
     * aggregate semantics — `day` slices on the device-local calendar,
     * `hour` slices in fixed steps from `args.start`. Empty buckets are
     * skipped, like the other discrete metrics.
     */
    private suspend fun aggregateHrvManually(
        healthClient: HealthConnectClient,
        args: QueryAggregatedOptions,
        zone: ZoneId,
    ): List<AggregatedBucket> {
        val values = mutableListOf<Pair<Long, Double>>() // (epoch ms, ms)
        var pageToken: String? = null
        do {
            val response = healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(args.start),
                        Instant.ofEpochMilli(args.end),
                    ),
                    pageToken = pageToken,
                ),
            )
            for (record in response.records) {
                values.add(record.time.toEpochMilli() to record.heartRateVariabilityMillis)
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        values.sortBy { it.first }

        // Bucket bounds as [startMs, endMs) pairs.
        val bounds = mutableListOf<Pair<Long, Long>>()
        if (args.bucket == Bucket.Day) {
            var cursor = LocalDateTime.ofInstant(Instant.ofEpochMilli(args.start), zone)
                .toLocalDate().atStartOfDay(zone)
            val endInstant = Instant.ofEpochMilli(args.end)
            while (cursor.toInstant() < endInstant) {
                val next = cursor.plusDays(1)
                bounds.add(cursor.toInstant().toEpochMilli() to next.toInstant().toEpochMilli())
                cursor = next
            }
        } else {
            var cursor = args.start
            while (cursor < args.end) {
                val next = cursor + Duration.ofHours(1).toMillis()
                bounds.add(cursor to next)
                cursor = next
            }
        }

        val buckets = mutableListOf<AggregatedBucket>()
        var i = 0
        for ((startMs, endMs) in bounds) {
            var sum = 0.0
            var count = 0
            var min = Double.POSITIVE_INFINITY
            var max = Double.NEGATIVE_INFINITY
            while (i < values.size && values[i].first < endMs) {
                val (ts, v) = values[i]
                if (ts >= startMs) {
                    sum += v
                    count++
                    if (v < min) min = v
                    if (v > max) max = v
                }
                i++
            }
            if (count == 0) continue
            buckets.add(
                AggregatedBucket(
                    start = startMs,
                    end = endMs,
                    value = sum / count,
                    unit = HealthUnit.Ms,
                    min = min,
                    max = max,
                ),
            )
        }
        return buckets
    }

    /** Builds one bucket; null when the bucket is empty. */
    private fun bucketFor(
        metric: Metric,
        unit: HealthUnit,
        result: AggregationResult,
        start: Long,
        end: Long,
    ): AggregatedBucket? = when (metric) {
        Metric.Steps -> AggregatedBucket(
            start, end,
            value = (result[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble(),
            unit = unit,
        )
        Metric.Distance -> AggregatedBucket(
            start, end,
            value = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0,
            unit = unit,
        )
        Metric.ActiveCalories -> AggregatedBucket(
            start, end,
            value = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                ?: 0.0,
            unit = unit,
        )
        Metric.TotalCalories -> AggregatedBucket(
            start, end,
            value = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0,
            unit = unit,
        )
        // Skip empty buckets for discrete metrics.
        Metric.HeartRate -> result[HeartRateRecord.BPM_AVG]?.let { avg ->
            AggregatedBucket(
                start, end,
                value = avg.toDouble(),
                unit = unit,
                min = result[HeartRateRecord.BPM_MIN]?.toDouble(),
                max = result[HeartRateRecord.BPM_MAX]?.toDouble(),
            )
        }
        Metric.RestingHeartRate -> result[RestingHeartRateRecord.BPM_AVG]?.let { avg ->
            AggregatedBucket(
                start, end,
                value = avg.toDouble(),
                unit = unit,
                min = result[RestingHeartRateRecord.BPM_MIN]?.toDouble(),
                max = result[RestingHeartRateRecord.BPM_MAX]?.toDouble(),
            )
        }
        Metric.HeartRateVariability, Metric.Workouts, Metric.Sleep -> null
    }

    @Command
    fun querySleep(invoke: Invoke) {
        val args = WireJson.parse<QueryRangeOptions>(invoke.getRawArgs())
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val sessions = mutableListOf<SleepSession>()
                var pageToken: String? = null
                do {
                    val response = healthClient.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.ofEpochMilli(args.start),
                                Instant.ofEpochMilli(args.end),
                            ),
                            pageToken = pageToken,
                        ),
                    )
                    for (record in response.records) {
                        sessions.add(
                            SleepSession(
                                start = record.startTime.toEpochMilli(),
                                end = record.endTime.toEpochMilli(),
                                source = record.metadata.dataOrigin.packageName,
                                stages = record.stages.map { stage ->
                                    SleepStageSample(
                                        stage = HealthMapping.sleepStage(stage.stage),
                                        start = stage.startTime.toEpochMilli(),
                                        end = stage.endTime.toEpochMilli(),
                                    )
                                },
                            ),
                        )
                    }
                    pageToken = response.pageToken
                } while (pageToken != null)
                invoke.resolveWire(QuerySleepResponse(sessions))
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "querySleep failed")
            }
        }
    }

    @Command
    fun queryWorkouts(invoke: Invoke) {
        val args = WireJson.parse<QueryRangeOptions>(invoke.getRawArgs())
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val workouts = mutableListOf<Workout>()
                var pageToken: String? = null
                // Calories/distance need a per-session aggregate (N+1); cap
                // the enriched session count to bound the cost.
                val maxSessions = 100
                outer@ do {
                    val response = healthClient.readRecords(
                        ReadRecordsRequest(
                            recordType = ExerciseSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.ofEpochMilli(args.start),
                                Instant.ofEpochMilli(args.end),
                            ),
                            pageToken = pageToken,
                        ),
                    )
                    for (record in response.records) {
                        if (workouts.size >= maxSessions) break@outer
                        var workout = Workout(
                            start = record.startTime.toEpochMilli(),
                            end = record.endTime.toEpochMilli(),
                            activityType = HealthMapping.exerciseType(record.exerciseType),
                            rawActivityType = record.exerciseType.toLong(),
                            durationSec = Duration.between(record.startTime, record.endTime)
                                .seconds.toDouble(),
                            source = record.metadata.dataOrigin.packageName,
                        )
                        try {
                            val aggregate = healthClient.aggregate(
                                AggregateRequest(
                                    metrics = setOf(
                                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                                        DistanceRecord.DISTANCE_TOTAL,
                                    ),
                                    timeRangeFilter = TimeRangeFilter.between(
                                        record.startTime,
                                        record.endTime,
                                    ),
                                ),
                            )
                            workout = workout.copy(
                                calories = aggregate[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                                    ?.inKilocalories,
                                distanceMeters = aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                            )
                        } catch (_: Exception) {
                            // Enrichment is best-effort (may lack permissions).
                        }
                        workouts.add(workout)
                    }
                    pageToken = response.pageToken
                } while (pageToken != null)
                invoke.resolveWire(QueryWorkoutsResponse(workouts))
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "queryWorkouts failed")
            }
        }
    }

    @Command
    fun queryHeartRateSamples(invoke: Invoke) {
        val args = WireJson.parse<QueryHeartRateSamplesOptions>(invoke.getRawArgs())
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val all = mutableListOf<Pair<Long, Double>>()
                val sources = mutableMapOf<Long, String>()
                var pageToken: String? = null
                do {
                    val response = healthClient.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(
                                Instant.ofEpochMilli(args.start),
                                Instant.ofEpochMilli(args.end),
                            ),
                            pageToken = pageToken,
                        ),
                    )
                    for (record in response.records) {
                        val source = record.metadata.dataOrigin.packageName
                        for (sample in record.samples) {
                            val ts = sample.time.toEpochMilli()
                            all.add(ts to sample.beatsPerMinute.toDouble())
                            sources[ts] = source
                        }
                    }
                    pageToken = response.pageToken
                } while (pageToken != null)

                all.sortBy { it.first }
                val limit = args.limit?.toInt() ?: 1000
                val kept = if (all.size > limit) all.takeLast(limit) else all
                val samples = kept.map { (ts, bpm) ->
                    HeartRateSample(timestamp = ts, bpm = bpm, source = sources[ts])
                }
                invoke.resolveWire(QueryHeartRateSamplesResponse(samples))
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "queryHeartRateSamples failed")
            }
        }
    }

    @Command
    fun openSettings(invoke: Invoke) {
        try {
            activity.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "could not open Health Connect settings")
        }
    }
}
