package com.sukoon.app.ui.logbook

import java.time.ZonedDateTime
import java.time.ZoneId
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.insulin.injectionSite
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import android.text.format.DateFormat
import android.app.TimePickerDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.sukoon.app.health.HealthConnectSync
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.health.connect.client.records.MealType
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.StateHigh
import com.sukoon.app.ui.theme.StateLow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ColorFilter

/** An imported entry's app as this phone has it: its name and its own icon (no copy ships in Sukoon). */
internal class SourceApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val installed: Boolean = icon != null,
    /** Just the logo (the icon's monochrome or foreground layer), drawn in the theme's ink instead of the app's colours. */
    val glyph: ImageBitmap? = null,
)

private val sourceApps = mutableMapOf<String, SourceApp>()
private val KNOWN_APPS = mapOf(
    "com.myfitnesspal.android" to "MyFitnessPal",
    "com.samsung.android.app.health" to "Samsung Health",
    "com.google.android.apps.fitness" to "Google Fit",
)

@Composable
internal fun rememberSourceApp(packageName: String): SourceApp {
    val pm = LocalContext.current.packageManager
    return sourceApps.getOrPut(packageName) {
        val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        SourceApp(
            packageName = packageName,
            label = info?.let { pm.getApplicationLabel(it).toString() } ?: KNOWN_APPS[packageName] ?: packageName,
            icon = info?.let { runCatching { pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }.getOrNull() },
            installed = info != null,
            glyph = info?.let { runCatching { glyphOf(pm.getApplicationIcon(it)) }.getOrNull() },
        )
    }
}

/**
 * The logo alone from an adaptive icon: its themed-icon (monochrome) layer, else its foreground, cut
 * to the visible middle. Null when there's no such layer, or the "logo" fills the square (it isn't one).
 */
private fun glyphOf(icon: Drawable): ImageBitmap? {
    if (icon !is AdaptiveIconDrawable) return null
    val layer = (if (Build.VERSION.SDK_INT >= 33) icon.monochrome else null) ?: icon.foreground ?: return null
    val px = 96
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    // Layers are 108 dp with the visible icon the middle 72: draw them a quarter larger on each side.
    layer.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4)
    layer.draw(Canvas(bitmap))
    val pixels = IntArray(px * px).also { bitmap.getPixels(it, 0, px, 0, 0, px, px) }
    val solid = pixels.count { (it ushr 24) > 200 }
    return if (solid == 0 || solid > pixels.size * 0.8) null else bitmap.asImageBitmap()
}

@Composable
internal fun AppIcon(app: SourceApp, size: Dp) {
    val glyph = app.glyph
    if (glyph != null) {
        Image(glyph, contentDescription = app.label, modifier = Modifier.size(size), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground))
        return
    }
    val icon = app.icon
    if (icon != null) {
        Image(icon, contentDescription = app.label, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)))
    }
}

/** "[icon] MyFitnessPal · 12:40 · Koshari, laban". */
@Composable
internal fun SourceLine(app: SourceApp, details: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(app, 16.dp)
        Text((listOf(app.label) + details).joinToString(" · "), fontSize = 12.5.sp, color = CaptionMuted, maxLines = 1)
    }
}

/** A meal's name: its meal type when an app gave one, else what it was called, else "Meal". */
@Composable
internal fun mealTitle(meal: EventEntity): String = when (meal.mealType) {
    MealType.MEAL_TYPE_BREAKFAST -> stringResource(R.string.hc_meal_breakfast)
    MealType.MEAL_TYPE_LUNCH -> stringResource(R.string.hc_meal_lunch)
    MealType.MEAL_TYPE_DINNER -> stringResource(R.string.hc_meal_dinner)
    MealType.MEAL_TYPE_SNACK -> stringResource(R.string.hc_meal_snack)
    else -> meal.note ?: stringResource(R.string.logbook_type_carb)
}

