package com.sukoon.app.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "‹ Today ›", "‹ Mon 6 Oct ›": a day back, a day on, or tap the day for a calendar. [day] null is
 * today (the last 24 hours, live).
 */
@Composable
fun DayBar(day: LocalDate?, onDay: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = LocalDate.now(ZoneId.systemDefault())
    val shown = day ?: today
    fun go(to: LocalDate) = onDay(to.takeIf { it.isBefore(today) })
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Arrow(stringResource(R.string.day_previous), enabled = true, flip = false) { go(shown.minusDays(1)) }
        Text(
            when (shown) {
                today -> stringResource(R.string.day_today)
                today.minusDays(1) -> stringResource(R.string.day_yesterday)
                else -> DateTimeFormatter.ofPattern(if (shown.year == today.year) "EEE d MMM" else "d MMM yyyy").format(shown)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    DatePickerDialog(context, { _, y, m, d -> go(LocalDate.of(y, m + 1, d)) }, shown.year, shown.monthValue - 1, shown.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }
                        .show()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Arrow(stringResource(R.string.day_next), enabled = shown.isBefore(today), flip = true) { go(shown.plusDays(1)) }
    }
}

@Composable
private fun Arrow(description: String, enabled: Boolean, flip: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_back),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp).then(if (flip) Modifier.rotate(180f) else Modifier),
        )
    }
}
