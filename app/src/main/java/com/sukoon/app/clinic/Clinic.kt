package com.sukoon.app.clinic

import android.content.Context
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.Level
import com.sukoon.app.insights.MealSlot
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lab tests worth tracking, with how often they're due (ADA Standards of Care 2026: HbA1c every
 * 3 months when not at goal, twice a year when it is; eGFR and urine albumin at least yearly
 * (section 11); lipids yearly). ponytail: one interval each, the stricter one for HbA1c.
 */
enum class LabKind(val unit: String, val dueDays: Long) {
    HBA1C("%", 90),
    CREATININE("mg/dL", 365),
    CYSTATIN_C("mg/L", 365),
    EGFR("mL/min/1.73m²", 365),
    UACR("mg/g", 365),
    LDL("mg/dL", 365),
}

data class LabResult(val kind: LabKind, val value: Double, val date: LocalDate)

/** A question for the visit, from Insights or from a lab that's due; the screen words it. */
data class AutoQuestion(val kind: Kind, val a: Int = 0, val b: Int = 0, val slot: MealSlot? = null) {
    enum class Kind { BASAL_UP, BASAL_DOWN, DAWN, LOWS_AT, HIGHS_AT, MEAL_HIGH, MEAL_LOW, REBOUNDS, BASAL_HABITS, ACTIVITY_LOWS, STACKING, HBA1C_DUE, KIDNEY_DUE, LIPIDS_DUE }
}

object Clinic {
    /** The latest result of each kind. */
    fun latest(labs: List<LabResult>): Map<LabKind, LabResult> = labs.groupBy { it.kind }.mapValues { (_, r) -> r.maxBy { it.date } }

    /** Due: never done, or its interval has passed. */
    fun due(kind: LabKind, labs: List<LabResult>, today: LocalDate): Boolean =
        latest(labs)[kind]?.let { ChronoUnit.DAYS.between(it.date, today) >= kind.dueDays } ?: true

    /** What's worth asking: the insights a doctor adjusts doses from (attention or worse), then labs due. */
    fun questions(insights: List<Insight>, labs: List<LabResult>, today: LocalDate): List<AutoQuestion> {
        val out = mutableListOf<AutoQuestion>()
        insights.forEach { i ->
            when (i) {
                is Insight.BasalDrift -> if (i.level == Level.ATTENTION) out += AutoQuestion(if (i.medianDrift > 0) AutoQuestion.Kind.BASAL_UP else AutoQuestion.Kind.BASAL_DOWN, kotlin.math.abs(i.medianDrift))
                is Insight.DawnRise -> out += AutoQuestion(AutoQuestion.Kind.DAWN, i.medianRise)
                is Insight.RecurringLows -> out += AutoQuestion(AutoQuestion.Kind.LOWS_AT, i.fromHour, i.toHour)
                is Insight.RecurringHighs -> out += AutoQuestion(AutoQuestion.Kind.HIGHS_AT, i.fromHour, i.toHour)
                is Insight.MealOutcomes -> when {
                    i.lowWithin4h >= 2 -> out += AutoQuestion(AutoQuestion.Kind.MEAL_LOW, slot = i.slot)
                    i.level == Level.ATTENTION -> out += AutoQuestion(AutoQuestion.Kind.MEAL_HIGH, slot = i.slot)
                    else -> Unit
                }
                is Insight.Rebounds -> out += AutoQuestion(AutoQuestion.Kind.REBOUNDS)
                is Insight.BasalHabits -> if (i.level == Level.ATTENTION) out += AutoQuestion(AutoQuestion.Kind.BASAL_HABITS)
                is Insight.ActivityLows -> out += AutoQuestion(AutoQuestion.Kind.ACTIVITY_LOWS)
                is Insight.Stacking -> out += AutoQuestion(AutoQuestion.Kind.STACKING)
                else -> Unit
            }
        }
        if (due(LabKind.HBA1C, labs, today)) out += AutoQuestion(AutoQuestion.Kind.HBA1C_DUE)
        if (due(LabKind.EGFR, labs, today) || due(LabKind.UACR, labs, today)) out += AutoQuestion(AutoQuestion.Kind.KIDNEY_DUE)
        if (due(LabKind.LDL, labs, today)) out += AutoQuestion(AutoQuestion.Kind.LIPIDS_DUE)
        return out.distinct()
    }
}

/** The next visit, your own questions and your lab results, on this phone. */
class ClinicStore(context: Context) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    private val _visit = MutableStateFlow(prefs.getLong(K_VISIT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay))
    val visit: StateFlow<LocalDate?> = _visit.asStateFlow()

    fun setVisit(d: LocalDate?) {
        prefs.edit().apply { if (d == null) remove(K_VISIT) else putLong(K_VISIT, d.toEpochDay()) }.apply()
        _visit.value = d
    }

    private val _questions = MutableStateFlow(runCatching { JSONArray(prefs.getString(K_QUESTIONS, "[]")).let { a -> (0 until a.length()).map(a::getString) } }.getOrDefault(emptyList()))
    val questions: StateFlow<List<String>> = _questions.asStateFlow()

    fun setQuestions(q: List<String>) {
        prefs.edit().putString(K_QUESTIONS, JSONArray(q).toString()).apply()
        _questions.value = q
    }

    private val _labs = MutableStateFlow(readLabs())
    val labs: StateFlow<List<LabResult>> = _labs.asStateFlow()

    private fun readLabs(): List<LabResult> = runCatching {
        JSONArray(prefs.getString(K_LABS, "[]")).let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val kind = LabKind.entries.firstOrNull { it.name == o.optString("kind") } ?: return@mapNotNull null
                LabResult(kind, o.getDouble("value"), LocalDate.parse(o.getString("date")))
            }
        }
    }.getOrDefault(emptyList())

    fun setLabs(l: List<LabResult>) {
        prefs.edit().putString(K_LABS, JSONArray(l.map { JSONObject().put("kind", it.kind.name).put("value", it.value).put("date", it.date.toString()) }).toString()).apply()
        _labs.value = l
    }

    private companion object {
        const val K_VISIT = "clinic_visit"
        const val K_QUESTIONS = "clinic_questions"
        const val K_LABS = "clinic_labs"
    }
}
