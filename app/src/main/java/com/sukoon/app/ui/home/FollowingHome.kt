package com.sukoon.app.ui.home

import androidx.compose.runtime.mutableIntStateOf
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.prefs.SettingsPrefs
import com.sukoon.app.data.source.GlucoseReading
import com.sukoon.app.emergency.PhoneNumbers
import com.sukoon.app.insights.InsightEngine
import com.sukoon.app.sharing.Followed
import com.sukoon.app.sharing.Sharing
import com.sukoon.app.ui.components.durationText
import com.sukoon.app.ui.graph.GlucoseChart
import com.sukoon.app.ui.graph.GraphRange
import com.sukoon.app.ui.graph.GraphScreen
import com.sukoon.app.ui.graph.GraphUiState
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.PillLowBg
import com.sukoon.app.ui.theme.PillLowText
import com.sukoon.app.ui.theme.PillNeutralBg
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.TextMuted
import com.sukoon.app.ui.widget.arrow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import com.sukoon.app.alarms.AlarmEngine
import com.sukoon.app.alarms.AlarmSettings
import kotlin.math.roundToInt
import androidx.compose.ui.res.pluralStringResource

private val THREE_HOURS: Duration = Duration.ofHours(3)

/** The people this phone follows, refreshed every minute (null until the first answer). */
@Composable
private fun rememberFollowing(sharing: Sharing): List<Followed>? {
    val people by produceState<List<Followed>?>(null, sharing) {
        while (true) {
            runCatching { sharing.following() }.onSuccess { value = it }
            delay(60_000)
        }
    }
    return people
}

/**
 * Home for someone who follows (design "Follower · Home"): the person's live number and trend, their
 * last 3 hours, how they're doing in plain words, and Call / Message. A phone following several
 * people switches between them from the name at the top.
 */
@Composable
fun FollowingHome(sharing: Sharing, settings: SettingsPrefs, onAddPerson: () -> Unit, modifier: Modifier = Modifier, personId: String? = null) {
    val people = rememberFollowing(sharing)
    var selectedId by rememberSaveable { mutableStateOf(personId) }
    val person = people?.let { list -> list.firstOrNull { it.id == selectedId } ?: list.firstOrNull() }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        when {
            people == null -> Unit // first load
            person == null -> NobodyYet(onAddPerson)
            else -> PersonView(sharing, settings, person, people) { selectedId = it }
        }
    }
}

