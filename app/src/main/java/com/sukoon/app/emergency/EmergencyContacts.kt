package com.sukoon.app.emergency

import org.json.JSONArray
import org.json.JSONObject

/** Someone to text (and, if first in the list, call) when an urgent low goes unanswered. */
data class EmergencyContact(val name: String, val phone: String)

/** You → Emergency contacts. The first contact is the one Sukoon calls. */
data class EmergencySettings(
    val contacts: List<EmergencyContact> = emptyList(),
    /** How the texts name the user ("Kai's glucose is…"). */
    val yourName: String = "",
    val afterMinutes: Int = 10,
    val shareLocation: Boolean = false,
) {
    fun sanitized() = copy(afterMinutes = afterMinutes.coerceIn(AFTER_RANGE))

    companion object {
        val AFTER_CHOICES = listOf(5, 10, 15, 30)
        val AFTER_RANGE = 3..60

        fun contactsToJson(contacts: List<EmergencyContact>): String =
            JSONArray(contacts.map { JSONObject().put("name", it.name).put("phone", it.phone) }).toString()

        /** Tolerant: a corrupt value means no contacts, never a crash in the alarm path. */
        fun contactsFromJson(json: String?): List<EmergencyContact> = runCatching {
            val array = JSONArray(json ?: return emptyList())
            (0 until array.length()).map { i -> array.getJSONObject(i).let { EmergencyContact(it.optString("name"), it.getString("phone")) } }
        }.getOrDefault(emptyList())
    }
}

object PhoneNumbers {

    /**
     * International form, so texts, calls and WhatsApp all work: "+20 101 234 5678", "00201012345678"
     * and — with [callingCode] 20 — "01012345678" all become "+201012345678". Anything else is kept as typed.
     */
    fun normalize(raw: String, callingCode: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            raw.trim().startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.startsWith("0") -> "+$callingCode" + digits.drop(1)
            else -> digits
        }
    }

    /** Country calling code for the SIM's country (ISO 3166 alpha-2); Egypt when unknown. */
    fun callingCodeFor(countryIso: String?): String = CALLING_CODES[countryIso?.lowercase()] ?: "20"

    /** wa.me links take the international number as bare digits. */
    fun whatsappDigits(phone: String) = phone.filter { it.isDigit() }

    private val CALLING_CODES = mapOf(
        "eg" to "20", "sa" to "966", "ae" to "971", "kw" to "965", "qa" to "974", "bh" to "973", "om" to "968",
        "jo" to "962", "lb" to "961", "iq" to "964", "sd" to "249", "ly" to "218", "ma" to "212", "tn" to "216",
        "dz" to "213", "tr" to "90", "gb" to "44", "de" to "49", "fr" to "33", "it" to "39", "es" to "34",
        "nl" to "31", "us" to "1", "ca" to "1",
    )
}
