use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_health);

/// Initializes the Kotlin or Swift plugin classes registered by the host app.
pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<Health<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin("app.tauri.health", "HealthPlugin")?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_health)?;
    Ok(Health(handle))
}

/// Access to the health APIs on mobile.
pub struct Health<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> Health<R> {
    // Method names are camelCase to match the @objc / @Command methods on
    // the Swift / Kotlin sides.

    pub fn is_available(&self) -> crate::Result<Availability> {
        self.0
            .run_mobile_plugin("isAvailable", ())
            .map_err(Into::into)
    }

    pub fn request_permissions(
        &self,
        options: RequestPermissionsOptions,
    ) -> crate::Result<PermissionsResponse> {
        self.0
            .run_mobile_plugin("requestPermissions", options)
            .map_err(Into::into)
    }

    pub fn check_permissions(&self) -> crate::Result<PermissionsResponse> {
        self.0
            .run_mobile_plugin("checkPermissions", ())
            .map_err(Into::into)
    }
    #[cfg(feature = "activity")]
    pub fn query_aggregated(
        &self,
        options: QueryAggregatedOptions,
    ) -> crate::Result<QueryAggregatedResponse> {
        self.0
            .run_mobile_plugin("queryAggregated", options)
            .map_err(Into::into)
    }
    #[cfg(feature = "sleep")]
    pub fn query_sleep(&self, options: QueryRangeOptions) -> crate::Result<QuerySleepResponse> {
        self.0
            .run_mobile_plugin("querySleep", options)
            .map_err(Into::into)
    }
    #[cfg(feature = "workouts")]
    pub fn query_workouts(
        &self,
        options: QueryRangeOptions,
    ) -> crate::Result<QueryWorkoutsResponse> {
        self.0
            .run_mobile_plugin("queryWorkouts", options)
            .map_err(Into::into)
    }
    #[cfg(feature = "heart-rate")]
    pub fn query_heart_rate_samples(
        &self,
        options: QueryHeartRateSamplesOptions,
    ) -> crate::Result<QueryHeartRateSamplesResponse> {
        self.0
            .run_mobile_plugin("queryHeartRateSamples", options)
            .map_err(Into::into)
    }

    pub fn open_settings(&self) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("openSettings", ())
            .map_err(Into::into)
    }
}
