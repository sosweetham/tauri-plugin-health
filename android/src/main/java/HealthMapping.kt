// Metric ↔ Health Connect record/permission tables plus the sleep-stage and
// exercise-type maps. The wire types (Metric, HealthUnit, SleepStage,
// ActivityType, …) are generated from the plugin's src/models.rs by
// typeshare — see HealthTypes.generated.kt. Only the Health-Connect-facing
// mapping lives here; the `when`s are exhaustive so a new Metric variant in
// models.rs fails this file's compile until it is mapped.

package app.tauri.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import kotlin.reflect.KClass

object HealthMapping {
    /** Record class per metric — drives permission strings. */
    fun recordType(metric: Metric): KClass<out Record> = when (metric) {
        Metric.Steps -> StepsRecord::class
        Metric.Distance -> DistanceRecord::class
        Metric.ActiveCalories -> ActiveCaloriesBurnedRecord::class
        Metric.TotalCalories -> TotalCaloriesBurnedRecord::class
        Metric.HeartRate -> HeartRateRecord::class
        Metric.RestingHeartRate -> RestingHeartRateRecord::class
        // RMSSD on Android (iOS reads SDNN) — baseline-relative only.
        Metric.HeartRateVariability -> HeartRateVariabilityRmssdRecord::class
        Metric.Workouts -> ExerciseSessionRecord::class
        Metric.Sleep -> SleepSessionRecord::class
    }

    fun readPermission(metric: Metric): String =
        HealthPermission.getReadPermission(recordType(metric))

    /** Wire unit per aggregatable metric. */
    fun unit(metric: Metric): HealthUnit = when (metric) {
        Metric.Steps -> HealthUnit.Count
        Metric.Distance -> HealthUnit.M
        Metric.ActiveCalories, Metric.TotalCalories -> HealthUnit.Kcal
        Metric.HeartRate, Metric.RestingHeartRate -> HealthUnit.Bpm
        Metric.HeartRateVariability -> HealthUnit.Ms
        Metric.Workouts, Metric.Sleep -> HealthUnit.Count
    }

    fun sleepStage(stage: Int): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> SleepStage.Awake
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.InBed
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.Light
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.Deep
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.Rem
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.Asleep
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.OutOfBed
        else -> SleepStage.Unknown
    }

    /** Common EXERCISE_TYPE_* values → wire activity types. */
    fun exerciseType(type: Int): ActivityType = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ActivityType.Running
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.Walking
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ActivityType.Cycling
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ActivityType.Swimming
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> ActivityType.Strength
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> ActivityType.Hiit
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ActivityType.Yoga
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ActivityType.Hiking
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> ActivityType.Elliptical
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> ActivityType.Rowing
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> ActivityType.Tennis
        ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> ActivityType.Basketball
        ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> ActivityType.Soccer
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> ActivityType.Dance
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> ActivityType.Pilates
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> ActivityType.Stairs
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> ActivityType.Golf
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> ActivityType.Core
        else -> ActivityType.Other
    }
}
