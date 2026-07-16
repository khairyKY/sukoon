package com.sukoon.app.domain.units

import com.sukoon.app.domain.metrics.GlucoseMetrics
import java.util.Locale

/**
 * Display unit for a glucose value. Canonical storage/math is always mg/dL (docs/PLAN.md §3) —
 * this only governs how a value is *formatted*.
 */
enum class GlucoseUnit {
    MG_DL,
    MMOL_L;

    /**
     * Formats a canonical mg/dL value for display in this unit. mg/dL shows as a whole number;
     * mmol/L shows to one decimal place. Locale is forced to US so the decimal separator is
     * always a period — never a comma — regardless of device locale (this app ships Arabic;
     * a locale-flipped decimal on a glucose number is a real safety concern, not cosmetic).
     */
    fun format(glucoseMgDl: Int): String = when (this) {
        MG_DL -> glucoseMgDl.toString()
        MMOL_L -> String.format(Locale.US, "%.1f", GlucoseMetrics.mgDlToMmolL(glucoseMgDl))
    }
}