/** Carbs first and filled, then whatever else the meal carries. */
@Composable
internal fun NutrientGrid(meal: EventEntity) {
    val grams = stringResource(R.string.logbook_unit_grams)
    val cells = listOfNotNull(
        meal.value?.let { Triple(stringResource(R.string.entry_carbs), it, grams) },
        meal.fiber?.let { Triple(stringResource(R.string.entry_fiber), it, grams) },
        meal.sugar?.let { Triple(stringResource(R.string.entry_sugar), it, grams) },
        meal.protein?.let { Triple(stringResource(R.string.entry_protein), it, grams) },
        meal.fat?.let { Triple(stringResource(R.string.entry_fat), it, grams) },
        meal.kcal?.let { Triple(stringResource(R.string.entry_energy), Math.round(it).toDouble(), stringResource(R.string.entry_unit_kcal)) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { i, (label, amount, unit) ->
                    val lead = row == 0 && i == 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (lead) Modifier.background(Sage) else Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(14.dp)))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (lead) Color.White else CaptionMuted, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(formatAmountLocalized(amount), fontFamily = HeadlineSerifFontFamily, fontSize = 23.sp, maxLines = 1, softWrap = false, color = if (lead) Color.White else MaterialTheme.colorScheme.onBackground)
                            Text(unit.trim(), fontSize = 12.sp, color = if (lead) Color.White else CaptionMuted, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                }
                repeat(3 - chunk.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A meal another app logged (design "MyFitnessPal meal"): what it brought, what it did to your
 * glucose, the insulin taken for it, and the way back to the app that owns it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealDetailSheet(
    meal: EventEntity,
    insulin: List<EventEntity>,
    readings: List<GlucoseReading>,
    onAddInsulin: () -> Unit,
    onEditInsulin: (EventEntity) -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
    /** The Health Connect record behind it: its own time and every nutrient the other app sent. */
    loadRecord: suspend () -> HealthConnectSync.MealRecord? = { null },
    /** "When did you eat?": the time you picked; kept when the other app sends the meal again. */
    onSetTime: (Instant) -> Unit = {},
) {
    val context = LocalContext.current
    val app = rememberSourceApp(meal.source.orEmpty())
    val record by produceState<HealthConnectSync.MealRecord?>(null, meal.id) { value = loadRecord() }
    val mealAt = Instant.ofEpochMilli(meal.timestampMillis)
    val now = Instant.now()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mealTitle(meal), fontFamily = HeadlineSerifFontFamily, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
                SourceLine(app, emptyList())
            }
            EatenAt(meal, app.label, onSetTime)
            DoseCard(meal, insulin, onAddInsulin, onEditInsulin)
            if (slowMeal(meal.fat, meal.protein)) {
                Text(
                    stringResource(R.string.meal_detail_rich),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(PillHighBg).padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PillHighText,
                )
            }
            Card {
                Eyebrow(stringResource(R.string.meal_detail_what))
                ResponseChart(readings, mealAt, now)
                Text(responseText(mealResponse(readings, mealAt, now)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            WhatsInIt(meal, record)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.meal_detail_foods_stay, app.label), fontSize = 13.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
                    Text(
                        stringResource(R.string.meal_detail_open, app.label),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SageDeep,
                    )
                }
            }
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.meal_detail_hide), color = StateLow, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The meal's time, stated honestly: when it was logged in the other app (which can be later than you
 * ate), so "When did you eat?" until you've said; then "Ate at 13:50 · Change".
 */
@Composable
private fun EatenAt(meal: EventEntity, appName: String, onSetTime: (Instant) -> Unit) {
    val context = LocalContext.current
    val at = Instant.ofEpochMilli(meal.timestampMillis)
    fun pick() {
        val start = at.atZone(ZoneId.systemDefault())
        TimePickerDialog(context, { _, hour, minute ->
            val picked = start.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            val now = ZonedDateTime.now()
            onSetTime((if (picked.isAfter(now)) picked.minusDays(1) else picked).toInstant()) // later than now: the day before
        }, start.hour, start.minute, DateFormat.is24HourFormat(context)).show()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
            .clickable(onClick = ::pick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                if (meal.timeSet) {
                    withStyle(SpanStyle(color = CaptionMuted)) { append(stringResource(R.string.meal_detail_ate_at) + " ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(hmFormatter.format(at)) }
                } else {
                    withStyle(SpanStyle(color = CaptionMuted)) { append(stringResource(R.string.meal_detail_app_says, appName) + " ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(hmFormatter.format(at)) }
                }
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        Text(stringResource(if (meal.timeSet) R.string.apps_change else R.string.meal_detail_when), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}

/** What you dose from: the carbs and the insulin taken for them (or that none was), and each dose. */
@Composable
private fun DoseCard(meal: EventEntity, insulin: List<EventEntity>, onAdd: () -> Unit, onEdit: (EventEntity) -> Unit) {
    val carbs = meal.value ?: 0.0
    val units = insulin.sumOf { it.value ?: 0.0 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (insulin.isEmpty()) 1.5.dp else 1.dp, if (insulin.isEmpty()) Sage else outline(), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigAmount(stringResource(R.string.entry_carbs), formatAmountLocalized(carbs), stringResource(R.string.logbook_unit_grams).trim())
            if (insulin.isNotEmpty()) {
                BigAmount(stringResource(R.string.meal_detail_insulin), formatAmountLocalized(units), stringResource(R.string.logbook_unit_units).trim())
                Text(
                    if (units > 0 && carbs > 0) stringResource(R.string.meal_detail_ratio, formatAmountLocalized(Math.round(carbs / units * 10) / 10.0)) else "",
                    fontSize = 13.sp,
                    color = CaptionMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.logbook_add_insulin),
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(Sage).clickable(onClick = onAdd).padding(horizontal = 18.dp, vertical = 14.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
        if (insulin.isEmpty()) {
            Text(stringResource(R.string.meal_detail_no_insulin_yet), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PillHighText, modifier = Modifier.padding(top = 2.dp))
            return@Column
        }
        HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        insulin.forEach { dose ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(dose) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground))
                Text(
                    listOfNotNull(hmFormatter.format(Instant.ofEpochMilli(dose.timestampMillis)), doseTiming(dose, meal), dose.injectionSite?.let { siteName(it) }).joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(formatAmountLocalized(dose.value ?: 0.0) + stringResource(R.string.logbook_unit_units), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Text(
            stringResource(R.string.meal_detail_add_more),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAdd).padding(vertical = 8.dp),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = SageDeep,
        )
    }
}

@Composable
private fun BigAmount(label: String, value: String, unit: String) {
    Column {
        Eyebrow(label)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 40.sp, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onBackground)
            Text(unit, fontSize = 15.sp, color = CaptionMuted, maxLines = 1, modifier = Modifier.padding(bottom = 7.dp))
        }
    }
}

/** One list of everything in it: carbs (fibre and sugar under it), protein, fat (its kinds under it), calories, then the rest the app sent. */
@Composable
private fun WhatsInIt(meal: EventEntity, record: HealthConnectSync.MealRecord?) {
    val g = stringResource(R.string.logbook_unit_grams).trim()
    val mg = stringResource(R.string.meal_detail_mg)
    val extra = record?.nutrients.orEmpty().toMap()
    fun amount(x: Double, unit: String) = formatAmountLocalized(if (x >= 10) Math.round(x).toDouble() else Math.round(x * 10) / 10.0) + " " + unit
    val fats = listOf(HealthConnectSync.Nutrient.SATURATED_FAT, HealthConnectSync.Nutrient.TRANS_FAT, HealthConnectSync.Nutrient.MONO_FAT, HealthConnectSync.Nutrient.POLY_FAT)
    Card {
        Eyebrow(stringResource(R.string.meal_detail_whats_in))
        meal.value?.let { NutrientRow(stringResource(R.string.entry_carbs), amount(it, g), strong = true) }
        meal.fiber?.let { NutrientRow(stringResource(R.string.entry_fiber), amount(it, g), sub = true) }
        meal.sugar?.let { NutrientRow(stringResource(R.string.entry_sugar), amount(it, g), sub = true) }
        meal.protein?.let { NutrientRow(stringResource(R.string.entry_protein), amount(it, g)) }
        meal.fat?.let { NutrientRow(stringResource(R.string.entry_fat), amount(it, g)) }
        fats.forEach { n -> extra[n]?.let { NutrientRow(stringResource(n.labelRes), amount(it, g), sub = true) } }
        meal.kcal?.let { NutrientRow(stringResource(R.string.entry_energy), String.format(Locale.getDefault(), "%,d", Math.round(it)) + " " + stringResource(R.string.entry_unit_kcal)) }
        extra.filterKeys { it !in fats }.forEach { (n, v) -> NutrientRow(stringResource(n.labelRes), amount(v, if (n.milligrams) mg else g)) }
    }
}

@Composable
private fun NutrientRow(label: String, value: String, strong: Boolean = false, sub: Boolean = false) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
    Row(Modifier.fillMaxWidth().padding(start = if (sub) 14.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (sub) 13.5.sp else 14.5.sp, color = if (sub) CaptionMuted else MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.5.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** "With the meal", "10 min before", "15 min after". */
@Composable
internal fun doseTiming(dose: EventEntity, meal: EventEntity): String {
    val minutes = ((dose.timestampMillis - meal.timestampMillis) / 60_000).toInt()
    return when {
        kotlin.math.abs(minutes) < 3 -> stringResource(R.string.logbook_prebolus_with)
        minutes < 0 -> stringResource(R.string.logbook_prebolus_min, -minutes)
        else -> stringResource(R.string.entry_dose_after, minutes)
    }
}

@Composable
private fun responseText(response: MealResponse?): String = when {
    response == null -> stringResource(R.string.meal_detail_no_data)
    response.stillRising -> stringResource(R.string.meal_detail_rising, response.peak)
    response.peak > 180 -> listOfNotNull(
        stringResource(R.string.meal_detail_rose, response.start, response.peak, hmFormatter.format(response.peakAt)),
        response.backInRangeAt?.let { stringResource(R.string.meal_detail_back, hmFormatter.format(it)) },
    ).joinToString(" ")
    else -> stringResource(R.string.meal_detail_stayed, response.peak)
}

/** Half an hour before the meal to three and a half after (or now), over the 70–180 band, the meal marked. */
@Composable
private fun ResponseChart(readings: List<GlucoseReading>, mealAt: Instant, now: Instant) {
    val start = mealAt.minus(Duration.ofMinutes(30))
    val end = minOf(mealAt.plus(Duration.ofMinutes(210)), maxOf(now, mealAt.plus(Duration.ofMinutes(30))))
    val points = readings.filter { it.timestamp >= start && it.timestamp <= end }
    if (points.size < 2) return
    val onBg = MaterialTheme.colorScheme.onBackground
    val measurer = rememberTextMeasurer()
    val caption = TextStyle(fontSize = 11.sp, color = CaptionMuted)
    val yMax = ((points.maxOf { it.glucoseMgDl } + 30) / 50 * 50).coerceIn(250, 400)
    val span = Duration.between(start, end).toMillis().toFloat()
    Canvas(Modifier.fillMaxWidth().height(124.dp)) {
        val plot = size.height - 18.dp.toPx()
        fun x(t: Instant) = Duration.between(start, t).toMillis() / span * size.width
        fun y(mgDl: Int) = plot * (1f - (mgDl.coerceIn(40, yMax) - 40).toFloat() / (yMax - 40))
        drawRect(Sage.copy(alpha = 0.10f), topLeft = Offset(0f, y(180)), size = Size(size.width, y(70) - y(180)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        listOf(70, 180).forEach { level ->
            drawLine(onBg.copy(alpha = 0.15f), Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val label = measurer.measure(String.format(Locale.getDefault(), "%d", level), caption)
            drawText(label, topLeft = Offset(size.width - label.size.width - 2.dp.toPx(), y(level) - label.size.height - 2.dp.toPx()))
        }
        val mx = x(mealAt)
        drawLine(Sage, Offset(mx, 6.dp.toPx()), Offset(mx, plot), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        drawCircle(Sage, radius = 5.dp.toPx(), center = Offset(mx, 6.dp.toPx()))
        val label = measurer.measure(hmFormatter.format(mealAt), caption)
        drawText(label, topLeft = Offset((mx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), plot + 4.dp.toPx()))

        val path = Path()
        points.forEachIndexed { i, r -> if (i == 0) path.moveTo(x(r.timestamp), y(r.glucoseMgDl)) else path.lineTo(x(r.timestamp), y(r.glucoseMgDl)) }
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        clipRect(bottom = y(180)) { drawPath(path, StateHigh, style = stroke) }
        clipRect(top = y(180), bottom = y(70)) { drawPath(path, Sage, style = stroke) }
        clipRect(top = y(70)) { drawPath(path, StateLow, style = stroke) }
    }
}
