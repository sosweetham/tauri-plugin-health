//
//  HealthMapping.swift
//  tauri-plugin-health
//
//  Metric ↔ HealthKit type/unit tables plus the cross-platform sleep-stage
//  and workout-activity name maps. The output strings are kept in lockstep
//  with the Android HealthMapping.kt — same unions on both platforms.
//

import HealthKit

enum HealthMetric: String, CaseIterable {
    case steps
    case distance
    case activeCalories
    case totalCalories
    case heartRate
    case restingHeartRate
    case heartRateVariability
    case workouts
    case sleep

    /// HK types whose read permission this metric needs.
    var objectTypes: [HKObjectType] {
        switch self {
        case .steps: return [HKQuantityType(.stepCount)]
        case .distance: return [HKQuantityType(.distanceWalkingRunning)]
        case .activeCalories: return [HKQuantityType(.activeEnergyBurned)]
        // HealthKit has no single "total calories" type — totals are
        // active + basal energy summed per bucket.
        case .totalCalories:
            return [HKQuantityType(.activeEnergyBurned), HKQuantityType(.basalEnergyBurned)]
        case .heartRate: return [HKQuantityType(.heartRate)]
        case .restingHeartRate: return [HKQuantityType(.restingHeartRate)]
        // SDNN on iOS (Android reads RMSSD) — baseline-relative only.
        case .heartRateVariability: return [HKQuantityType(.heartRateVariabilitySDNN)]
        case .workouts: return [HKObjectType.workoutType()]
        case .sleep: return [HKCategoryType(.sleepAnalysis)]
        }
    }

    /// (HKUnit, wire unit string) for aggregatable metrics.
    var unit: (HKUnit, String) {
        switch self {
        case .steps: return (.count(), "count")
        case .distance: return (.meter(), "m")
        case .activeCalories, .totalCalories: return (.kilocalorie(), "kcal")
        case .heartRate, .restingHeartRate:
            return (HKUnit.count().unitDivided(by: .minute()), "bpm")
        case .heartRateVariability: return (HKUnit.secondUnit(with: .milli), "ms")
        case .workouts, .sleep: return (.count(), "count")  // unused
        }
    }

    /// Discrete quantities aggregate by avg/min/max; the rest sum.
    var isDiscrete: Bool {
        switch self {
        case .heartRate, .restingHeartRate, .heartRateVariability: return true
        default: return false
        }
    }
}

enum HealthMapping {
    /// HKCategoryValueSleepAnalysis raw values → shared stage strings.
    /// Raw-value switching avoids #available(iOS 16) gating: the stage
    /// values 3–5 only ever appear in data written by iOS 16+ sources,
    /// but reading the raw Int is safe everywhere.
    static func sleepStage(fromRawValue value: Int) -> String {
        switch value {
        case 0: return "inBed"  // .inBed
        case 1: return "asleep"  // .asleepUnspecified
        case 2: return "awake"  // .awake
        case 3: return "light"  // .asleepCore
        case 4: return "deep"  // .asleepDeep
        case 5: return "rem"  // .asleepREM
        default: return "unknown"
        }
    }

    /// Common HKWorkoutActivityType values → shared activity names.
    /// Everything else maps to "other"; the raw value is always reported
    /// alongside.
    static func activityName(fromHKRawValue value: UInt) -> String {
        guard let type = HKWorkoutActivityType(rawValue: value) else { return "other" }
        switch type {
        case .running: return "running"
        case .walking: return "walking"
        case .cycling: return "cycling"
        case .swimming: return "swimming"
        case .traditionalStrengthTraining, .functionalStrengthTraining: return "strength"
        case .highIntensityIntervalTraining: return "hiit"
        case .yoga: return "yoga"
        case .hiking: return "hiking"
        case .elliptical: return "elliptical"
        case .rowing: return "rowing"
        case .tennis: return "tennis"
        case .basketball: return "basketball"
        case .soccer: return "soccer"
        case .socialDance, .cardioDance: return "dance"
        case .pilates: return "pilates"
        case .stairClimbing, .stairs: return "stairs"
        case .golf: return "golf"
        case .coreTraining: return "core"
        case .crossTraining: return "crossTraining"
        default: return "other"
        }
    }
}
