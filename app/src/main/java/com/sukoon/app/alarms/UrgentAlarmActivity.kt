package com.sukoon.app.alarms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.StateUrgent
import kotlinx.coroutines.launch

/**
 * The urgent-low takeover: shown over the lock screen (full-screen intent) or straight over
 * whatever is open (with "display over other apps"). One big action — "I'm treating it" —
 * silences the alarm for 5 minutes; if glucose is still urgent then, it comes back.
 */
class UrgentAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val mgDl = intent.getIntExtra(EXTRA_MG_DL, -1).takeIf { it > 0 }
        val container = (applicationContext as SukoonApp).container
        setContent {
            Column(
                Modifier.fillMaxSize().background(StateUrgent).safeDrawingPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.alarm_urgent_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    mgDl?.toString() ?: "—",
                    color = Color.White,
                    fontSize = 120.sp,
                    fontFamily = HeadlineSerifFontFamily,
                )
                Text(stringResource(R.string.alarm_urgent_body), color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable {
                            lifecycleScope.launch {
                                container.alarms.acknowledge(AlarmType.URGENT_LOW, 5)
                                finish()
                            }
                        }
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.alarm_action_treating), color = StateUrgent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_MG_DL = "mg_dl"

        fun intent(context: Context, mgDl: Int?) = Intent(context, UrgentAlarmActivity::class.java)
            .putExtra(EXTRA_MG_DL, mgDl ?: -1)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
