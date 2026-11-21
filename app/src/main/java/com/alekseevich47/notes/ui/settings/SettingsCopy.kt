package com.alekseevich47.notes.ui.settings

object SettingsCopy {
    const val MODELS =
        "Без установки моделей распознавания возможно некорректная работа как самого приложения, так и процесса распознавания текста."

    const val PIN =
        "ПИН-код необходим для работы функции \"Скрытый текст\". Для сброса забытого Вами ПИН-кода обратитесь к разработчикам по контактам ниже."

    const val EMAIL = "loomixx.dev@yandex.ru"
    const val TELEGRAM_HANDLE = "@alekseevich47"
    const val TELEGRAM_URL = "https://t.me/alekseevich47"

    const val WARMUP_TITLE = "Автозагрузка моделей"
    const val WARMUP_SUBTITLE =
        "На Tecno, Xiaomi и Samsung также нужны системный автозапуск и работа без ограничений батареи. Модели загружаются только после разблокировки экрана. Нажатие открывает эти системные экраны."

    const val RECOGNIZER_TITLE = "Распознавание"
    const val RECOGNIZER_TONE = "T-one"
    const val RECOGNIZER_SYSTEM = "Системное"

    fun versionLabel(versionName: String): String {
        return "Заметки " + versionName.removeSuffix("-debug")
    }

    fun modelSwitchLabel(enabled: Boolean): String {
        return if (enabled) "Отключить" else "Подключить"
    }
}
