// Shown when the user taps the privacy-policy link in Health Connect's
// permission sheet (mandated by Health Connect). Displays overridable
// string resources and, when the consumer app declares an
// app.tauri.health.PRIVACY_POLICY_URL meta-data, a button opening it.

package app.tauri.health

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        layout.addView(TextView(this).apply {
            text = getString(R.string.tauri_health_rationale_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, 0, padding / 2)
        })
        layout.addView(TextView(this).apply {
            text = getString(R.string.tauri_health_rationale_text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })

        privacyPolicyUrl()?.let { url ->
            layout.addView(Button(this).apply {
                text = getString(R.string.tauri_health_rationale_policy_button)
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
        }

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun privacyPolicyUrl(): String? = try {
        packageManager
            .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString("app.tauri.health.PRIVACY_POLICY_URL")
    } catch (e: Exception) {
        null
    }
}
