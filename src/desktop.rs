use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;
use crate::Error;

const UNSUPPORTED: &str = "health data is only available on iOS and Android";

pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<Health<R>> {
    Ok(Health(std::marker::PhantomData))
}

/// Desktop stub — HealthKit does not exist on macOS and there is no
/// desktop equivalent of Health Connect, so every command rejects with
/// `Unsupported` except `is_available`, which reports gracefully.
// fn() -> R keeps Health Send+Sync regardless of R's auto-traits.
pub struct Health<R: Runtime>(std::marker::PhantomData<fn() -> R>);

impl<R: Runtime> Health<R> {
    pub fn is_available(&self) -> crate::Result<Availability> {
        Ok(Availability {
            available: false,
            platform: HealthPlatform::Unsupported,
            reason: Some(AvailabilityReason::UnsupportedPlatform),
        })
    }

    pub fn request_permissions(
        &self,
        _options: RequestPermissionsOptions,
    ) -> crate::Result<PermissionsResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }

    pub fn check_permissions(&self) -> crate::Result<PermissionsResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }
    #[cfg(any(
        feature = "steps",
        feature = "distance",
        feature = "active-calories",
        feature = "total-calories",
        feature = "resting-heart-rate",
        feature = "hrv"
    ))]
    pub fn query_aggregated(
        &self,
        _options: QueryAggregatedOptions,
    ) -> crate::Result<QueryAggregatedResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }
    #[cfg(feature = "sleep")]
    pub fn query_sleep(&self, _options: QueryRangeOptions) -> crate::Result<QuerySleepResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }
    #[cfg(feature = "workouts")]
    pub fn query_workouts(
        &self,
        _options: QueryRangeOptions,
    ) -> crate::Result<QueryWorkoutsResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }
    #[cfg(feature = "heart-rate")]
    pub fn query_heart_rate_samples(
        &self,
        _options: QueryHeartRateSamplesOptions,
    ) -> crate::Result<QueryHeartRateSamplesResponse> {
        Err(Error::Unsupported(UNSUPPORTED))
    }

    pub fn open_settings(&self) -> crate::Result<()> {
        Err(Error::Unsupported(UNSUPPORTED))
    }
}
