package com.sukoon.app.sharing

/**
 * Invite codes: [LENGTH] characters from [ALPHABET], shown as ABCD-EFGH and sent as a link
 * (web/join.html) that opens Sukoon on Android with the code filled in, or the follow page anywhere else.
 */
object InviteCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O, 1/I

    /** The join page (on GitHub Pages, next to the follow page). */
    const val JOIN_PAGE = "https://khairyky.github.io/sukoon/join.html"

    /** "ABCD-EFGH": easier to read out or type. */
    fun format(code: String) = code.chunked(4).joinToString("-")

    fun link(code: String) = "$JOIN_PAGE?c=$code"

    /** What a code field keeps of [typed]: capitals, only characters a code can have, at most [LENGTH]. */
    fun normalize(typed: String): String = typed.uppercase().filter { it in ALPHABET }.take(LENGTH)

    /** A whole code in pasted [text]: an invite link, "ABCD-EFGH", "abcdefgh" or a message holding one; else null. */
    fun find(text: String): String? {
        Regex("[?&]c=([A-Za-z0-9-]+)").find(text)?.let { m ->
            normalize(m.groupValues[1]).takeIf { it.length == LENGTH }?.let { return it }
        }
        return Regex("(?<![A-Za-z0-9])([A-Za-z0-9]{4})-?([A-Za-z0-9]{4})(?![A-Za-z0-9])").findAll(text)
            .map { (it.groupValues[1] + it.groupValues[2]).uppercase() }
            .firstOrNull { code -> code.all { it in ALPHABET } }
    }
}
