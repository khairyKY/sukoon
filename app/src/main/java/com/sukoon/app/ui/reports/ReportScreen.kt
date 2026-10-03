package com.sukoon.app.ui.reports

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sukoon.app.R
import com.sukoon.app.reports.AgpPdf
import com.sukoon.app.reports.AgpRenderer
import com.sukoon.app.reports.AgpReport
import com.sukoon.app.ui.components.NumberChips
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.graph.RangeStats
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Trends → Report: the AGP and its metrics over 7, 14 or 30 days, and a one-page PDF for the doctor. */
@Composable
fun ReportScreen(state: ReportUiState, name: String, onSelectDays: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var making by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.report_title), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        NumberChips(listOf(7, 14, 30), state.days, 1..90, { stringResource(R.string.report_days, it) }) { onSelectDays(it) }
        val report = state.report
        if (report == null) {
            Text(stringResource(if (state.loading) R.string.report_loading else R.string.report_no_data), fontSize = 14.sp, color = CaptionMuted)
            return@Column
        }
        val day = DateTimeFormatter.ofPattern("d MMM")
        Text(stringResource(R.string.report_subtitle, day.format(report.from), day.format(report.to)), fontSize = 12.sp, color = CaptionMuted)

        Card {
            Metric(stringResource(R.string.report_active), stringResource(R.string.report_active_value, report.activePercent, report.daysWithData))
            Metric(stringResource(R.string.report_mean), stringResource(R.string.report_mean_value, report.summary.meanMgDl))
            Metric(stringResource(R.string.report_gmi), stringResource(R.string.report_gmi_value, String.format(Locale.getDefault(), "%.1f", report.summary.gmiPercent)))
            Metric(stringResource(R.string.report_cv), stringResource(R.string.report_cv_value, report.cvPercent))
        }

        RangeStats(report.summary, stringResource(R.string.report_tir), showGmi = false)

        Card {
            Text(stringResource(R.string.report_profile), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            val look = AgpRenderer.Look(
                outer = Sage.copy(alpha = 0.22f).toArgb(),
                inner = Sage.copy(alpha = 0.5f).toArgb(),
                median = MaterialTheme.colorScheme.onBackground.toArgb(),
                target = Sage.toArgb(),
                grid = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f).toArgb(),
                text = CaptionMuted.toArgb(),
                textSize = 0f,
                unit = 0f,
            )
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                drawIntoCanvas {
                    AgpRenderer.draw(it.nativeCanvas, RectF(0f, 0f, size.width, size.height), report.profile, look.copy(textSize = 11.sp.toPx(), unit = 1.dp.toPx()))
                }
            }
            Text(stringResource(R.string.report_legend), fontSize = 12.sp, lineHeight = 16.sp, color = CaptionMuted)
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (making) Sage.copy(alpha = 0.5f) else Sage)
                .clickable(enabled = !making) {
                    making = true
                    scope.launch {
                        try {
                            share(context, withContext(Dispatchers.IO) { AgpPdf.write(context, report, name) })
                        } catch (e: Exception) {
                            context.toast(context.getString(R.string.toast_report_failed, e.message ?: e.javaClass.simpleName), long = true)
                        } finally {
                            making = false
                        }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(stringResource(if (making) R.string.report_making else R.string.report_share), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(stringResource(R.string.report_note), fontSize = 12.sp, lineHeight = 16.sp, color = CaptionMuted)
    }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share)))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = CaptionMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}
