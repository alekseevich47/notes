package com.alekseevich47.notes

import com.alekseevich47.notes.service.OemPowerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemPowerSettingsTest {
    @Test
    fun batteryPromptWinsWhileTheAppIsStillOptimized() {
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = false,
            batteryPromptResolves = true,
            batteryListResolves = true,
            oemResolved = OemPowerSettings.OEM_CANDIDATES.map { true },
        )
        assertEquals(OemPowerSettings.Kind.BATTERY_PROMPT, choice.kind)
        assertNull(choice.oem)
    }

    @Test
    fun batteryListIsUsedWhenThePromptDoesNotResolve() {
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = false,
            batteryPromptResolves = false,
            batteryListResolves = true,
            oemResolved = listOf(true),
        )
        assertEquals(OemPowerSettings.Kind.BATTERY_LIST, choice.kind)
        assertNull(choice.oem)
    }

    @Test
    fun oemAutostartOpensOnceBatteryIsAlreadyUnrestricted() {
        val flags = MutableList(OemPowerSettings.OEM_CANDIDATES.size) { false }
        val xiaomi = OemPowerSettings.OEM_CANDIDATES.indexOfFirst { it.packageName == "com.miui.securitycenter" }
        flags[xiaomi] = true
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = true,
            batteryPromptResolves = true,
            batteryListResolves = true,
            oemResolved = flags,
        )
        assertEquals(OemPowerSettings.Kind.OEM, choice.kind)
        assertEquals(OemPowerSettings.OEM_CANDIDATES[xiaomi], choice.oem)
        assertEquals(
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
            choice.oem?.className,
        )
    }

    @Test
    fun firstResolvedOemActivityIsTheOnlyOne() {
        val flags = MutableList(OemPowerSettings.OEM_CANDIDATES.size) { index -> index >= 1 }
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = true,
            batteryPromptResolves = false,
            batteryListResolves = true,
            oemResolved = flags,
        )
        assertEquals(OemPowerSettings.Kind.OEM, choice.kind)
        assertEquals(OemPowerSettings.OEM_CANDIDATES[1], choice.oem)
    }

    @Test
    fun appDetailsWhenBatteryIsUnrestrictedAndNoOemResolves() {
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = true,
            batteryPromptResolves = true,
            batteryListResolves = true,
            oemResolved = emptyList(),
        )
        assertEquals(OemPowerSettings.Kind.APP_DETAILS, choice.kind)
        assertNull(choice.oem)
    }

    @Test
    fun appDetailsWhenNothingResolves() {
        val choice = OemPowerSettings.choose(
            ignoringBatteryOptimizations = false,
            batteryPromptResolves = false,
            batteryListResolves = false,
            oemResolved = OemPowerSettings.OEM_CANDIDATES.map { false },
        )
        assertEquals(OemPowerSettings.Kind.APP_DETAILS, choice.kind)
    }

    @Test
    fun candidatesCoverTranssionXiaomiAndSamsung() {
        val candidates = OemPowerSettings.OEM_CANDIDATES
        assertTrue(candidates.any { it.packageName == "com.transsion.phonemaster" && it.className == "com.cyin.himgr.autostart.AutoStartActivity" })
        assertTrue(candidates.any { it.packageName == "com.transsion.phonemanager" })
        assertTrue(candidates.any { it.packageName == "com.mediatek.autobootcontroller" })
        assertTrue(candidates.any { it.packageName == "com.miui.securitycenter" })
        assertTrue(candidates.any { it.action == "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY" && it.activityType == 0 })
        assertTrue(candidates.any { it.className == "com.samsung.android.sm.ui.battery.BatteryActivity" })
        assertTrue(candidates.any { it.className == "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity" })
        val transsion = candidates.indexOfFirst { it.packageName.startsWith("com.transsion.") }
        val xiaomi = candidates.indexOfFirst { it.packageName == "com.miui.securitycenter" }
        val samsung = candidates.indexOfFirst { it.packageName.startsWith("com.samsung.") }
        assertTrue(transsion in 0 until xiaomi)
        assertTrue(xiaomi < samsung)
    }
}
