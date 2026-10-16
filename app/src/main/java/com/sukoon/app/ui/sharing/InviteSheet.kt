package com.sukoon.app.ui.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.sukoon.app.R
import com.sukoon.app.sharing.InviteCode
import com.sukoon.app.ui.components.SukoonSheet
import com.sukoon.app.ui.components.toast
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import java.time.Duration
import java.time.Instant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * An invite, ready to hand over: a QR code to scan in person (the phone's camera opens the link), the
 * code to read out, and the link to send. The link opens Sukoon with the code filled in; on an iPhone
 * or without the app, the follow page in the browser.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(code: String, expiresAt: Instant?, from: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = InviteCode.link(code)
    SukoonSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.invite_sheet_title), fontFamily = HeadlineSerifFontFamily, fontSize = 26.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text(stringResource(R.string.invite_sheet_body), fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted, textAlign = TextAlign.Center)
            // Always dark on white, whatever the theme: that's what cameras read best.
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
                Qr(link, Modifier.size(220.dp).semantics { contentDescription = context.getString(R.string.invite_qr_description) })
            }
            Text(InviteCode.format(code), fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            val left = expiresAt?.let { Duration.between(Instant.now(), it) }?.takeIf { !it.isNegative }
            Text(
                left?.let { stringResource(R.string.invite_works_once_left, com.sukoon.app.ui.components.durationText(context, it.toMinutes())) } ?: stringResource(R.string.invite_works_once),
                fontSize = 13.sp, color = CaptionMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.invite_share)) {
                    val text = context.getString(R.string.sharing_invite_message, from, link, InviteCode.format(code))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
                Pill(stringResource(R.string.invite_copy), filled = false) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Sukoon invite", link))
                    context.toast(context.getString(R.string.toast_invite_copied))
                }
            }
        }
    }
}

/** [text] as a QR code, drawn square by square (medium error correction, 2-module quiet zone). */
@Composable
private fun Qr(text: String, modifier: Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
    }
    Canvas(modifier) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * An invite link was tapped: follow in one tap when signed in. Signed out, "Sign in" opens You → People
 * and the code waits; this comes back once there's an account.
 */
@Composable
fun FollowInviteDialog(
    code: String,
    signedIn: Boolean,
    sharing: com.sukoon.app.sharing.Sharing,
    watch: com.sukoon.app.sharing.FollowerWatch,
    onSignIn: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var problem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.invite_follow_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(InviteCode.format(code), fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(if (signedIn) R.string.invite_follow_body else R.string.invite_follow_sign_in), fontSize = 13.5.sp, lineHeight = 19.sp, color = CaptionMuted)
                problem?.let { Text(it, fontSize = 13.sp, color = com.sukoon.app.ui.theme.StateLow) }
            }
        },
        confirmButton = {
            if (signedIn) {
                androidx.compose.material3.TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val owner = sharing.redeem(code)
                            watch.refreshNow()
                            context.toast(context.getString(R.string.toast_sharing_now_following, owner.ifBlank { "…" }), long = true)
                            onDone()
                        } catch (e: Exception) {
                            problem = readable(context, e)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(if (busy) R.string.sharing_working else R.string.sharing_follow)) }
            } else {
                androidx.compose.material3.TextButton(onClick = onSignIn) { Text(stringResource(R.string.sharing_sign_in)) }
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text(stringResource(R.string.ob_not_now)) } },
    )
}