@Composable
private fun PersonView(sharing: Sharing, settings: SettingsPrefs, person: Followed, people: List<Followed>, onSwitch: (String) -> Unit) {
    val context = LocalContext.current
    val name = person.name.ifBlank { "…" }
    val readings by produceState(emptyList<GlucoseReading>(), person.id) {
        while (true) {
            runCatching { sharing.readingsOf(person.id, System.currentTimeMillis() - THREE_HOURS.toMillis()) }.onSuccess { value = it }
            delay(60_000)
        }
    }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val latest = readings.lastOrNull() ?: person.latest
    val minutesAgo = latest?.let { Duration.between(it.timestamp, now).toMinutes().coerceAtLeast(0) }
    val fresh = minutesAgo != null && minutesAgo <= HomeUiStateMapper.STALE_AFTER.toMinutes()
    var phone by remember(person.id) { mutableStateOf(settings.followedPhone(person.id)) }
    var askingPhone by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                    .clickable(enabled = people.size > 1) { switching = true }
                    .heightIn(min = 44.dp)
                    .padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), fontFamily = HeadlineSerifFontFamily, fontSize = 17.sp, color = SageDeep)
                }
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(expanded = switching, onDismissRequest = { switching = false }) {
                people.forEach { p ->
                    DropdownMenuItem(text = { Text(p.name.ifBlank { "…" }) }, onClick = { onSwitch(p.id); switching = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (fresh) Sage else TextMuted))
            Text(stringResource(if (fresh) R.string.home_status_live else R.string.home_status_no_signal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = CaptionMuted)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val v = latest?.glucoseMgDl
        val (pillBg, pillText, pillLabel) = when {
            v == null || !fresh -> Triple(PillNeutralBg, TextMuted, stringResource(R.string.home_pill_signal_lost))
            v < 70 -> Triple(PillLowBg, PillLowText, stringResource(R.string.home_pill_low))
            v > 180 -> Triple(PillHighBg, PillHighText, stringResource(R.string.home_pill_high))
            else -> Triple(SageMist, SageDeep, stringResource(R.string.home_pill_in_range))
        }
        Text(pillLabel, modifier = Modifier.clip(RoundedCornerShape(50)).background(pillBg).padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pillText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text(v?.let { String.format(Locale.getDefault(), "%d", it) } ?: "- - -", fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 104.sp, color = if (fresh) MaterialTheme.colorScheme.onBackground else TextMuted)
            if (latest != null && fresh) Text(latest.trend.arrow, fontFamily = HeadlineSerifFontFamily, fontSize = 34.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 16.dp))
        }
        if (minutesAgo != null) {
            Text(
                "${stringResource(R.string.home_unit_mgdl)} · ${if (minutesAgo < 1) stringResource(R.string.graph_just_now) else pluralStringResource(R.plurals.graph_min_ago, minutesAgo.toInt(), minutesAgo.toInt())}",
                fontSize = 13.sp,
                color = CaptionMuted,
            )
        }
    }

    if (readings.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        GlucoseChart(readings, GraphRange.H3, emptyList(), remember { ZoneId.systemDefault() }, height = 132.dp, interactive = false)
        Text(stringResource(R.string.follow_last_3_hours), fontSize = 12.sp, color = CaptionMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }

    Spacer(Modifier.height(16.dp))
    val (title, body, calm) = followerWords(name, readings, latest, fresh, minutesAgo, context)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        if (body.isNotEmpty()) Text(body, fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted, modifier = Modifier.padding(top = 3.dp))
    }
    Row(Modifier.padding(start = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (calm) Sage else PillHighText))
        Text(stringResource(if (calm) R.string.home_nothing_to_do else R.string.follow_check_on, name), fontSize = 13.sp, color = CaptionMuted)
    }

    Spacer(Modifier.height(20.dp))
    val number = phone
    if (number == null) {
        ActionButton(R.drawable.ic_phone, stringResource(R.string.follow_add_number, name), Modifier.fillMaxWidth()) { askingPhone = true }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(R.drawable.ic_phone, stringResource(R.string.follow_call, name), Modifier.weight(1f)) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ActionButton(R.drawable.ic_message, stringResource(R.string.follow_message), Modifier.weight(1f)) {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    if (askingPhone) {
        PhoneDialog(name, onSave = { raw ->
            val normalized = raw.takeIf { it.isNotBlank() }?.let { PhoneNumbers.normalize(it, PhoneNumbers.callingCodeFor(Locale.getDefault().country)) }
            settings.setFollowedPhone(person.id, normalized)
            phone = normalized
            askingPhone = false
        }, onDismiss = { askingPhone = false })
    }
}

/** How they're doing, in words: title, detail, and whether all is calm. */
private fun followerWords(name: String, readings: List<GlucoseReading>, latest: GlucoseReading?, fresh: Boolean, minutesAgo: Long?, context: android.content.Context): Triple<String, String, Boolean> {
    if (latest == null) return Triple(context.getString(R.string.follow_no_data, name), "", true)
    if (!fresh) return Triple(context.getString(R.string.follow_no_signal, name), context.getString(R.string.follow_last_seen, latest.glucoseMgDl, (minutesAgo ?: 0).toInt()), false)
    val v = latest.glucoseMgDl
    fun runFor(inside: (Int) -> Boolean) = if (readings.isEmpty()) "" else durationText(context, HomeBriefs.run(readings, inside).coerceAtLeast(1))
    // The same lines as the wearer's own Home and alarms (docs/behaviour.md): urgent under 55, heading low within 20 min.
    val projected = AlarmEngine.projected((readings.takeLast(60) + latest).distinctBy { it.timestamp }, latest)
    return when {
        v < AlarmSettings.URGENT_LOW_MG_DL -> Triple(context.getString(R.string.follow_urgent, name), context.getString(R.string.follow_urgent_for, runFor { it < AlarmSettings.URGENT_LOW_MG_DL }), false)
        v < 70 -> Triple(context.getString(R.string.follow_low, name), context.getString(R.string.follow_low_for, runFor { it < 70 }), false)
        projected < 70 -> Triple(context.getString(R.string.follow_heading_low, name), context.resources.getQuantityString(R.plurals.follow_heading_low_in, ((((v - 70) * 20) / (v - projected)).roundToInt().coerceAtLeast(1)).toInt(), (((v - 70) * 20) / (v - projected)).roundToInt().coerceAtLeast(1)), false)
        v > 250 -> Triple(context.getString(R.string.follow_high, name), context.getString(R.string.follow_high_for, runFor { it > 180 }), false)
        v > 180 -> Triple(context.getString(R.string.follow_high, name), context.getString(R.string.follow_high_for, runFor { it > 180 }), true)
        else -> Triple(context.getString(R.string.follow_steady, name), context.getString(R.string.follow_in_range_for, runFor { it in 70..180 }), true)
    }
}

@Composable
private fun ActionButton(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

@Composable
private fun PhoneDialog(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.follow_add_number, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.follow_number_private), fontSize = 13.sp, color = CaptionMuted)
                OutlinedTextField(number, { number = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(enabled = number.count { it.isDigit() } >= 7, onClick = { onSave(number) }) { Text(stringResource(R.string.entry_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.emergency_cancel)) } },
    )
}

