package com.sukoon.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.platform.Notices
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A notice from Sukoon on Now (design "Notices", the slim banner): one line under the app bar, a dot
 * while unread. A tap opens the whole notice in a sheet; nothing shows when there's no notice.
 */
@Composable
fun NoticeBanner(modifier: Modifier = Modifier) {
    val notices = (LocalContext.current.applicationContext as? SukoonApp)?.container?.notices ?: return // previews
    val notice by notices.current.collectAsStateWithLifecycle()
    val read by notices.read.collectAsStateWithLifecycle()
    val shown = notice ?: return
    val arabic = LocalContext.current.resources.configuration.locales[0].language == "ar"
    var open by rememberSaveable(shown.id) { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SageMist)
            .clickable {
                open = true
                notices.markRead(shown.id)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mark(24, MaterialTheme.colorScheme.background)
        Text(shown.titleIn(arabic), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (shown.id !in read) Box(Modifier.size(7.dp).clip(CircleShape).background(Sage))
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = SageDeep, modifier = Modifier.size(16.dp))
    }
    if (open) NoticeSheet(shown, arabic, notices) { open = false }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheet(notice: Notices.Notice, arabic: Boolean, notices: Notices, onClose: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    SukoonSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mark(32, SageMist)
                Text(
                    when (val sent = notice.date) {
                        null -> stringResource(R.string.notice_from)
                        today -> stringResource(R.string.notice_from_today)
                        else -> stringResource(R.string.notice_from_date, sent.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }.uppercase(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (arabic) 0.sp else 1.sp,
                    color = CaptionMuted,
                )
            }
            Text(notice.titleIn(arabic), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(notice.bodyIn(arabic), fontSize = 15.sp, lineHeight = 23.sp, color = CaptionMuted)
            notice.until?.let { Text(stringResource(R.string.notice_until, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))), fontSize = 12.5.sp, color = CaptionMuted) }
            Spacer(Modifier.height(2.dp))
            notice.link?.let { link ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Sage)
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.notice_open), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White) }
            }
            TextButton(
                onClick = {
                    notices.dismiss(notice.id)
                    onClose()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.notice_dont_show), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep) }
        }
    }
}

/** Sukoon's mark in a circle, as the notification's icon. */
@Composable
private fun Mark(size: Int, background: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_stat_sukoon), contentDescription = null, tint = SageDeep, modifier = Modifier.size((size * 0.6f).dp))
    }
}
