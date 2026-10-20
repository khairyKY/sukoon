package com.sukoon.app.ui.onboarding

import com.sukoon.app.data.prefs.UserRole
import com.sukoon.app.ui.onboarding.Step.ACCOUNT
import com.sukoon.app.ui.onboarding.Step.ALARMS
import com.sukoon.app.ui.onboarding.Step.CODE
import com.sukoon.app.ui.onboarding.Step.FOLLOW_ALERTS
import com.sukoon.app.ui.onboarding.Step.PERMISSIONS
import com.sukoon.app.ui.onboarding.Step.READY
import com.sukoon.app.ui.onboarding.Step.ROLE
import com.sukoon.app.ui.onboarding.Step.SENSOR
import com.sukoon.app.ui.onboarding.Step.WELCOME
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPathTest {

    @Test fun `a wearer goes straight to the sensor, no account`() =
        assertEquals(listOf(WELCOME, ROLE, SENSOR, PERMISSIONS, ALARMS, READY), path(UserRole.WEARER))

    @Test fun `a follower needs the account before the code`() =
        assertEquals(listOf(WELCOME, ROLE, ACCOUNT, CODE, FOLLOW_ALERTS), path(UserRole.FOLLOWER))

    @Test fun `both pairs first, then the account to follow`() =
        assertEquals(listOf(WELCOME, ROLE, SENSOR, PERMISSIONS, ALARMS, ACCOUNT, CODE, FOLLOW_ALERTS, READY), path(UserRole.BOTH))

    @Test fun `I have an account signs in right after the role, once`() {
        assertEquals(listOf(WELCOME, ROLE, ACCOUNT, SENSOR, PERMISSIONS, ALARMS, READY), path(UserRole.WEARER, haveAccount = true))
        assertEquals(listOf(WELCOME, ROLE, ACCOUNT, CODE, FOLLOW_ALERTS), path(UserRole.FOLLOWER, haveAccount = true))
        assertEquals(listOf(WELCOME, ROLE, ACCOUNT, SENSOR, PERMISSIONS, ALARMS, CODE, FOLLOW_ALERTS, READY), path(UserRole.BOTH, haveAccount = true))
    }
}
