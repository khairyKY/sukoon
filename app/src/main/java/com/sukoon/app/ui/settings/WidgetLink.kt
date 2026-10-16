package com.sukoon.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.BuildConfig
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Where a Nightscout widget reads Sukoon from (supabase/functions/nightscout). */
internal val WIDGET_FEED_URL = "${BuildConfig.SUPABASE_URL}/functions/v1/nightscout"

/**
 * You › Apps & data › Widget link: a private address that serves your readings in Nightscout's format,
 * for a laptop widget (Rainmeter) or a watch face, with no Nightscout server or VPN. The token is the
 * key: a new one stops the old link.
 */
@Composable
internal fun WidgetLink() {
    val context = LocalContext.current
    val supabase = (context.applicationContext as SukoonApp).container.sharing.supabase
    val session by supabase.session.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.widget_link_failed)

    if (session == null) {
        Text(stringResource(R.string.widget_link_sign_in), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted, modifier = Modifier.padding(vertical = 10.dp))
        return
    }
    LaunchedEffect(session?.userId) {
        token = runCatching { JSONArray(supabase.get("feed_tokens?select=token")).optJSONObject(0)?.optString("token")?.takeIf { it.isNotBlank() } }.getOrNull()
    }
    fun make() {
        busy = true
        scope.launch {
            token = runCatching { supabase.rpc("new_feed_token", org.json.JSONObject()).trim().trim('"') }.getOrNull()
                ?: run { Toast.makeText(context, failed, Toast.LENGTH_LONG).show(); token }
            busy = false
        }
    }
    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, context.getString(R.string.widget_link_copied), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.widget_link_body), fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
        val t = token
        if (t == null) {
            Pill(stringResource(R.string.widget_link_make), enabled = !busy) { make() }
            return@Column
        }
        Field(stringResource(R.string.widget_link_address), WIDGET_FEED_URL) { copy(WIDGET_FEED_URL) }
        Field(stringResource(R.string.widget_link_token), t) { copy(t) }
        Field(stringResource(R.string.widget_link_full), "$WIDGET_FEED_URL/api/v1/entries/sgv.json?count=240&token=$t") { copy("$WIDGET_FEED_URL/api/v1/entries/sgv.json?count=240&token=$t") }
        Text(stringResource(R.string.widget_link_private), fontSize = 12.5.sp, lineHeight = 17.sp, color = CaptionMuted)
        Pill(stringResource(R.string.widget_link_new), enabled = !busy, quiet = true) { make() }
    }
}

/** A label, the value in mono (one line, ellipsed), tap to copy. */
@Composable
private fun Field(label: String, value: String, onCopy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SageMist.copy(alpha = 0.6f)).clickable(onClick = onCopy).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.widget_link_copy), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Sage)
        }
        Text(value, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun Pill(label: String, enabled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (quiet) Sage else androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (quiet) androidx.compose.ui.graphics.Color.Transparent else Sage)
            .clickable(enabled = enabled, onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}
