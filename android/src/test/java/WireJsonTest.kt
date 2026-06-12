// Round-trip tests pinning WireJson's wire format to what serde expects on
// the Rust side (src/models.rs): enums by their @SerialName/`string` value,
// camelCase keys, and absent (not null) optional fields.

package app.tauri.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WireJsonTest {
    private fun tree(json: String) = WireJson.mapper.readTree(json)

    @Test
    fun `enums serialize as wire names`() {
        val json = WireJson.stringify(
            PermissionsResponse(
                granted = listOf(Metric.Steps, Metric.HeartRateVariability),
                state = PermissionAccuracy.Exact,
            ),
        )
        assertEquals(
            tree("""{"granted":["steps","heartRateVariability"],"state":"exact"}"""),
            tree(json),
        )
    }

    @Test
    fun `none optionals are omitted not null`() {
        val json = WireJson.stringify(
            AggregatedBucket(start = 1L, end = 2L, value = 3.5, unit = HealthUnit.Kcal),
        )
        assertEquals(tree("""{"start":1,"end":2,"value":3.5,"unit":"kcal"}"""), tree(json))
        assertFalse(tree(json).has("min"))
    }

    @Test
    fun `workout payload matches serde shape`() {
        val json = WireJson.stringify(
            Workout(
                start = 10L,
                end = 20L,
                activityType = ActivityType.CrossTraining,
                rawActivityType = 37L,
                durationSec = 600.0,
                calories = 12.5,
                source = "fit",
            ),
        )
        assertEquals(
            tree(
                """{"start":10,"end":20,"activityType":"crossTraining","rawActivityType":37,
                   "durationSec":600.0,"calories":12.5,"source":"fit"}""",
            ),
            tree(json),
        )
    }

    @Test
    fun `availability reason round-trips`() {
        val json = WireJson.stringify(
            Availability(false, HealthPlatform.Android, AvailabilityReason.ProviderUpdateRequired),
        )
        assertEquals(
            tree("""{"available":false,"platform":"android","reason":"providerUpdateRequired"}"""),
            tree(json),
        )
    }

    @Test
    fun `args parse with enum wire names`() {
        val args = WireJson.parse<QueryAggregatedOptions>(
            """{"metric":"restingHeartRate","start":100,"end":200,"bucket":"day"}""",
        )
        assertEquals(Metric.RestingHeartRate, args.metric)
        assertEquals(Bucket.Day, args.bucket)
        assertEquals(100L, args.start)
    }

    @Test
    fun `limit parses present and absent`() {
        val with = WireJson.parse<QueryHeartRateSamplesOptions>(
            """{"start":1,"end":2,"limit":50}""",
        )
        assertEquals(50L, with.limit)
        val without = WireJson.parse<QueryHeartRateSamplesOptions>("""{"start":1,"end":2}""")
        assertNull(without.limit)
    }

    @Test
    fun `sleep response nests stages`() {
        val json = WireJson.stringify(
            QuerySleepResponse(
                listOf(
                    SleepSession(
                        start = 1L, end = 9L, source = "pkg",
                        stages = listOf(SleepStageSample(SleepStage.OutOfBed, 1L, 2L)),
                    ),
                ),
            ),
        )
        assertEquals(
            tree(
                """{"sessions":[{"start":1,"end":9,"source":"pkg",
                   "stages":[{"stage":"outOfBed","start":1,"end":2}]}]}""",
            ),
            tree(json),
        )
    }
}
