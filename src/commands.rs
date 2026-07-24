use tauri::{command, AppHandle, Runtime};

use crate::models::*;
#[allow(unused_imports)]
use crate::Error;
use crate::HealthExt;
use crate::Result;

#[command]
pub(crate) async fn is_available<R: Runtime>(app: AppHandle<R>) -> Result<Availability> {
    app.health().is_available()
}

#[command]
pub(crate) async fn request_permissions<R: Runtime>(
    app: AppHandle<R>,
    options: RequestPermissionsOptions,
) -> Result<PermissionsResponse> {
    app.health().request_permissions(options)
}

#[command]
pub(crate) async fn check_permissions<R: Runtime>(app: AppHandle<R>) -> Result<PermissionsResponse> {
    app.health().check_permissions()
}

#[cfg(any(
    feature = "steps",
    feature = "distance",
    feature = "active-calories",
    feature = "total-calories",
    feature = "resting-heart-rate",
    feature = "hrv"
))]
#[command]
pub(crate) async fn query_aggregated<R: Runtime>(
    app: AppHandle<R>,
    options: QueryAggregatedOptions,
) -> Result<QueryAggregatedResponse> {
    // Shared validation: keep non-aggregatable metrics from crossing the
    // native bridge on any platform.
    if !options.metric.is_aggregatable() {
        return Err(Error::InvalidArgs(format!(
            "{:?} is not an aggregatable metric — use querySleep/queryWorkouts",
            options.metric
        )));
    }
    app.health().query_aggregated(options)
}

#[cfg(feature = "sleep")]
#[command]
pub(crate) async fn query_sleep<R: Runtime>(
    app: AppHandle<R>,
    options: QueryRangeOptions,
) -> Result<QuerySleepResponse> {
    app.health().query_sleep(options)
}

#[cfg(feature = "workouts")]
#[command]
pub(crate) async fn query_workouts<R: Runtime>(
    app: AppHandle<R>,
    options: QueryRangeOptions,
) -> Result<QueryWorkoutsResponse> {
    app.health().query_workouts(options)
}

#[cfg(feature = "heart-rate")]
#[command]
pub(crate) async fn query_heart_rate_samples<R: Runtime>(
    app: AppHandle<R>,
    options: QueryHeartRateSamplesOptions,
) -> Result<QueryHeartRateSamplesResponse> {
    app.health().query_heart_rate_samples(options)
}

#[command]
pub(crate) async fn open_settings<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.health().open_settings()
}
