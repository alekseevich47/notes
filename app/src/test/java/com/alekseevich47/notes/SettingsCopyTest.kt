package com.alekseevich47.notes

import com.alekseevich47.notes.ui.settings.SettingsCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCopyTest {
    @Test
    fun modelsAndPinParagraphsMatchTheReleaseCopy() {
        assertEquals(
            "Без установки моделей распознавания возможно некорректная работа как самого приложения, так и процесса распознавания текста.",
            SettingsCopy.MODELS,
        )
        assertEquals(
            "ПИН-код необходим для работы функции \"Скрытый текст\". Для сброса забытого Вами ПИН-кода обратитесь к разработчикам по контактам ниже.",
            SettingsCopy.PIN,
        )
    }

    @Test
    fun aboutRowUsesTheMailAddressAndTelegramHandle() {
        assertEquals("loomixx.dev@yandex.ru", SettingsCopy.EMAIL)
        assertEquals("@alekseevich47", SettingsCopy.TELEGRAM_HANDLE)
        assertEquals("https://t.me/alekseevich47", SettingsCopy.TELEGRAM_URL)
    }

    @Test
    fun versionLabelStripsTheDebugSuffix() {
        assertEquals("Заметки 1.0.4", SettingsCopy.versionLabel("1.0.4-debug"))
        assertEquals("Заметки 1.0.4", SettingsCopy.versionLabel("1.0.4"))
    }

    @Test
    fun installedModelButtonReplacesTheStaticLabel() {
        assertEquals("Отключить", SettingsCopy.modelSwitchLabel(enabled = true))
        assertEquals("Подключить", SettingsCopy.modelSwitchLabel(enabled = false))
    }
}
