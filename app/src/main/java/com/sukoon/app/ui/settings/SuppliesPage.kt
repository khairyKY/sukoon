package com.sukoon.app.ui.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.sukoon.app.ui.components.SukoonTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.supplies.PenKind
import com.sukoon.app.supplies.Pickup
import com.sukoon.app.supplies.Stock
import com.sukoon.app.supplies.Supply
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.logbook.outline
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * You → Supplies (design: the canvas's "Supplies, pickups and gym days"): pens of each kind as a
 * ring of days left and a row of pens, and the insurance pickup with its reminder.
 */
@Composable
fun SuppliesPage() {
    val app = (LocalContext.current.applicationContext as com.sukoon.app.SukoonApp).container
    val stock by app.supplies.stock.collectAsState()
    val pickup by app.supplies.pickup.collectAsState()
    val since = remember { Instant.now().minus(java.time.Duration.ofDays(120)).toEpochMilli() }
    val events by remember { app.logbookRepository.eventsSince(since) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<PenKind?>(null) }
    var pickedUp by remember { mutableStateOf(false) }

    PenKind.entries.forEach { kind ->
        val s = stock[kind]
        Column(Modifier.padding(bottom = 12.dp)) {
            if (s == null) {
                SetUpRow(stringResource(kindName(kind))) { editing = kind }
            } else {
                PenCard(kind, s, Supply.remaining(s, events, kind), Supply.perDay(events, kind, Instant.now(), ZoneId.systemDefault()),
                    onOpenPen = { app.supplies.set(kind, Supply.openPen(s, Instant.now())) }, onEdit = { editing = kind })
            }
        }
    }
    PickupCard(pickup, onChange = app.supplies::setPickup, onPickedUp = { pickedUp = true })

    editing?.let { kind ->
        val s = stock[kind]
        StockDialog(kind, s, onSave = { app.supplies.set(kind, it); editing = null }, onRemove = { app.supplies.set(kind, null); editing = null }, onDismiss = { editing = null })
    }
    if (pickedUp) {
        PickedUpDialog(stock.filterValues { it != null }.keys.toList(), onDone = { added ->
            val now = Instant.now()
            added.forEach { (kind, n) ->
                val s = stock[kind] ?: return@forEach
                if (n > 0) app.supplies.set(kind, Supply.addPens(s, Supply.remaining(s, events, kind), n, now))
            }
            app.supplies.setPickup(pickup.copy(next = LocalDate.now().plusDays(pickup.everyDays.toLong())))
            pickedUp = false
        }, onDismiss = { pickedUp = false })
    }
}

/** "Long-acting" or "Rapid". */
fun kindName(kind: PenKind): Int = if (kind == PenKind.LONG) R.string.supply_long else R.string.supply_rapid

@Composable
private fun SetUpRow(name: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.supply_set_up_body), fontSize = 12.5.sp, color = CaptionMuted)
        }
        Text(stringResource(R.string.supply_set_up), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
    }
}

