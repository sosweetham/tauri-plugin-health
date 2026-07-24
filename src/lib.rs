//! Read-only health data for Tauri 2 apps.
//!
//! - **iOS**: Apple HealthKit (`HKHealthStore`). Requires the consumer app
//!   to carry the `com.apple.developer.healthkit` entitlement and an
//!   `NSHealthShareUsageDescription` Info.plist string.
//! - **Android**: Health Connect (`androidx.health.connect`) — Google Fit's
//!   replacement, built into Android 14+. Requires
//!   `bundle.android.minSdkVersion: 26`.
//! - **Desktop**: every command rejects with `Unsupported`;
//!   `is_available` reports `{ available: false, platform: "unsupported" }`.

use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::Health;
#[cfg(mobile)]
use mobile::Health;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the health APIs.
pub trait HealthExt<R: Runtime> {
    fn health(&self) -> &Health<R>;
}

impl<R: Runtime, T: Manager<R>> crate::HealthExt<R> for T {
    fn health(&self) -> &Health<R> {
        self.state::<Health<R>>().inner()
    }
}

/// Initializes the plugin. Call this from your Tauri app's `lib.rs`:
///
/// ```ignore
/// .plugin(tauri_plugin_health::init())
/// ```
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("health")
        .invoke_handler(tauri::generate_handler![
            commands::is_available,
            commands::request_permissions,
            commands::check_permissions,
            commands::open_settings,
            #[cfg(feature = "activity")]
            commands::query_aggregated,
            #[cfg(feature = "sleep")]
            commands::query_sleep,
            #[cfg(feature = "workouts")]
            commands::query_workouts,
            #[cfg(feature = "heart-rate")]
            commands::query_heart_rate_samples,
        ])
        .setup(|app, api| {
            #[cfg(mobile)]
            let health = mobile::init(app, api)?;
            #[cfg(desktop)]
            let health = desktop::init(app, api)?;
            app.manage(health);
            Ok(())
        })
        .build()
}
