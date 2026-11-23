package com.alekseevich47.notes.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, scroll the list, open a note, and type 50 characters.
 * The profiled package is the debug install id shared by the debug and optimized builds.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    @Test
    fun scrollOpenAndType() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            scrollTheList()
            openANote()
            typeFiftyCharacters()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.scrollTheList() {
        device.waitForIdle()
        val list = device.findObject(By.scrollable(true))
        if (list != null) {
            list.setGestureMargin(device.displayWidth / 8)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
            return
        }
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 30)
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openANote() {
        device.waitForIdle()
        val existing = device.findObjects(By.descContains("Заметка"))
            .firstOrNull { description ->
                val text = description.contentDescription ?: return@firstOrNull false
                !text.startsWith("Новая")
            }
        if (existing != null) {
            existing.click()
        } else {
            device.wait(Until.hasObject(By.desc("Новая заметка")), 5_000)
            device.findObject(By.desc("Новая заметка"))?.click()
        }
        device.wait(Until.hasObject(By.text(BODY_HINT)), 5_000)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.typeFiftyCharacters() {
        val hint = device.findObject(By.text(BODY_HINT))
        if (hint != null) {
            hint.click()
        } else {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
        }
        device.waitForIdle()
        val sample = "a".repeat(50)
        val focused = device.findObject(By.focused(true))
        if (focused != null) {
            focused.text = sample
        } else {
            typeWithKeyEvents(device, sample)
        }
        device.waitForIdle()
    }

    private fun typeWithKeyEvents(device: UiDevice, text: String) {
        device.executeShellCommand("input text $text")
    }

    private companion object {
        const val TARGET_PACKAGE = "com.alekseevich47.notes.debug"
        const val BODY_HINT = "Начните писать…"
    }
}
