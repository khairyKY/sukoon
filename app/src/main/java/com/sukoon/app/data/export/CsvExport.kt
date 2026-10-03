package com.sukoon.app.data.export

import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.data.db.logType
import com.sukoon.app.data.source.GlucoseReading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Readings + logbook as one CSV, in Kai's own column layout (docs/data/glucose-log.csv):
 * `timestamp,type,carbs_g,insulin_units,insulin_type,glucose_mgdl,notes`, oldest first. Times are
 * ISO-8601 with the UTC offset so spreadsheets and scripts both read them unambiguously.
 */
object CsvExport {

    const val HEADER = "timestamp,type,carbs_g,insulin_units,insulin_type,glucose_mgdl,notes"

    fun build(readings: List<GlucoseReading>, events: List<EventEntity>, zone: ZoneId): String {
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone)
        val rows = readings.map { r ->
            r.timestamp to row(time.format(r.timestamp), "glucose", glucose = r.glucoseMgDl.toString())
        } + events.map { e ->
            val at = Instant.ofEpochMilli(e.timestampMillis)
            val ts = time.format(at)
            val amount = e.value?.let(::number).orEmpty()
            at to when (e.logType) {
                LogEventType.CARB -> row(ts, "meal", carbs = amount, notes = e.note)
                LogEventType.INSULIN -> row(ts, "insulin", insulin = amount, insulinType = "rapid", notes = e.note)
                LogEventType.BASAL -> row(ts, "insulin", insulin = amount, insulinType = "basal", notes = e.note)
                LogEventType.FINGERSTICK -> row(ts, "fingerstick", glucose = amount, notes = e.note)
                LogEventType.ACTIVITY -> row(ts, "activity", notes = listOfNotNull(e.value?.let { "${number(it)} min" }, e.note).joinToString(" · "))
                LogEventType.NOTE -> row(ts, "note", notes = e.note)
            }
        }
        return (listOf(HEADER) + rows.sortedBy { it.first }.map { it.second }).joinToString("\n", postfix = "\n")
    }

    private fun row(ts: String, type: String, carbs: String = "", insulin: String = "", insulinType: String = "", glucose: String = "", notes: String? = null) =
        listOf(ts, type, carbs, insulin, insulinType, glucose, escape(notes.orEmpty())).joinToString(",")

    private fun number(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** RFC 4180: quote fields holding a comma, quote or newline; double any quotes inside. */
    internal fun escape(field: String) =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
