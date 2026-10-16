package com.sukoon.app.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import com.sukoon.app.ui.settings.rememberMissingSetup
import com.sukoon.app.platform.SetupItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateHigh

/**
 * The Guide, full screen (Redesign Canvas §7; content in assets/guide, docs/guide.md): a hub with
 * search, Start here and the groups; a topic page with numbered steps, path chips that open the
 * screen they name ([onLink]), tips, fixes and related topics.
 */
@Composable
fun HelpDialog(onClose: () -> Unit, onLink: (String) -> Unit = {}, startTopic: String? = null) {
    val context = LocalContext.current
    val guide = remember { GuideContent.load(context) }
    var openId by rememberSaveable { mutableStateOf(startTopic) }
    val topic = openId?.let(guide::topic)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (topic != null) {
                BackHandler { openId = null }
                TopicPage(topic, guide, onBack = { openId = null }, onOpen = { openId = it }, onLink = onLink)
            } else {
                Hub(guide, startDone(), onClose, onOpen = { openId = it }, onLink = onLink)
            }
        }
    }
}

/**
 * Start here, ticked from the phone (the Guide's hub, Now's card, onboarding's last page): real
 * glucose coming in (any source but demo data), alarms able to reach you, an emergency contact.
 */
@Composable
internal fun startDone(): List<Boolean> {
    val container = (LocalContext.current.applicationContext as SukoonApp).container
    val source by container.sourceKind.collectAsStateWithLifecycle()
    val latest by container.glucoseRepository.latestReading.collectAsStateWithLifecycle(initialValue = null)
    return listOf(source.real && latest?.source?.real == true, alarmsCanReach(rememberMissingSetup()), container.settings.emergency.contacts.isNotEmpty())
}

/**
 * Alarms reach you when nothing they can't work without is missing: notifications, an unrestricted
 * battery, and full-screen alerts where Android asks. Do Not Disturb, the overlay and texts stay in
 * the checklist; many phones lack or refuse them, and the step shouldn't wait on them.
 */
internal fun alarmsCanReach(missing: List<SetupItem>): Boolean = missing.none { it in CORE_SETUP }

private val CORE_SETUP = setOf(SetupItem.NOTIFICATIONS, SetupItem.BATTERY, SetupItem.FULL_SCREEN)

@Composable
private fun Hub(guide: Guide, done: List<Boolean>, onClose: () -> Unit, onOpen: (String) -> Unit, onLink: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.guide_close), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.help_title), fontFamily = HeadlineSerifFontFamily, fontSize = 28.sp, lineHeight = 34.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        SearchField(query) { query = it }
        if (query.isNotBlank()) {
            val found = guide.search(query)
            if (found.isEmpty()) Text(stringResource(R.string.guide_no_results, query), fontSize = 13.5.sp, color = CaptionMuted)
            TopicList(found, onOpen)
            return@Column
        }
        if (guide.start.isNotEmpty()) StartHere(guide.start, done, eyebrow = stringResource(R.string.guide_start_here), title = stringResource(R.string.guide_start_title), onStep = onLink)
        guide.groups.forEach { group ->
            Eyebrow(group.title)
            TopicList(group.topics, onOpen)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = CaptionMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.guide_search), fontSize = 14.sp, color = CaptionMuted)
            BasicTextField(value, onValue, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground), cursorBrush = SolidColor(Sage), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** "Start here" (hub) or "Get set up" (Now): progress, the three steps, the next one outlined. */
@Composable
internal fun StartHere(steps: List<GuideStart>, done: List<Boolean>, eyebrow: String, title: String, onStep: (String) -> Unit, footer: @Composable () -> Unit = {}) {
    val count = done.count { it }
    val next = done.indexOfFirst { !it }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(eyebrow.uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = SageDeep, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.guide_progress, count, steps.size), fontSize = 12.sp, color = CaptionMuted)
        }
        Text(title, fontFamily = HeadlineSerifFontFamily, fontSize = 20.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(count.toFloat() / steps.size.coerceAtLeast(1)).height(4.dp).background(Sage))
        }
        steps.forEachIndexed { i, step ->
            val isDone = done.getOrElse(i) { false }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onStep(step.link) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .then(if (isDone) Modifier.background(Sage) else Modifier.border(1.5.dp, if (i == next) Sage else CaptionMuted.copy(alpha = 0.5f), CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(14.dp))
                    else Text(String.format(java.util.Locale.getDefault(), "%d", i + 1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (i == next) SageDeep else CaptionMuted)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    step.title,
                    fontSize = 14.sp,
                    fontWeight = if (i == next) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isDone) CaptionMuted else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                if (!isDone) Icon(painterResource(R.drawable.ic_chevron), null, tint = CaptionMuted, modifier = Modifier.size(16.dp))
            }
        }
        footer()
    }
}

@Composable
private fun TopicList(topics: List<GuideTopic>, onOpen: (String) -> Unit) {
    if (topics.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
        topics.forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)))
            Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconCircle(t.icon, 36.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(t.summary, fontSize = 12.5.sp, color = CaptionMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.ic_chevron), null, tint = CaptionMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TopicPage(topic: GuideTopic, guide: Guide, onBack: () -> Unit, onOpen: (String) -> Unit, onLink: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_back), null, tint = SageDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.help_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(SageMist), contentAlignment = Alignment.Center) {
                Icon(painterResource(GuideContent.icon(topic.icon)), null, tint = SageDeep, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(topic.title, fontFamily = HeadlineSerifFontFamily, fontSize = 25.sp, lineHeight = 30.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        Text(topic.forLine, fontSize = 14.sp, lineHeight = 20.sp, color = CaptionMuted)
        topic.sections.forEach { section ->
            section.heading?.let { Eyebrow(it) }
            Steps(section.steps, onLink)
        }
        if (topic.tips.isNotEmpty()) {
            Eyebrow(stringResource(R.string.guide_good_to_know))
            topic.tips.forEach { tip ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                    Icon(painterResource(R.drawable.ic_sparkle), null, tint = Sage, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(tip, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        if (topic.fixes.isNotEmpty()) {
            Eyebrow(stringResource(R.string.guide_if_not))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(StateHigh.copy(alpha = 0.11f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                topic.fixes.forEach { fix ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(fix.problem, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(fix.fix, fontSize = 13.sp, lineHeight = 18.sp, color = CaptionMuted)
                    }
                }
            }
        }
        val related = topic.related.mapNotNull(guide::topic)
        if (related.isNotEmpty()) {
            Eyebrow(stringResource(R.string.guide_related))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                related.forEach { r ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), RoundedCornerShape(50)).clickable { onOpen(r.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(GuideContent.icon(r.icon)), null, tint = SageDeep, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(r.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Numbered steps joined by a rail; a step naming a control carries its path chip and, inside Sukoon, Open. */
@Composable
private fun Steps(steps: List<GuideStep>, onLink: (String) -> Unit) {
    Column {
        steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Sage), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(Sage.copy(alpha = 0.35f)))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.text, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                    step.path?.let { path ->
                        Row(Modifier.clip(RoundedCornerShape(12.dp)).background(SageMist).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(path, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = SageDeep, modifier = Modifier.weight(1f, fill = false))
                            step.link?.let { link ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.guide_open),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Sage).clickable { onLink(link) }.padding(horizontal = 12.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCircle(icon: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
        Icon(painterResource(GuideContent.icon(icon)), null, tint = SageDeep, modifier = Modifier.size(size / 2))
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = CaptionMuted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

// Chevrons and back arrows point the reading way by themselves: their drawables are autoMirrored.
