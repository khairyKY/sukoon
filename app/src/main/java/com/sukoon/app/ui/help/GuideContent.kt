package com.sukoon.app.ui.help

import android.content.Context
import androidx.annotation.DrawableRes
import com.sukoon.app.R
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** One step; [path] is shown as a chip, [link] (a screen in MainScaffold.openLink) adds Open. */
data class GuideStep(val text: String, val path: String? = null, val link: String? = null)

data class GuideSection(val heading: String?, val steps: List<GuideStep>)

data class GuideFix(val problem: String, val fix: String)

data class GuideTopic(
    val id: String,
    val icon: String,
    val title: String,
    /** The one-line "For": what this is for, under the title. */
    val forLine: String,
    /** The shorter line under the title in the hub's list. */
    val summary: String,
    val sections: List<GuideSection>,
    val tips: List<String>,
    val fixes: List<GuideFix>,
    val related: List<String>,
) {
    /** Everything a search can match. */
    val searchable: String by lazy {
        (listOf(title, forLine, summary) + sections.flatMap { s -> listOfNotNull(s.heading) + s.steps.flatMap { listOfNotNull(it.text, it.path) } } + tips + fixes.flatMap { listOf(it.problem, it.fix) })
            .joinToString(" ").lowercase()
    }
}

data class GuideGroup(val title: String, val topics: List<GuideTopic>)

data class GuideStart(val title: String, val link: String)

/** The Guide (docs/guide.md, assets/guide): the three Start here steps and the topics by group. */
data class Guide(val start: List<GuideStart>, val groups: List<GuideGroup>) {
    val topics: List<GuideTopic> get() = groups.flatMap { it.topics }

    fun topic(id: String): GuideTopic? = topics.firstOrNull { it.id == id }

    fun search(query: String): List<GuideTopic> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return if (words.isEmpty()) emptyList() else topics.filter { t -> words.all { it in t.searchable } }
    }
}

object GuideContent {

    /** Egyptian Arabic for Arabic in Egypt (like values-ar-rEG), Standard Arabic for other Arabic, else English. */
    fun fileFor(locale: Locale): String = when {
        locale.language == "ar" && locale.country == "EG" -> "guide/guide-ar-EG.json"
        locale.language == "ar" -> "guide/guide-ar.json"
        else -> "guide/guide-en.json"
    }

    fun load(context: Context): Guide {
        val locale = context.resources.configuration.locales[0]
        val text = context.assets.open(fileFor(locale)).bufferedReader().use { it.readText() }
        return parse(text)
    }

    fun parse(json: String): Guide {
        val root = JSONObject(json)
        fun JSONObject.str(name: String): String? = optString(name).takeIf { has(name) && it.isNotBlank() }
        fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        fun JSONArray?.objects(): List<JSONObject> = this?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        return Guide(
            start = root.optJSONArray("start").objects().map { GuideStart(it.getString("title"), it.getString("link")) },
            groups = root.optJSONArray("groups").objects().map { g ->
                GuideGroup(
                    g.getString("title"),
                    g.optJSONArray("topics").objects().map { t ->
                        GuideTopic(
                            id = t.getString("id"),
                            icon = t.optString("icon"),
                            title = t.getString("title"),
                            forLine = t.optString("for"),
                            summary = t.str("summary") ?: t.optString("for"),
                            sections = t.optJSONArray("sections").objects().map { s ->
                                GuideSection(s.str("heading"), s.optJSONArray("steps").objects().map { GuideStep(it.getString("text"), it.str("path"), it.str("link")) })
                            },
                            tips = t.optJSONArray("tips").strings(),
                            fixes = t.optJSONArray("fixes").objects().map { GuideFix(it.getString("problem"), it.getString("fix")) },
                            related = t.optJSONArray("related").strings(),
                        )
                    },
                )
            },
        )
    }

    /** The topic's icon (named in the JSON). Mapped here, not looked up by name, so resource shrinking keeps them. */
    @DrawableRes
    fun icon(name: String): Int = when (name) {
        "ic_sensor" -> R.drawable.ic_sensor
        "ic_drop" -> R.drawable.ic_drop
        "ic_sync" -> R.drawable.ic_sync
        "ic_help" -> R.drawable.ic_help
        "ic_bell" -> R.drawable.ic_bell
        "ic_phone" -> R.drawable.ic_phone
        "ic_food" -> R.drawable.ic_food
        "ic_insulin" -> R.drawable.ic_insulin
        "ic_sparkle" -> R.drawable.ic_sparkle
        "ic_clock" -> R.drawable.ic_clock
        "ic_eye" -> R.drawable.ic_eye
        "ic_doc" -> R.drawable.ic_doc
        "ic_contrast" -> R.drawable.ic_contrast
        "ic_people" -> R.drawable.ic_people
        "ic_link" -> R.drawable.ic_link
        "ic_lock" -> R.drawable.ic_lock
        else -> R.drawable.ic_help
    }
}
