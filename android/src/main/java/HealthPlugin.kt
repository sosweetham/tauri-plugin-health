// Health Connect bridge. All connect-client APIs are suspend functions, so
// commands run inside a CoroutineScope and resolve the Invoke from the
// coroutine. The permission flow goes through Health Connect's
// ActivityResultContract via Tauri's startActivityForResult, and re-queries
// getGrantedPermissions() on return rather than parsing the result intent.

package app.tauri.health

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.PermissionController
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
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
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSArray
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

@InvokeArg
class RequestPermissionsArgs {
    lateinit var read: List<String>
}

@InvokeArg
class QueryAggregatedArgs {
    lateinit var metric: String
    var start: Long = 0
    var end: Long = 0
    lateinit var bucket: String
}

@InvokeArg
class QueryRangeArgs {
    var start: Long = 0
    var end: Long = 0
}

@InvokeArg
class QueryHeartRateSamplesArgs {
    var start: Long = 0
    var end: Long = 0
    var limit: Int? = null
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

    private var pendingPermissionMetrics: List<String> = emptyList()

    @Command
    fun isAvailable(invoke: Invoke) {
        val status = HealthConnectClient.getSdkStatus(activity)
        invoke.resolve(JSObject().apply {
            put("available", status == HealthConnectClient.SDK_AVAILABLE)
            put("platform", "android")
            when (status) {
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    put("reason", "providerUpdateRequired")
                HealthConnectClient.SDK_UNAVAILABLE ->
                    put("reason", "providerUnavailable")
            }
        })
    }

