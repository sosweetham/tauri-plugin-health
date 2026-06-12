//
//  HealthQueries.swift
//  tauri-plugin-health
//
//  HealthKit query helpers. Aggregates use HKStatisticsCollectionQuery with
//  device-local calendar bucketing; sleep/workouts/heart-rate use
//  HKSampleQuery. HK completion handlers arrive on background queues —
//  results are resolved directly (no main-thread hop needed for IPC).
//
//  Results are built as the typeshare-generated wire structs
//  (HealthTypes.generated.swift) and resolved through Invoke's Encodable
//  overload, so the payload shape is compiler-checked against models.rs.
//

import Foundation
import HealthKit

final class HealthQueries {
    let store = HKHealthStore()

    private static func ms(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    // MARK: aggregates

    /// Single-quantity-type statistics collection over [start, end).
    private func statistics(
        type: HKQuantityType,
        metric: Metric,
        start: Date,
        end: Date,
        hourly: Bool,
        completion: @escaping (Result<[AggregatedBucket], Error>) -> Void
    ) {
        let calendar = Calendar.current
        let anchor = calendar.startOfDay(for: start)
        var interval = DateComponents()
        if hourly { interval.hour = 1 } else { interval.day = 1 }

        let options: HKStatisticsOptions =
            metric.isDiscrete ? [.discreteAverage, .discreteMin, .discreteMax] : .cumulativeSum
        let predicate = HKQuery.predicateForSamples(
            withStart: start, end: end, options: .strictStartDate)

        let query = HKStatisticsCollectionQuery(
            quantityType: type,
            quantitySamplePredicate: predicate,
            options: options,
            anchorDate: anchor,
            intervalComponents: interval
        )
        let (unit, unitName) = metric.unit
        query.initialResultsHandler = { _, collection, error in
            if let error {
                completion(.failure(error))
                return
            }
            var buckets: [AggregatedBucket] = []
            collection?.enumerateStatistics(from: start, to: end) { stats, _ in
                if metric.isDiscrete {
                    guard let avg = stats.averageQuantity() else { return }
                    buckets.append(
                        AggregatedBucket(
                            start: Self.ms(stats.startDate), end: Self.ms(stats.endDate),
                            value: avg.doubleValue(for: unit), unit: unitName,
                            min: stats.minimumQuantity()?.doubleValue(for: unit),
                            max: stats.maximumQuantity()?.doubleValue(for: unit)))
                } else {
                    let sum = stats.sumQuantity()?.doubleValue(for: unit) ?? 0
                    buckets.append(
                        AggregatedBucket(
                            start: Self.ms(stats.startDate), end: Self.ms(stats.endDate),
                            value: sum, unit: unitName, min: nil, max: nil))
                }
            }
            completion(.success(buckets))
        }
        store.execute(query)
    }

    func aggregate(
        metric: Metric,
        start: Date,
        end: Date,
        hourly: Bool,
        completion: @escaping (Result<[AggregatedBucket], Error>) -> Void
    ) {
        if metric == .totalCalories {
            // active + basal summed per bucket.
            let group = DispatchGroup()
            var active: [AggregatedBucket] = []
            var basal: [AggregatedBucket] = []
            var firstError: Error?

            group.enter()
            statistics(
                type: quantityType(.activeEnergyBurned), metric: metric,
                start: start, end: end, hourly: hourly
            ) { result in
                switch result {
                case .success(let buckets): active = buckets
                case .failure(let error): firstError = firstError ?? error
                }
                group.leave()
            }
            group.enter()
            statistics(
                type: quantityType(.basalEnergyBurned), metric: metric,
                start: start, end: end, hourly: hourly
            ) { result in
                switch result {
                case .success(let buckets): basal = buckets
                case .failure(let error): firstError = firstError ?? error
                }
                group.leave()
            }
            group.notify(queue: .global()) {
                if let error = firstError {
                    completion(.failure(error))
                    return
                }
                // Buckets share the same anchor/interval → merge by start time.
                var byStart: [Int64: AggregatedBucket] = [:]
                for bucket in active { byStart[bucket.start] = bucket }
                for bucket in basal {
                    if let existing = byStart[bucket.start] {
                        byStart[bucket.start] = AggregatedBucket(
                            start: existing.start, end: existing.end,
                            value: existing.value + bucket.value,
                            unit: existing.unit, min: nil, max: nil)
                    } else {
                        byStart[bucket.start] = bucket
                    }
                }
                completion(.success(byStart.values.sorted { $0.start < $1.start }))
            }
            return
        }

        guard let type = metric.objectTypes.first as? HKQuantityType else {
            completion(.success([]))
            return
        }
        statistics(
            type: type, metric: metric, start: start, end: end, hourly: hourly,
            completion: completion)
    }

    // MARK: sleep

    /// HealthKit has no sleep-session object: samples are grouped per
    /// source and split into sessions on gaps > 60 minutes.
    func sleepSessions(
        start: Date, end: Date,
        completion: @escaping (Result<[SleepSession], Error>) -> Void
    ) {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
        let query = HKSampleQuery(
            sampleType: categoryType(.sleepAnalysis),
            predicate: predicate,
            limit: HKObjectQueryNoLimit,
            sortDescriptors: [sort]
        ) { _, samples, error in
            if let error {
                completion(.failure(error))
                return
            }
            let categorySamples = (samples as? [HKCategorySample]) ?? []
            var bySource: [String: [HKCategorySample]] = [:]
            for sample in categorySamples {
                bySource[sample.sourceRevision.source.bundleIdentifier, default: []].append(sample)
            }

            var sessions: [SleepSession] = []
            let maxGap: TimeInterval = 60 * 60
            for (_, sourceSamples) in bySource {
                var current: [HKCategorySample] = []
                func flush() {
                    guard let first = current.first, let last = current.max(by: { $0.endDate < $1.endDate })
                    else { return }
                    sessions.append(
                        SleepSession(
                            start: Self.ms(first.startDate),
                            end: Self.ms(last.endDate),
                            source: first.sourceRevision.source.name,
                            stages: current.map {
                                SleepStageSample(
                                    stage: HealthMapping.sleepStage(fromRawValue: $0.value),
                                    start: Self.ms($0.startDate),
                                    end: Self.ms($0.endDate))
                            }))
                    current = []
                }
                for sample in sourceSamples {
                    if let last = current.last,
                        sample.startDate.timeIntervalSince(last.endDate) > maxGap
                    {
                        flush()
                    }
                    current.append(sample)
                }
                flush()
            }
            completion(.success(sessions.sorted { $0.start < $1.start }))
        }
        store.execute(query)
    }

    // MARK: workouts

    func workouts(
        start: Date, end: Date,
        completion: @escaping (Result<[Workout], Error>) -> Void
    ) {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
        let query = HKSampleQuery(
            sampleType: HKObjectType.workoutType(),
            predicate: predicate,
            limit: HKObjectQueryNoLimit,
            sortDescriptors: [sort]
        ) { _, samples, error in
            if let error {
                completion(.failure(error))
                return
            }
            let workouts = (samples as? [HKWorkout]) ?? []
            let results = workouts.map { workout -> Workout in
                var calories: Double?
                if #available(iOS 16.0, *) {
                    calories = workout.statistics(for: quantityType(.activeEnergyBurned))?
                        .sumQuantity()?.doubleValue(for: .kilocalorie())
                } else {
                    calories = workout.totalEnergyBurned?.doubleValue(for: .kilocalorie())
                }
                return Workout(
                    start: Self.ms(workout.startDate),
                    end: Self.ms(workout.endDate),
                    activityType: HealthMapping.activityName(
                        fromHKRawValue: workout.workoutActivityType.rawValue),
                    rawActivityType: Int64(workout.workoutActivityType.rawValue),
                    durationSec: workout.duration,
                    calories: calories,
                    distanceMeters: workout.totalDistance?.doubleValue(for: .meter()),
                    source: workout.sourceRevision.source.name)
            }
            completion(.success(results))
        }
        store.execute(query)
    }

    // MARK: heart-rate samples

    func heartRateSamples(
        start: Date, end: Date, limit: Int,
        completion: @escaping (Result<[HeartRateSample], Error>) -> Void
    ) {
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        // Newest first so `limit` keeps the most recent; reversed below.
        let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
        let bpmUnit = HKUnit.count().unitDivided(by: .minute())
        let query = HKSampleQuery(
            sampleType: quantityType(.heartRate),
            predicate: predicate,
            limit: limit,
            sortDescriptors: [sort]
        ) { _, samples, error in
            if let error {
                completion(.failure(error))
                return
            }
            let quantitySamples = (samples as? [HKQuantitySample]) ?? []
            let results = quantitySamples.map {
                HeartRateSample(
                    timestamp: Self.ms($0.startDate),
                    bpm: $0.quantity.doubleValue(for: bpmUnit),
                    source: $0.sourceRevision.source.name)
            }
            completion(.success(results.reversed()))
        }
        store.execute(query)
    }
}
