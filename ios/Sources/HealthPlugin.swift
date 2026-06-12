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
//  Args and responses use the typeshare-generated wire types
//  (HealthTypes.generated.swift); responses go through Invoke's Encodable
//  overload, so payload shapes are compiler-checked against models.rs.
//

import Foundation
import HealthKit
import Tauri
import UIKit

class HealthPlugin: Plugin {
    private let queries = HealthQueries()

    private func date(fromMs ms: Int64) -> Date {
        Date(timeIntervalSince1970: Double(ms) / 1000)
    }

    @objc public func isAvailable(_ invoke: Invoke) {
        let available = HKHealthStore.isHealthDataAvailable()
        invoke.resolve(
            Availability(
                available: available,
                platform: .ios,
                reason: available ? nil : .healthDataUnavailable))
    }

    @objc public override func requestPermissions(_ invoke: Invoke) {
        let args: RequestPermissionsOptions
        do {
            args = try invoke.parseArgs(RequestPermissionsOptions.self)
        } catch {
            invoke.reject(error.localizedDescription)
            return
        }
        guard HKHealthStore.isHealthDataAvailable() else {
            invoke.reject("health data is not available on this device")
            return
        }
        var types = Set<HKObjectType>()
        for metric in args.read {
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
            invoke.resolve(PermissionsResponse(granted: args.read, state: .unknown))
        }
    }

    @objc public override func checkPermissions(_ invoke: Invoke) {
        // Read grants are unknowable on iOS by design; report everything as
        // potentially granted with state "unknown".
        invoke.resolve(PermissionsResponse(granted: Array(Metric.allCases), state: .unknown))
    }

    @objc public func queryAggregated(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryAggregatedOptions.self)
        queries.aggregate(
            metric: args.metric,
            start: date(fromMs: args.start),
            end: date(fromMs: args.end),
            hourly: args.bucket == .hour
        ) { result in
            switch result {
            case .success(let buckets):
                invoke.resolve(QueryAggregatedResponse(buckets: buckets))
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func querySleep(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryRangeOptions.self)
        queries.sleepSessions(start: date(fromMs: args.start), end: date(fromMs: args.end)) {
            result in
            switch result {
            case .success(let sessions):
                invoke.resolve(QuerySleepResponse(sessions: sessions))
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func queryWorkouts(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryRangeOptions.self)
        queries.workouts(start: date(fromMs: args.start), end: date(fromMs: args.end)) { result in
            switch result {
            case .success(let workouts):
                invoke.resolve(QueryWorkoutsResponse(workouts: workouts))
            case .failure(let error):
                invoke.reject(error.localizedDescription)
            }
        }
    }

    @objc public func queryHeartRateSamples(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(QueryHeartRateSamplesOptions.self)
        queries.heartRateSamples(
            start: date(fromMs: args.start),
            end: date(fromMs: args.end),
            limit: Int(args.limit ?? 1000)
        ) { result in
            switch result {
            case .success(let samples):
                invoke.resolve(QueryHeartRateSamplesResponse(samples: samples))
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
