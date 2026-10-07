package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukoon.app.R
import com.sukoon.app.data.db.EventEntity
import com.sukoon.app.data.db.LogEventType
import com.sukoon.app.insulin.InjectionSites
import com.sukoon.app.ui.components.ChoiceChips
import com.sukoon.app.ui.logbook.BodyMap
import com.sukoon.app.ui.logbook.siteName
import com.sukoon.app.ui.logbook.typeLabel
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.PillHighBg
import com.sukoon.app.ui.theme.PillHighText
import java.time.Duration
import java.time.Instant

/**
 * You → Insulin (design "You · Injection sites"): where the last month's rapid or long-acting doses
 * went, the spot to use next, and a nudge when one spot takes too many.
 */
@Composable
fun InjectionSitesCard(load: suspend () -> List<EventEntity>) {
    val history by produceState<List<EventEntity>?>(null) { value = runCatching { load() }.getOrDefault(emptyList()) }
    var type by rememberSaveable { mutableStateOf(LogEventType.INSULIN) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChoiceChips(listOf(LogEventType.INSULIN, LogEventType.BASAL), type, { typeLabel(it) }) { type = it }
        val doses = history ?: return@Column
        val now = Instant.now()
        val counts = InjectionSites.counts(doses, type, now.minus(Duration.ofDays(30)))
        val total = counts.values.sum()
        if (total == 0) {
            Text(stringResource(R.string.sites_empty), fontSize = 12.5.sp, color = CaptionMuted)
            return@Column
        }
        val next = InjectionSites.next(doses, type)
        BodyMap(selected = null, suggested = next, onPick = null, heat = counts, height = 190.dp, modifier = Modifier.fillMaxWidth())
        Text(
            listOfNotNull(pluralStringResource(R.plurals.sites_doses, total, total), next?.let { stringResource(R.string.site_next, siteName(it)) }).joinToString(" · "),
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        InjectionSites.crowded(doses, now)?.takeIf { it.type == type }?.let { c ->
            Text(
                stringResource(R.string.sites_crowded, siteName(c.site), c.count, c.total),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PillHighBg).padding(12.dp),
                fontSize = 13.sp,
                color = PillHighText,
            )
        }
        Text(stringResource(R.string.sites_speed), fontSize = 12.sp, color = CaptionMuted)
    }
}