@Composable
private fun PenCard(kind: PenKind, s: Stock, remaining: Double, perDay: Double?, onOpenPen: () -> Unit, onEdit: () -> Unit) {
    val days = Supply.daysLeft(remaining, perDay)
    val low = days != null && days <= Supply.LOW_DAYS
    val ring = if (low) PillHighText else SageDeep
    val openNow = (remaining - s.pens * s.penUnits).coerceIn(0.0, s.penUnits.toDouble())
    // The pens still unopened, filled; the open one shows how full it is.
    val fullPens = if (remaining >= s.pens * s.penUnits) s.pens else (remaining / s.penUnits).toInt().coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (low) PillHighBg else MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                val share = com.sukoon.app.ui.theme.Motion.fill(((days ?: 0) / 30f).coerceIn(0.02f, 1f)) // sweeps to its value (motion spec: progress)
                Canvas(Modifier.size(76.dp)) {
                    val w = 7.dp.toPx()
                    drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(ring, -90f, 360f * share, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(days?.let { java.text.NumberFormat.getIntegerInstance().format(it) } ?: "–", fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.supply_days), fontSize = 10.5.sp, color = CaptionMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(kindName(kind)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (low) PillHighText else MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    // The open pen, as full as it is, then each unopened one.
                    PenBar(openNow / s.penUnits, ring)
                    repeat(s.pens.coerceAtMost(12)) { i -> PenBar(if (i < fullPens) 1.0 else 0.0, ring) }
                }
                val until = days?.let { LocalDate.now().plusDays(it.toLong()).format(DateTimeFormatter.ofPattern("EEE d MMM").withDecimalStyle(java.time.format.DecimalStyle.ofDefaultLocale())) }
                Text(
                    stringResource(R.string.supply_left_line, s.pens, openNow.toInt()) + (until?.let { " · " + stringResource(R.string.supply_until, it) } ?: ""),
                    fontSize = 12.5.sp, color = CaptionMuted,
                )
                if (perDay == null) Text(stringResource(R.string.supply_need_doses), fontSize = 12.sp, color = CaptionMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Link(stringResource(R.string.supply_opened_pen), onOpenPen)
            Link(stringResource(R.string.supply_edit), onEdit)
        }
    }
}

@Composable
private fun PenBar(full: Double, color: Color) {
    Box(Modifier.width(12.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).border(1.5.dp, color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.width(12.dp).height((30 * full.coerceIn(0.0, 1.0)).dp).background(color))
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) = Text(
    text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep,
    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).heightIn(min = 40.dp).padding(vertical = 10.dp),
)

@Composable
private fun PickupCard(p: Pickup, onChange: (Pickup) -> Unit, onPickedUp: () -> Unit) {
    val context = LocalContext.current
    val to = Supply.daysToPickup(p, LocalDate.now())
    val soon = to != null && to <= p.remindDays
    fun pickDate() {
        val d = p.next ?: LocalDate.now().plusDays(p.everyDays.toLong())
        DatePickerDialog(context, { _, y, m, day -> onChange(p.copy(next = LocalDate.of(y, m + 1, day))) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(if (soon) PillHighBg else MaterialTheme.colorScheme.surface)
            .border(1.dp, outline(), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.pickup_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (soon) PillHighText else MaterialTheme.colorScheme.onBackground)
        Text(
            p.next?.let { stringResource(R.string.pickup_next, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withDecimalStyle(java.time.format.DecimalStyle.ofDefaultLocale()))) } ?: stringResource(R.string.pickup_none),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { pickDate() }.heightIn(min = 40.dp).padding(vertical = 10.dp),
        )
        Text(stringResource(R.string.pickup_every), fontSize = 12.5.sp, color = CaptionMuted)
        ChoiceChips(listOf(28, 30, 60, 90), p.everyDays, { stringResource(R.string.pickup_days, it) }) { onChange(p.copy(everyDays = it)) }
        Text(stringResource(R.string.pickup_remind), fontSize = 12.5.sp, color = CaptionMuted)
        ChoiceChips(listOf(1, 2, 3, 5, 7), p.remindDays, { stringResource(R.string.pickup_days, it) }) { onChange(p.copy(remindDays = it)) }
        if (p.next != null) {
            Text(
                stringResource(R.string.pickup_done), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sage).clickable(onClick = onPickedUp).heightIn(min = 48.dp).padding(14.dp),
            )
        }
    }
}

@Composable
private fun StockDialog(kind: PenKind, s: Stock?, onSave: (Stock) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var units by remember { mutableStateOf(s?.penUnits ?: if (kind == PenKind.LONG) 450 else 300) }
    var pens by remember { mutableStateOf(s?.pens ?: 0) }
    var open by remember { mutableStateOf((s?.openLeft?.toInt() ?: units).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(kindName(kind))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.supply_pen_size), fontSize = 13.sp, color = CaptionMuted)
                ChoiceChips(listOf(300, 450, 900), units, { stringResource(R.string.supply_units, it) }) { units = it }
                Text(stringResource(R.string.supply_unopened), fontSize = 13.sp, color = CaptionMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Link("−") { pens = (pens - 1).coerceAtLeast(0) }
                    Text(java.text.NumberFormat.getIntegerInstance().format(pens), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp)
                    Link("+") { pens += 1 }
                }
                SukoonTextField(open, { open = it.filter(Char::isDigit).take(3) }, label = stringResource(R.string.supply_open_left),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Stock(units, pens, (open.toIntOrNull() ?: 0).coerceIn(0, units).toDouble(), Instant.now())) }) { Text(stringResource(R.string.entry_save)) }
        },
        dismissButton = {
            Row {
                if (s != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.supply_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensor_cancel)) }
            }
        },
    )
}

@Composable
private fun PickedUpDialog(kinds: List<PenKind>, onDone: (Map<PenKind, Int>) -> Unit, onDismiss: () -> Unit) {
    var added by remember { mutableStateOf(kinds.associateWith { 0 }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pickup_how_many)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kinds.isEmpty()) Text(stringResource(R.string.pickup_no_counts), fontSize = 13.sp, color = CaptionMuted)
                kinds.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(kindName(k)), Modifier.weight(1f), fontSize = 15.sp)
                        Link("−") { added = added + (k to ((added[k] ?: 0) - 1).coerceAtLeast(0)) }
                        Text(java.text.NumberFormat.getIntegerInstance().format(added[k] ?: 0), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp)
                        Link("+") { added = added + (k to (added[k] ?: 0) + 1) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(added) }) { Text(stringResource(R.string.entry_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sensor_cancel)) } },
    )
}
