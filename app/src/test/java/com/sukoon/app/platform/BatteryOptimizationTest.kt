package com.sukoon.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationTest {

    @Test
    fun `known aggressive OEMs each get at least one candidate`() {
        listOf("Xiaomi", "HUAWEI", "honor", "samsung", "OPPO", "realme", "vivo").forEach { manufacturer ->
            assertTrue(
                "expected at least one candidate for $manufacturer",
                BatteryOptimization.candidatesFor(manufacturer).isNotEmpty(),
            )
        }
    }

    @Test
    fun `manufacturer match is case-insensitive`() {
        assertEquals(
            BatteryOptimization.candidatesFor("xiaomi"),
            BatteryOptimization.candidatesFor("XIAOMI"),
        )
    }

    @Test
    fun `unrecognized manufacturer falls back to no candidates`() {
        assertTrue(BatteryOptimization.candidatesFor("Google").isEmpty())
        assertTrue(BatteryOptimization.candidatesFor("").isEmpty())
    }

    @Test
    fun `huawei has a fallback candidate if the first component is missing`() {
        // Two Huawei ROM generations use different activity names — both must be listed so
        // openOemBackgroundSettings can fall through to the second if the first isn't present.
        assertTrue(BatteryOptimization.candidatesFor("HUAWEI").size >= 2)
    }
}