@Composable
private fun NobodyYet(onAddPerson: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.follow_nobody_title), fontFamily = HeadlineSerifFontFamily, fontSize = 25.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(stringResource(R.string.follow_nobody_body), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Box(
            Modifier.padding(top = 22.dp).fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).background(Sage).clickable(onClick = onAddPerson),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.sharing_enter_code), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/** Trends for a follower: the person's graph, every range, time in range and readings. */
@Composable
fun FollowingTrends(sharing: Sharing, modifier: Modifier = Modifier) {
    val people = rememberFollowing(sharing)
    val person = people?.firstOrNull() ?: return Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    var tab by rememberSaveable { mutableIntStateOf(0) } // graph, insights, report
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.graph_title, R.string.insights_tab, R.string.report_tab).forEachIndexed { i, label ->
                com.sukoon.app.ui.navigation.SubTabChip(stringResource(label), tab == i) { tab = i }
            }
        }
        when (tab) {
            0 -> FollowedGraph(sharing, person, Modifier.weight(1f))
            1 -> FollowedInsights(sharing, person, Modifier.weight(1f))
            else -> FollowedReport(sharing, person, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FollowedGraph(sharing: Sharing, person: Followed, modifier: Modifier) {
    var range by rememberSaveable { mutableStateOf(GraphRange.H6) }
    val readings by produceState(emptyList<GlucoseReading>(), person.id, range) {
        while (true) {
            runCatching { sharing.readingsOf(person.id, System.currentTimeMillis() - range.millis) }.onSuccess { value = it }
            delay(60_000)
        }
    }
    val summary = remember(readings) { InsightEngine.summary(readings, Instant.now()) }
    GraphScreen(state = GraphUiState(range, readings, emptyList(), summary), onSelectRange = { range = it }, modifier = modifier, title = person.name.ifBlank { null })
}

/**
 * Their insights, from their shared readings (the last 14 days): time in range, variability, recurring
 * lows and highs, the dawn rise. Meals and insulin stay on their phone, so those insights don't show here.
 */
@Composable
private fun FollowedInsights(sharing: Sharing, person: Followed, modifier: Modifier) {
    val settings = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container.settings
    var acknowledged by remember { mutableStateOf(settings.insightsAcknowledged) }
    val insights by produceState<List<com.sukoon.app.insights.Insight>?>(null, person.id) {
        while (true) {
            runCatching {
                val readings = sharing.readingsOf(person.id, System.currentTimeMillis() - Duration.ofDays(com.sukoon.app.insights.InsightEngine.WINDOW_DAYS).toMillis())
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { InsightEngine.analyze(readings, emptyList(), Instant.now(), java.time.ZoneId.systemDefault()) }
            }.onSuccess { value = it }
            delay(Duration.ofMinutes(15).toMillis())
        }
    }
    com.sukoon.app.ui.insights.InsightsScreen(
        com.sukoon.app.ui.insights.InsightsUiState(acknowledged, insights),
        onAcknowledge = { settings.insightsAcknowledged = true; acknowledged = true },
        modifier = modifier,
    )
}

/** Their doctor report (AGP) over 7, 14 or 30 days of shared readings, as a PDF like their own. */
@Composable
private fun FollowedReport(sharing: Sharing, person: Followed, modifier: Modifier) {
    var days by rememberSaveable { mutableIntStateOf(14) }
    val state by produceState(com.sukoon.app.ui.reports.ReportUiState(days), person.id, days) {
        value = com.sukoon.app.ui.reports.ReportUiState(days)
        runCatching {
            val readings = sharing.readingsOf(person.id, System.currentTimeMillis() - Duration.ofDays(days.toLong()).toMillis())
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.sukoon.app.reports.Agp.build(readings, Instant.now(), java.time.ZoneId.systemDefault(), days) }
        }.onSuccess { value = com.sukoon.app.ui.reports.ReportUiState(days, it, loading = false) }
            .onFailure { value = com.sukoon.app.ui.reports.ReportUiState(days, null, loading = false) }
    }
    com.sukoon.app.ui.reports.ReportScreen(state, person.name, onSelectDays = { days = it }, modifier = modifier)
}

/** On a wearer's Home who also follows someone: each person's latest, one tap to their view. */
@Composable
fun FollowingStrip(sharing: Sharing, onOpen: (Followed) -> Unit) {
    val people = rememberFollowing(sharing).orEmpty()
    if (people.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { p ->
            val r = p.latest
            val fresh = r != null && Duration.between(r.timestamp, Instant.now()) <= HomeUiStateMapper.STALE_AFTER
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .clickable { onOpen(p) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(
                        when {
                            !fresh -> TextMuted
                            r!!.glucoseMgDl < 70 -> PillLowText
                            r.glucoseMgDl > 180 -> PillHighText
                            else -> Sage
                        },
                    ),
                )
                Text(
                    "${p.name.ifBlank { "…" }} ${if (fresh) String.format(Locale.getDefault(), "%d %s", r!!.glucoseMgDl, r.trend.arrow) else "- - -"}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
