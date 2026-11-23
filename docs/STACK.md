# Заметки — архитектура

Живые заметки по стеку. Обновлять вместе с кодом.

## Продукт

| | |
|--|--|
| Имя | Заметки |
| Package | `com.alekseevich47.notes` (debug + `.debug`) |
| SDK | min 26 / target 35 / compile 35 |
| Версия | 1.0.1 (2) |
| Компания | Loomixx · loomixx.dev@yandex.ru · https://t.me/aalekseevich47 |
| ABI | arm64-v8a, armeabi-v7a |
| JDK | 17 |

## Стек

Kotlin, Jetpack Compose, Material 3, Manrope, Hilt, Room, EncryptedSharedPreferences, OkHttp, Media3 (ExoPlayer + MediaSession), WorkManager, Accompanist permissions, Haze 1.5.2.

Стекло нижнего меню — `LiquidGlassPlate`: Haze blur и полупрозрачная заливка. AGSL / RenderEffect не используем: на Tecno/HiOS он ломает композитинг. Кнопка микрофона 88dp выше плашки 62dp и выступает вверх и вниз.

## Речь

1. Pass 1, пока идёт запись: sherpa-onnx `OnlineToneCtc`, если в `filesDir/models/t-one/` лежат `model.onnx` и `tokens.txt` и движок реально открылся. T-one читает тот же PCM, что и WAV, поэтому SpeechRecognizer в этом режиме не стартует. Иначе `SpeechRecognizer` ru-RU подключается только после того, как `AudioRecord` уже пишет файл. Если системный распознаватель забирает микрофон (на части Tecno/HiOS и других OEM второй клиент эксклюзивен), он останавливается и WAV продолжает писать один `AudioRecord`. Битый или отсутствующий onnx не роняет запись: инициализация возвращает null, финал остаётся текстом первого прохода или пустым. Модели в idle не грузятся.
2. Pass 2, после стопа: GigaAM v3 e2e RNNT (`featureDim=64`, `modelType=nemo_transducer`) из `filesDir/models/gigaam/`. Если финал пустой, остаётся текст первого прохода.
3. AAR `sherpa-onnx-2.0.1.aar` качается в `app/libs/` на этапе конфигурации Gradle и не попадает в git.
4. Источники файлов: Hugging Face `csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08` и `Alexxerm/gigaam-v3-e2e-rnnt-sherpa-onnx`.
5. Аудио: PCM 16 кГц моно в WAV (`filesDir/recordings`), короткий тихий beep перед стартом.

## Загрузка моделей

`DownloadResume` считает Range (`bytes=N-`), прогресс и склейку нескольких файлов. `ModelDownloadManager` пишет `*.partial`, умеет паузу (файл остаётся), стоп (недокачанное удаляется) и продолжение. Обрыв сети ретраится, затем WorkManager (`ModelDownloadWorker`) с `NetworkType.CONNECTED` поднимает загрузку снова. После убийства процесса `recoverInterrupted()` продолжает статус DOWNLOADING.

## Данные и PIN

Room: title, JSON-документ (текст, спаны, таблицы), searchBlob в нижнем регистре, путь к WAV, seq. Поиск фильтрует уже lower-case строку, поэтому кириллица ищется без SQL NOCASE.

PIN хранится только как SHA-256(pepper || pin) в EncryptedSharedPreferences. Сервисный сброс — константа `PinHasher.SERVICE_RESET_HASH`, цифры в исходниках не лежат.

## UI

Нижнее стекло: Заметки / микрофон / Настройки. Состояния микрофона: IDLE (контур и тихая фиолетовая волна), RECORDING (заливка по уровню и glow), PROCESSING (три точки), DONE (галочка). После стопа открывается редактор.

Редактор: жирный, курсив, зачёркнутый, подчёркнутый, скрытый (мозаика поверх текста того же цвета, без AGSL; тап открывает свой PIN), нумерованные списки (`ListAutoFormat`), таблицы rows×cols с сеткой выбора размера и редактируемыми ячейками. Диалоги и тосты свои, не системные. Календарь — свой month grid, не `DatePickerDialog`.

Тема: около-белый светлый фон и тёмная схема, переключатель в настройках (система / светлая / тёмная).

## Виджеты и сервисы

- 2×2 и 4×2: те же фазы микрофона. Пока идёт запись, иконка пульсирует по кадрам; обработка листает шесть кадров точек; готово — короткая вспышка галочки. У широкого справа до четырёх строк транскрипта: новая строка проявляется по альфе, старые выше и бледнее.
- `RecordingService` — FGS `microphone`, wake lock, уведомление «заметка готова» открывает note id.
- `PlaybackService` — ExoPlayer, MediaSession, FGS `mediaPlayback`, play/pause и seek ±10 с. В заметке свой мини-плеер.

## CI

`.github/workflows/android.yml`: JDK 17, SDK 35, `testDebugUnitTest`, `assembleDebug`, `assembleOptimized`, артефакты `notes-debug-apk` и `notes-optimized-apk`.
