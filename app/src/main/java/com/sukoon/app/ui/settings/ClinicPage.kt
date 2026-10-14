package com.sukoon.app.ui.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.clinic.AutoQuestion
import com.sukoon.app.clinic.Clinic
import com.sukoon.app.clinic.LabKind
import com.sukoon.app.clinic.LabResult
import com.sukoon.app.insights.Insight
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.platform.TimeFormat
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.SageDeep
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * You → Clinic visit: the next visit, what to ask (from Insights and labs that are due, plus your
 * own), and your lab results. The report for the doctor carries the questions and labs too.
 */
@Composable
fun ClinicPage(onOpenReports: () -> Unit) {
    val context = LocalContext.current
    val app = (context.applicationContext as com.sukoon.app.SukoonApp).container
    val visit by app.clinic.visit.collectAsState()
    val own by app.clinic.questions.collectAsState()
    val labs by app.clinic.labs.collectAsState()
    val insights by produceState(emptyList<Insight>()) { value = clinicInsights(app) }
    val today = LocalDate.now()
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withDecimalStyle(DecimalStyle.ofDefaultLocale())
    var adding by remember { mutableStateOf<LabKind?>(null) }
    var typed by remember { mutableStateOf("") }

    // The visit
    Card {
        Text(stringResource(R.string.clinic_visit), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            visit?.let { d ->
                val days = ChronoUnit.DAYS.between(today, d)
                stringResource(R.string.clinic_visit_on, d.format(dates)) + if (days >= 0) " · " + context.resources.getQuantityString(R.plurals.clinic_in_days, days.toInt(), days.toInt()) else ""
            } ?: stringResource(R.string.clinic_visit_none),
            fontSize = 14.sp, color = SageDeep, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                val d = visit ?: today.plusWeeks(2)
                DatePickerDialog(context, { _, y, m, day -> app.clinic.setVisit(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }.heightIn(min = 40.dp).padding(vertical = 10.dp),
        )
        Text(stringResource(R.string.clinic_visit_note), fontSize = 12.5.sp, color = CaptionMuted)
    }

    // Questions
    Label(stringResource(R.string.clinic_questions))
    val auto = remember(insights, labs) { Clinic.questions(insights, labs, today) }
    Card {
        if (auto.isEmpty() && own.isEmpty()) Text(stringResource(R.string.clinic_no_questions), fontSize = 13.sp, color = CaptionMuted)
        auto.forEach { q -> QuestionRow(questionText(q), stringResource(R.string.clinic_from_insights), null) }
        own.forEachIndexed { i, q -> QuestionRow(q, null) { app.clinic.setQuestions(own.filterIndexed { j, _ -> j != i }) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(typed, { typed = it }, label = { Text(stringResource(R.string.clinic_add_question)) }, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (typed.isNotBlank()) { app.clinic.setQuestions(own + typed.trim()); typed = "" } }) { Text(stringResource(R.string.clinic_add)) }
        }
    }

    // Labs
    Label(stringResource(R.string.clinic_labs))
    val latest = Clinic.latest(labs)
    val gmi = (insights.firstOrNull { it is Insight.Targets } as? Insight.Targets)?.gmiPercent
    Card {
        LabKind.entries.forEach { k ->
            val r = latest[k]
            val due = Clinic.due(k, labs, today)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { adding = k }.heightIn(min = 52.dp).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(labName(k)), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        r?.let { stringResource(R.string.clinic_lab_value, number(it.value), k.unit, it.date.format(dates)) } ?: stringResource(R.string.clinic_lab_none),
                        fontSize = 12.5.sp, color = CaptionMuted,
                    )
                    if (k == LabKind.HBA1C && gmi != null) Text(stringResource(R.string.clinic_gmi, number(gmi)), fontSize = 12.sp, color = CaptionMuted)
                    if (k == LabKind.CYSTATIN_C) Text(stringResource(R.string.clinic_cystatin_note), fontSize = 12.sp, color = CaptionMuted)
                }
                if (due) Text(stringResource(R.string.clinic_due), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PillHighText,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(PillHighBg).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    Text(
        stringResource(R.string.clinic_share), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenReports).heightIn(min = 44.dp).padding(vertical = 12.dp),
    )

    adding?.let { k ->
        var value by remember(k) { mutableStateOf("") }
        var date by remember(k) { mutableStateOf(today) }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(stringResource(labName(k))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value, { value = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(k.unit) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text(date.format(dates), fontSize = 14.sp, color = SageDeep, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                            DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                        }.heightIn(min = 40.dp).padding(vertical = 10.dp))
                    labs.filter { it.kind == k }.sortedByDescending { it.date }.take(4).forEach {
                        Text(stringResource(R.string.clinic_lab_value, number(it.value), k.unit, it.date.format(dates)), fontSize = 12.5.sp, color = CaptionMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    value.toDoubleOrNull()?.let { v -> app.clinic.setLabs(labs + LabResult(k, v, date)) }
                    adding = null
                }) { Text(stringResource(R.string.entry_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text(stringResource(R.string.sensor_cancel)) } },
        )
    }
}

/** Insights over the last 14 days (what the visit's questions come from). */
suspend fun clinicInsights(app: com.sukoon.app.di.AppContainer): List<Insight> = withContext(Dispatchers.Default) {
    runCatching {
        val since = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(14)
        InsightEngine.analyze(app.glucoseRepository.readingsSince(since).first(), app.logbookRepository.eventsSince(since).first(), java.time.Instant.now(), java.time.ZoneId.systemDefault())
    }.getOrDefault(emptyList())
}

private fun number(v: Double): String = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(v)

fun labName(k: LabKind): Int = when (k) {
    LabKind.HBA1C -> R.string.lab_hba1c
    LabKind.CREATININE -> R.string.lab_creatinine
    LabKind.CYSTATIN_C -> R.string.lab_cystatin
    LabKind.EGFR -> R.string.lab_egfr
    LabKind.UACR -> R.string.lab_uacr
    LabKind.LDL -> R.string.lab_ldl
}

@Composable
fun questionText(q: AutoQuestion): String = questionText(LocalContext.current, q)

/** A question in words, in the phone's language (the screen and the PDF both use it). */
fun questionText(context: android.content.Context, q: AutoQuestion): String {
    val clock = { h: Int -> LocalTime.of(h.coerceIn(0, 23), 0).format(TimeFormat.of()) }
    val meal = { s: com.sukoon.app.insights.MealSlot ->
        context.getString(
            when (s) {
                com.sukoon.app.insights.MealSlot.BREAKFAST -> R.string.meal_breakfast
                com.sukoon.app.insights.MealSlot.LUNCH -> R.string.meal_lunch
                com.sukoon.app.insights.MealSlot.DINNER -> R.string.meal_dinner
                com.sukoon.app.insights.MealSlot.LATE -> R.string.meal_late
            },
        ).lowercase()
    }
    return when (q.kind) {
        AutoQuestion.Kind.BASAL_UP -> context.getString(R.string.q_basal_up, q.a)
        AutoQuestion.Kind.BASAL_DOWN -> context.getString(R.string.q_basal_down, q.a)
        AutoQuestion.Kind.DAWN -> context.getString(R.string.q_dawn, q.a)
        AutoQuestion.Kind.LOWS_AT -> context.getString(R.string.q_lows_at, clock(q.a), clock(q.b))
        AutoQuestion.Kind.HIGHS_AT -> context.getString(R.string.q_highs_at, clock(q.a), clock(q.b))
        AutoQuestion.Kind.MEAL_HIGH -> context.getString(R.string.q_meal_high, meal(q.slot!!))
        AutoQuestion.Kind.MEAL_LOW -> context.getString(R.string.q_meal_low, meal(q.slot!!))
        AutoQuestion.Kind.REBOUNDS -> context.getString(R.string.q_rebounds)
        AutoQuestion.Kind.BASAL_HABITS -> context.getString(R.string.q_basal_habits)
        AutoQuestion.Kind.ACTIVITY_LOWS -> context.getString(R.string.q_activity_lows)
        AutoQuestion.Kind.STACKING -> context.getString(R.string.q_stacking)
        AutoQuestion.Kind.HBA1C_DUE -> context.getString(R.string.q_hba1c_due)
        AutoQuestion.Kind.KIDNEY_DUE -> context.getString(R.string.q_kidney_due)
        AutoQuestion.Kind.LIPIDS_DUE -> context.getString(R.string.q_lipids_due)
    }
}

@Composable
private fun QuestionRow(text: String, tag: String?, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
            tag?.let { Text(it, fontSize = 11.5.sp, color = CaptionMuted) }
        }
        onRemove?.let { TextButton(onClick = it) { Text(stringResource(R.string.supply_remove)) } }
    }
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, letterSpacing = 1.sp,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
)

@Composable
private fun Card(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, outline(), RoundedCornerShape(18.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) { content() }
