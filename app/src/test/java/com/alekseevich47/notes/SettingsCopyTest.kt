package com.alekseevich47.notes

import com.alekseevich47.notes.data.download.ModelKind
import com.alekseevich47.notes.service.WarmupNotice
import com.alekseevich47.notes.ui.settings.SettingsCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals("Заметки 1.0.5", SettingsCopy.versionLabel("1.0.5-debug"))
        assertEquals("Заметки 1.0.5", SettingsCopy.versionLabel("1.0.5"))
    }

    @Test
    fun warmupCopyNamesOemAutostartBatteryAndUnlock() {
        assertEquals("Автозагрузка моделей", SettingsCopy.WARMUP_TITLE)
        assertEquals("на этом устройстве недоступно", SettingsCopy.WARMUP_UNAVAILABLE)
        val subtitle = SettingsCopy.WARMUP_SUBTITLE
        assertTrue(subtitle.contains("мгновенно"))
        assertTrue(subtitle.contains("автозапуск"))
        assertTrue(subtitle.contains("ограничения батареи"))
        assertTrue(subtitle.contains("обратитесь в поддержку"))
        assertFalse(subtitle.contains("  "))
        assertEquals("Распознавание", SettingsCopy.RECOGNIZER_TITLE)
        assertEquals(
            "Не на всех смартфонах корректно работает \"системное распознавание\". Рекомендуем использовать T-one, предварительно скачав его по кнопке ниже.",
            SettingsCopy.RECOGNIZER_HINT,
        )
        assertEquals("T-one", SettingsCopy.RECOGNIZER_TONE)
        assertEquals("Системное", SettingsCopy.RECOGNIZER_SYSTEM)
        assertEquals("Финальная обработка текста.", ModelKind.GIGA_AM.detail)
        assertEquals("Заметки готовы к записи", WarmupNotice.TEXT)
    }

    @Test
    fun installedModelButtonReplacesTheStaticLabel() {
        assertEquals("Отключить", SettingsCopy.modelSwitchLabel(enabled = true))
        assertEquals("Подключить", SettingsCopy.modelSwitchLabel(enabled = false))
    }
}
