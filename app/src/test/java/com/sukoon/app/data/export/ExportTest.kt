package com.sukoon.app.data.export

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.data.source.SourceKind
import com.sukoon.app.data.source.TrendDirection
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTest {

    private val t = Instant.parse("2026-10-03T04:00:00Z")

    @Test
    fun `csv merges readings and logbook in time order with Kai's columns`() {
        val csv = CsvExport.build(
            readings = listOf(GlucoseReading(t.plusSeconds(600), 182, TrendDirection.RISING, SourceKind.LIBRE_BLE)),
            events = listOf(
                EventEntity(timestampMillis = t.plusSeconds(300).toEpochMilli(), type = "CARB", value = 60.0, note = "koshari, large"),
                EventEntity(timestampMillis = t.toEpochMilli(), type = "INSULIN", value = 6.0),
                EventEntity(timestampMillis = t.plusSeconds(900).toEpochMilli(), type = "BASAL", value = 18.0),
            ),
            zone = ZoneOffset.ofHours(3),
        )
        assertEquals(
            listOf(
                CsvExport.HEADER,
                "2026-10-03T07:00:00+03:00,insulin,,6,rapid,,",
                "2026-10-03T07:05:00+03:00,meal,60,,,,\"koshari, large\"",
                "2026-10-03T07:10:00+03:00,glucose,,,,182,",
                "2026-10-03T07:15:00+03:00,insulin,,18,basal,,",
            ),
            csv.trimEnd().lines(),
        )
    }

    @Test
    fun `finger-pricks export as fingerstick rows and nightscout BG checks`() {
        val prick = EventEntity(timestampMillis = t.toEpochMilli(), type = "FINGERSTICK", value = 112.0)
        assertEquals("2026-10-03T04:00:00Z,fingerstick,,,,112,", CsvExport.build(emptyList(), listOf(prick), ZoneOffset.UTC).trimEnd().lines()[1])
        val json = NightscoutUploader.treatmentJson(prick)
        assertEquals("BG Check", json.getString("eventType"))
        assertEquals(112.0, json.getDouble("glucose"), 0.0)
        assertEquals("Finger", json.getString("glucoseType"))
    }

    @Test
    fun `csv escaping follows RFC 4180`() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
    }

    @Test
    fun `nightscout payloads use its field names and the sha1 of the secret`() {
        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", NightscoutUploader.sha1("test"))
        val entry = NightscoutUploader.entryJson(GlucoseReading(t, 140, TrendDirection.FALLING, SourceKind.LIBRE_BLE))
        assertEquals("sgv", entry.getString("type"))
        assertEquals(140, entry.getInt("sgv"))
        assertEquals(t.toEpochMilli(), entry.getLong("date"))
        assertEquals("FortyFiveDown", entry.getString("direction"))
        val meal = NightscoutUploader.treatmentJson(EventEntity(timestampMillis = t.toEpochMilli(), type = "CARB", value = 45.0))
        assertEquals("Carb Correction", meal.getString("eventType"))
        assertEquals(45.0, meal.getDouble("carbs"), 0.0)
    }
}