    @Command
    override fun requestPermissions(invoke: Invoke) {
        val args = invoke.parseArgs(RequestPermissionsArgs::class.java)
        if (client == null) {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        pendingPermissionMetrics = args.read
        val permissions = args.read.mapNotNull { HealthMapping.readPermission(it) }.toSet()
        if (permissions.isEmpty()) {
            invoke.reject("no known metrics in request")
            return
        }
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
                invoke.resolve(grantResponse(metrics))
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
                val metrics = HealthMapping.RECORD_TYPES.keys.filter {
                    HealthMapping.readPermission(it) in granted
                }
                invoke.resolve(grantResponse(metrics))
            } catch (e: Exception) {
                invoke.reject(e.message ?: "permission check failed")
            }
        }
    }

    private fun grantResponse(metrics: List<String>): JSObject = JSObject().apply {
        put("granted", JSArray(metrics))
        put("state", "exact")
    }

    @Command
    fun queryAggregated(invoke: Invoke) {
        val args = invoke.parseArgs(QueryAggregatedArgs::class.java)
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val unit = HealthMapping.unit(args.metric)
                val buckets = JSArray()
                val zone = ZoneId.systemDefault()

                if (args.bucket == "day") {
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
                        bucketJson(args.metric, unit, row.result)?.let { json ->
                            json.put("start", row.startTime.atZone(zone).toInstant().toEpochMilli())
                            json.put("end", row.endTime.atZone(zone).toInstant().toEpochMilli())
                            buckets.put(json)
                        }
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
                        bucketJson(args.metric, unit, row.result)?.let { json ->
                            json.put("start", row.startTime.toEpochMilli())
                            json.put("end", row.endTime.toEpochMilli())
                            buckets.put(json)
                        }
                    }
                }
                invoke.resolve(JSObject().apply { put("buckets", buckets) })
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "queryAggregated failed")
            }
        }
    }

    private fun aggregateMetricsFor(metric: String) = when (metric) {
        "steps" -> setOf(StepsRecord.COUNT_TOTAL)
        "distance" -> setOf(DistanceRecord.DISTANCE_TOTAL)
        "activeCalories" -> setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        "totalCalories" -> setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        "heartRate" -> setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX)
        else -> throw IllegalArgumentException("unknown metric: $metric")
    }

    /** Builds the value part of a bucket; null when the bucket is empty. */
    private fun bucketJson(
        metric: String,
        unit: String,
        result: androidx.health.connect.client.aggregate.AggregationResult,
    ): JSObject? {
        val json = JSObject().apply { put("unit", unit) }
        when (metric) {
            "steps" -> {
                val value = result[StepsRecord.COUNT_TOTAL] ?: 0L
                json.put("value", value.toDouble())
            }
            "distance" -> {
                val value = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                json.put("value", value)
            }
            "activeCalories" -> {
                val value =
                    result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                json.put("value", value)
            }
            "totalCalories" -> {
                val value = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                json.put("value", value)
            }
            "heartRate" -> {
                // Skip empty buckets for discrete metrics.
                val avg = result[HeartRateRecord.BPM_AVG] ?: return null
                json.put("value", avg.toDouble())
                result[HeartRateRecord.BPM_MIN]?.let { json.put("min", it.toDouble()) }
                result[HeartRateRecord.BPM_MAX]?.let { json.put("max", it.toDouble()) }
            }
            else -> return null
        }
        return json
    }

    @Command
    fun querySleep(invoke: Invoke) {
        val args = invoke.parseArgs(QueryRangeArgs::class.java)
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val sessions = JSArray()
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
                        val stages = JSArray()
                        for (stage in record.stages) {
                            stages.put(JSObject().apply {
                                put("stage", HealthMapping.sleepStageName(stage.stage))
                                put("start", stage.startTime.toEpochMilli())
                                put("end", stage.endTime.toEpochMilli())
                            })
                        }
                        sessions.put(JSObject().apply {
                            put("start", record.startTime.toEpochMilli())
                            put("end", record.endTime.toEpochMilli())
                            put("source", record.metadata.dataOrigin.packageName)
                            put("stages", stages)
                        })
                    }
                    pageToken = response.pageToken
                } while (pageToken != null)
                invoke.resolve(JSObject().apply { put("sessions", sessions) })
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "querySleep failed")
            }
        }
    }

    @Command
    fun queryWorkouts(invoke: Invoke) {
        val args = invoke.parseArgs(QueryRangeArgs::class.java)
        val healthClient = client ?: run {
            invoke.reject("Health Connect is not available on this device")
            return
        }
        scope.launch {
            try {
                val workouts = JSArray()
                var pageToken: String? = null
                var count = 0
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
                        if (count >= maxSessions) break@outer
                        count++
                        val json = JSObject().apply {
                            put("start", record.startTime.toEpochMilli())
                            put("end", record.endTime.toEpochMilli())
                            put("activityType", HealthMapping.exerciseName(record.exerciseType))
                            put("rawActivityType", record.exerciseType)
                            put(
                                "durationSec",
                                Duration.between(record.startTime, record.endTime).seconds.toDouble(),
                            )
                            put("source", record.metadata.dataOrigin.packageName)
                        }
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
                            aggregate[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                                ?.let { json.put("calories", it.inKilocalories) }
                            aggregate[DistanceRecord.DISTANCE_TOTAL]
                                ?.let { json.put("distanceMeters", it.inMeters) }
                        } catch (_: Exception) {
                            // Enrichment is best-effort (may lack permissions).
                        }
                        workouts.put(json)
                    }
                    pageToken = response.pageToken
                } while (pageToken != null)
                invoke.resolve(JSObject().apply { put("workouts", workouts) })
            } catch (e: SecurityException) {
                invoke.reject("permission not granted: ${e.message}")
            } catch (e: Exception) {
                invoke.reject(e.message ?: "queryWorkouts failed")
            }
        }
    }

    @Command
    fun queryHeartRateSamples(invoke: Invoke) {
        val args = invoke.parseArgs(QueryHeartRateSamplesArgs::class.java)
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
                val limit = args.limit ?: 1000
                val kept = if (all.size > limit) all.takeLast(limit) else all
                val samples = JSArray()
                for ((ts, bpm) in kept) {
                    samples.put(JSObject().apply {
                        put("timestamp", ts)
                        put("bpm", bpm)
                        sources[ts]?.let { put("source", it) }
                    })
                }
                invoke.resolve(JSObject().apply { put("samples", samples) })
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
