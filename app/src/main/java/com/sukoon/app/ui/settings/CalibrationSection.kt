package com.sukoon.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.R
import com.sukoon.app.calibration.CalibrationManager
import com.sukoon.app.calibration.Calibrator
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import java.util.Locale
import kotlin.math.roundToInt

/** You → Calibration: opt in to matching the sensor to your finger-pricks, and see what it's doing. */
@Composable
fun CalibrationSection(manager: CalibrationManager) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(manager.enabled) }
    val fit by manager.fit.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.calibration_toggle), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.calibration_body), fontSize = 12.sp, color = CaptionMuted)
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    manager.enabled = it
                    context.toast(context.getString(if (it) R.string.toast_calibration_on else R.string.toast_calibration_off))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Sage),
            )
        }
        if (on) {
            val calibration = fit?.calibration
            if (calibration == null) {
                Text(stringResource(R.string.calibration_waiting), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            } else {
                Text(
                    stringResource(
                        R.string.calibration_using,
                        calibration.pairsUsed,
                        String.format(Locale.getDefault(), "%.2f", calibration.slope),
                        String.format(Locale.getDefault(), "%+d", calibration.intercept.roundToInt()),
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(stringResource(R.string.calibration_example, 150, calibration.apply(150), 65, calibration.apply(65)), fontSize = 12.sp, color = Sage)
            }
            val skipped = fit?.skipped.orEmpty().size
            if (skipped > 0) Text(stringResource(R.string.calibration_skipped, skipped), fontSize = 12.sp, color = CaptionMuted)
            Text(
                stringResource(R.string.calibration_caps, String.format(Locale.getDefault(), "%.2f", Calibrator.SLOPE_MIN), String.format(Locale.getDefault(), "%.2f", Calibrator.SLOPE_MAX), Calibrator.INTERCEPT_LIMIT.roundToInt()),
                fontSize = 11.5.sp,
                color = CaptionMuted,
            )
        }
    }
}
