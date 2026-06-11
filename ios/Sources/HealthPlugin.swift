//
//  HealthPlugin.swift
//  tauri-plugin-health
//
//  Tauri command entry points for HealthKit. The consumer app must carry
//  the com.apple.developer.healthkit entitlement and an
//  NSHealthShareUsageDescription Info.plist string — see the README.
//
//  Read-permission honesty: HealthKit hides read denials by design, so
//  permission responses report state "unknown" and denied metrics simply
//  return empty data.
//

import Foundation
import HealthKit
import Tauri
import UIKit

class RequestPermissionsArgs: Decodable {
    let read: [String]
}

class QueryAggregatedArgs: Decodable {
    let metric: String
    let start: Double
    let end: Double
    let bucket: String
}

class QueryRangeArgs: Decodable {
    let start: Double
    let end: Double
}

class QueryHeartRateSamplesArgs: Decodable {
    let start: Double
    let end: Double
    let limit: Int?
}

class HealthPlugin: Plugin {
    private let queries = HealthQueries()

    private func date(fromMs ms: Double) -> Date {
        Date(timeIntervalSince1970: ms / 1000)
    }

    private func bucketArray(_ buckets: [BucketResult]) -> [JSObject] {
        buckets.map { bucket in
            var obj: JSObject = [
                "start": bucket.start,
                "end": bucket.end,
                "value": bucket.value,
                "unit": bucket.unit,
            ]
            if let min = bucket.min { obj["min"] = min }
            if let max = bucket.max { obj["max"] = max }
            return obj
        }
    }

    @objc public func isAvailable(_ invoke: Invoke) {
        let available = HKHealthStore.isHealthDataAvailable()
        var data: JSObject = ["available": available, "platform": "ios"]
        if !available {
            data["reason"] = "healthDataUnavailable"
        }
        invoke.resolve(data)
    }

    @objc public override func requestPermissions(_ invoke: Invoke) {
        let args: RequestPermissionsArgs
        do {
            args = try invoke.parseArgs(RequestPermissionsArgs.self)
        } catch {
            invoke.reject(error.localizedDescription)
            return
        }
        guard HKHealthStore.isHealthDataAvailable() else {
            invoke.reject("health data is not available on this device")
            return
        }
        let metrics = args.read.compactMap { HealthMetric(rawValue: $0) }
        var types = Set<HKObjectType>()
        for metric in metrics {
            types.formUnion(metric.objectTypes)
        }
        queries.store.requestAuthorization(toShare: nil, read: types) { ok, error in
            if let error {
                invoke.reject(error.localizedDescription)
                return
            }
            guard ok else {
                invoke.reject("HealthKit authorization request failed")
                return
            }
            // HealthKit cannot report read grants — echo the request.
            invoke.resolve([
                "granted": metrics.map { $0.rawValue },
                "state": "unknown",
            ] as JSObject)
        }
    }

    @objc public override func checkPermissions(_ invoke: Invoke) {
        // Read grants are unknowable on iOS by design; report everything as
        // potentially granted with state "unknown".
        invoke.resolve([
            "granted": HealthMetric.allCases.map { $0.rawValue },
            "state": "unknown",
        ] as JSObject)
    }

    @objc public func queryAggregated(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryAggregatedArgs.self)
        guard let metric = HealthMetric(rawValue: args.metric) else {
            invoke.reject("unknown metric: \(args.metric)")
            return
        }
        queries.aggregate(
            metric: metric,
            start: date(fromMs: args.start),
            end: date(fromMs: args.end),
            hourly: args.bucket == "hour"
        ) { [weak self] result in
            switch result {
            case .success(let buckets):
                invoke.resolve(["buckets": self?.bucketArray(buckets) ?? []] as JSObject)
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func querySleep(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryRangeArgs.self)
        queries.sleepSessions(start: date(fromMs: args.start), end: date(fromMs: args.end)) {
            result in
            switch result {
            case .success(let sessions):
                let payload = sessions.map { session -> JSObject in
                    var obj: JSObject = [
                        "start": session.start,
                        "end": session.end,
                        "stages": session.stages.map {
                            ["stage": $0.stage, "start": $0.start, "end": $0.end] as JSObject
                        },
                    ]
                    if let source = session.source { obj["source"] = source }
                    return obj
                }
                invoke.resolve(["sessions": payload] as JSObject)
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func queryWorkouts(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryRangeArgs.self)
        queries.workouts(start: date(fromMs: args.start), end: date(fromMs: args.end)) { result in
            switch result {
            case .success(let workouts):
                let payload = workouts.map { workout -> JSObject in
                    var obj: JSObject = [
                        "start": workout.start,
                        "end": workout.end,
                        "activityType": workout.activityType,
                        "rawActivityType": workout.rawActivityType,
                        "durationSec": workout.durationSec,
                    ]
                    if let calories = workout.calories { obj["calories"] = calories }
                    if let distance = workout.distanceMeters { obj["distanceMeters"] = distance }
                    if let source = workout.source { obj["source"] = source }
                    return obj
                }
                invoke.resolve(["workouts": payload] as JSObject)
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func queryHeartRateSamples(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryHeartRateSamplesArgs.self)
        queries.heartRateSamples(
            start: date(fromMs: args.start),
            end: date(fromMs: args.end),
            limit: args.limit ?? 1000
        ) { result in
            switch result {
            case .success(let samples):
                let payload = samples.map { sample -> JSObject in
                    var obj: JSObject = ["timestamp": sample.timestamp, "bpm": sample.bpm]
                    if let source = sample.source { obj["source"] = source }
                    return obj
                }
                invoke.resolve(["samples": payload] as JSObject)
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func openSettings(_ invoke: Invoke) {
        DispatchQueue.main.async {
            if let url = URL(string: "x-apple-health://") {
                UIApplication.shared.open(url)
            }
            invoke.resolve()
        }
    }
}

@_cdecl("init_plugin_health")
func initPlugin() -> Plugin {
    return HealthPlugin()
}
