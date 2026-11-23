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
    const val WARMUP_UNAVAILABLE = "на этом устройстве недоступно"
    const val WARMUP_SUBTITLE =
        "Эта настройка нужна, чтобы записываемый текст отображался и обрабатывался мгновенно. На некоторых моделях телефона требуется в системных настройках установить автозапуск и работа без ограничения батареи. Нажатие на этот текст откроет возможные системные параметры. При возникновении трудностей обратитесь в поддержку по контактам ниже."

    const val RECOGNIZER_TITLE = "Распознавание"
    const val RECOGNIZER_HINT =
        "Не на всех смартфонах корректно работает \"системное распознавание\". Рекомендуем использовать T-one, предварительно скачав его по кнопке ниже."
    const val RECOGNIZER_TONE = "T-one"
    const val RECOGNIZER_SYSTEM = "Системное"

    fun versionLabel(versionName: String): String {
        return "Заметки " + versionName.removeSuffix("-debug")
    }

    fun modelSwitchLabel(enabled: Boolean): String {
        return if (enabled) "Отключить" else "Подключить"
    }
}
