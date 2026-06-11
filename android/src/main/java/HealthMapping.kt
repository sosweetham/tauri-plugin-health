// Metric ↔ Health Connect record/permission/aggregate tables plus the
// cross-platform sleep-stage and exercise-type name maps. The output
// strings are kept in lockstep with the iOS HealthMapping.swift — same
// unions on both platforms.

package app.tauri.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import kotlin.reflect.KClass

object HealthMapping {
    /** Record class per metric — drives permission strings. */
    val RECORD_TYPES: Map<String, KClass<out Record>> = mapOf(
        "steps" to StepsRecord::class,
        "distance" to DistanceRecord::class,
        "activeCalories" to ActiveCaloriesBurnedRecord::class,
        "totalCalories" to TotalCaloriesBurnedRecord::class,
        "heartRate" to HeartRateRecord::class,
        "workouts" to ExerciseSessionRecord::class,
        "sleep" to SleepSessionRecord::class,
    )

    fun readPermission(metric: String): String? =
        RECORD_TYPES[metric]?.let { HealthPermission.getReadPermission(it) }

    /** Wire unit string per aggregatable metric. */
    fun unit(metric: String): String = when (metric) {
        "steps" -> "count"
        "distance" -> "m"
        "activeCalories", "totalCalories" -> "kcal"
        "heartRate" -> "bpm"
        else -> "count"
    }

    fun sleepStageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "inBed"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
        SleepSessionRecord.STAGE_TYPE_REM -> "rem"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "asleep"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "outOfBed"
        else -> "unknown"
    }

    /** Common EXERCISE_TYPE_* values → shared activity names. */
    fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "strength"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "hiit"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "elliptical"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "rowing"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "basketball"
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "soccer"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "dance"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "stairs"
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "golf"
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "core"
        else -> "other"
    }
}
