const COMMANDS: &[&str] = &[
    "is_available",
    "request_permissions",
    "check_permissions",
    "query_aggregated",
    "query_sleep",
    "query_workouts",
    "query_heart_rate_samples",
    "open_settings",
    // No register_listener/remove_listener: this plugin emits no events.
];

fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .ios_path("ios")
        .build();
}
