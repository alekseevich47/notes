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

Стекло нижнего меню — `LiquidGlassPlate`: Haze blur и полупрозрачная заливка. AGSL / RenderEffect не используем: на Tecno/HiOS он ломает композитинг. Кнопка микрофона 88dp выше плашки 62dp и выступает вверх и вниз. Кнопка «+» на своей плите ставит `blur = false` и не блюрит себя. Нижняя плашка блюр оставляет.

## Речь

1. Pass 1, пока идёт запись: sherpa-onnx `OnlineToneCtc`, если в `filesDir/models/t-one/` лежат `model.onnx` и `tokens.txt` и движок реально открылся. T-one читает тот же PCM, что и WAV, поэтому SpeechRecognizer в этом режиме не стартует. Иначе `SpeechRecognizer` ru-RU подключается только после того, как `AudioRecord` уже пишет файл. Если системный распознаватель забирает микрофон (на части Tecno/HiOS и других OEM второй клиент эксклюзивен), он останавливается и WAV продолжает писать один `AudioRecord`. Битый или отсутствующий onnx не роняет запись: инициализация возвращает null, финал остаётся текстом первого прохода или пустым. Модели в idle не грузятся.
2. Pass 2, после стопа: GigaAM v3 e2e RNNT (`featureDim=64`, `modelType=nemo_transducer`) из `filesDir/models/gigaam/`. Если финал пустой, остаётся текст первого прохода.
3. AAR `sherpa-onnx-2.0.1.aar` качается в `app/libs/` на этапе конфигурации Gradle и не попадает в git.
4. Источники файлов: Hugging Face `csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08` и `Alexxerm/gigaam-v3-e2e-rnnt-sherpa-onnx`.
5. Аудио: PCM 16 кГц моно в WAV (`filesDir/recordings`), короткий тихий beep перед стартом.

Выбор распознавателя не остаётся на T-one, пока модель не установлена и не включена. Иначе эффективное значение — system (`RecognizerChoice`).

## Загрузка моделей

`DownloadResume` считает Range (`bytes=N-`), прогресс и склейку нескольких файлов. `ModelDownloadManager` пишет `*.partial`, умеет паузу (файл остаётся), стоп (недокачанное удаляется) и продолжение. Обрыв сети ретраится, затем WorkManager (`ModelDownloadWorker`) с `NetworkType.CONNECTED` поднимает загрузку снова. После убийства процесса `recoverInterrupted()` продолжает статус DOWNLOADING.

## Данные и PIN

Room: title, JSON-документ (текст, спаны, таблицы), searchBlob в нижнем регистре, путь к WAV, seq. Поиск фильтрует уже lower-case строку, поэтому кириллица ищется без SQL NOCASE. searchBlob не включает символы внутри спанов HIDDEN. Room is version 2 with a createdAt index and a non-destructive migration.

PIN хранится только как SHA-256(pepper || pin) в EncryptedSharedPreferences. Сервисный сброс — константа `PinHasher.SERVICE_RESET_HASH`, цифры в исходниках не лежат.

## UI

Нижнее стекло: Заметки / микрофон / Настройки. Состояния микрофона: IDLE (контур и тихая фиолетовая волна), RECORDING (заливка по уровню и glow), PROCESSING (три точки), DONE (галочка). После стопа открывается редактор.

Карта поведения официального Telegram-Android (курсор, выделение, попапы, автоскролл, таблицы display-only): `docs/TELEGRAM_ANDROID_EDITOR_MAP.md` (ревизия `21f7d10`).

Редактор: жирный, курсив, зачёркнутый, подчёркнутый, скрытый (мозаика поверх текста того же цвета, без AGSL; тап открывает свой PIN), нумерованные списки (`ListAutoFormat`), таблицы rows×cols с сеткой выбора размера и редактируемыми ячейками. Диалоги и тосты свои, не системные. Календарь — свой month grid, не `DatePickerDialog`. Меню выделения — одна рамка `SelectionBubble`: короткое «Вырезать / Копировать / ⋮», «Вставить» первым пунктом расширенного меню, если буфер не пуст. Положение считается по `reserve` и видимой области под шапкой: по умолчанию над выделением, под ним только у верха, вне экрана закрепляется у границы. При тяге ручек и скролле меню плавно гаснет и возвращается после паузы; пока оно скрыто, `Popup` не висит над текстом. Плавающее «Вставить» у каретки открывается только долгим нажатием на пустое место. В ячейке таблицы «Вставить» — первая строка меню ячейки. Тяга ручки к краю клавиатуры автоскроллит заметку с нарастанием скорости. Тап внизу заметки докручивает каретку над клавиатурой. Системный текстовый тулбар не показывается.

Список заметок показывает время создания. Кэш превью по-прежнему ключуется по `updatedAt`.

Тема: светлая или тёмная. Сохранённое `SYSTEM` один раз разрешается по теме устройства при первом запуске этой версии и дальше не меняется. The theme is read from plain NotesPrefs.

При первом запуске `InfoNoticeStore` показывает «Информация». «Продолжить» сохраняет согласие с `TermsOfUse` версии 1.0. В настройках тот же текст открывает пункт «Правила использования».

«Сообщить об ошибке» открывает почтовое приложение пользователя. SMTP и секрета в приложении нет. Скриншоты перекодируются в JPEG без EXIF. Технический журнал прилагается по желанию.

## Виджеты и сервисы

- 2×2 и 4×2: те же фазы микрофона. Пока идёт запись, иконка пульсирует по кадрам; обработка листает шесть кадров точек; готово — короткая вспышка галочки. У широкого справа до четырёх строк транскрипта: новая строка проявляется по альфе, старые выше и бледнее.
- `RecordingService` — FGS `microphone`, wake lock, уведомление «заметка готова» открывает note id.
- `PlaybackService` — ExoPlayer, MediaSession, FGS `mediaPlayback`, play/pause и seek ±10 с. В заметке свой мини-плеер.

## Журнал

Логи идут только через `AppLog`. `android.util.Log` в коде приложения не используется, кроме зеркала внутри `AppLog`. Шапка процесса содержит память, диск, модели, разрешения, батарею и тему. На старте процесса в тело пишется маркер `===== process start pid=… session=… =====`. На API 30+ туда же попадают причины прошлых завершений.

## CI

`.github/workflows/android.yml`: JDK 17, SDK 35, `testDebugUnitTest`, `assembleDebug`, `assembleOptimized`, артефакты `notes-debug-apk`, `notes-optimized-apk` и `notes-optimized-mapping`.

Стеки optimized расшифровываются командой `retrace mapping.txt trace.txt` (R8 retrace из cmdline-tools); mapping — артефакт `notes-optimized-mapping`.
