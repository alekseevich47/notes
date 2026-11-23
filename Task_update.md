# Task_update — оптимизация и доработка редактора

Проект: `com.alekseevich47.notes` (Kotlin 2.0.21, Compose BOM 2024.10.01, Room 2.6.1, Hilt, Haze 1.5.2, minSdk 26).
Цель: плавная работа на слабых Android-смартфонах, исправление багов редактора, поведение части функций как в Telegram.

## 0. Правила для исполнителя

- Один шаг = один коммит. После каждого шага выполнять `./gradlew testDebugUnitTest assembleDebug` (после фазы 1 ещё и `assembleOptimized`). Если сборка красная, дальше не идти.
- Не менять формат хранения так, чтобы старые заметки перестали открываться. Новые поля добавлять только с default-значениями.
- Не использовать `fallbackToDestructiveMigration`: она удалит заметки пользователей.
- Не трогать STT (`stt/*`), запись и виджеты, если шаг этого прямо не требует.
- AGSL и RuntimeShader не использовать: на Tecno/HiOS ломается композитинг (см. `docs/STACK.md`).
- Новую чистую логику (без Compose) класть в `object`/`internal fun` и покрывать JVM-тестами в `app/src/test/...`, по образцу `ListAutoFormatTest`.
- Приоритеты: **P1** — обязательно, **P2** — желательно, **P3** — по желанию.
- `graphify-out/` в проекте нет, команда `graphify update` не нужна.

---

## Фаза 1. Сборка и рантайм (P1)

### 1.1 Оптимизированная сборка, которая ставится поверх debug
Сейчас CI собирает только debug. Compose в debug-сборке работает в 2–5 раз медленнее: нет R8, лямбды не оптимизированы. Это главная причина подтормаживаний на слабых устройствах.
- `app/build.gradle.kts`, `buildTypes`: добавить
  `create("optimized") { initWith(getByName("release")); applicationIdSuffix = ".debug"; versionNameSuffix = "-opt"; signingConfig = signingConfigs.getByName("debug"); matchingFallbacks += "release"; isDebuggable = false }`.
  Пакет и подпись те же, что у debug, поэтому APK обновляет установленную debug-версию без потери заметок.
- `.github/workflows/android.yml`: после `assembleDebug` добавить шаг `./gradlew assembleOptimized` и артефакт `notes-optimized-apk` (`app/build/outputs/apk/optimized/*.apk`).
- Готово, когда APK ставится поверх debug-сборки, заметки на месте, запись и распознавание работают (проверка `proguard-rules.pro` для sherpa).

### 1.2 Стабильность типов для Compose
- Создать `compose_stability.conf` в корне репозитория:
  ```
  com.alekseevich47.notes.domain.*
  com.alekseevich47.notes.player.PlayerUi
  com.alekseevich47.notes.player.PlaybackSnapshot
  kotlin.collections.*
  java.time.LocalDate
  ```
- `app/build.gradle.kts`: `composeCompiler { stabilityConfigurationFile = rootProject.layout.projectDirectory.file("compose_stability.conf") }`.
- Перед этим убедиться, что все классы в `domain/Models.kt` неизменяемые: только `val` и read-only `List`. Сейчас это так.

### 1.3 Baseline Profile (P2)
- Добавить `implementation("androidx.profileinstaller:profileinstaller:1.4.1")`.
- Создать модуль `:baselineprofile` с плагином `androidx.baselineprofile` 1.3.x и генератором на сценарии: холодный старт, прокрутка списка, открытие заметки, набор 50 символов.
- Сгенерированный файл `app/src/main/generated/baselineProfiles/baseline-prof.txt` закоммитить. В CI генерацию не запускать, нужен эмулятор.

### 1.4 Холодный старт
- `MainActivity.onStart`: убрать `ModelWarmupService.start(this)`. Его уже вызывает `NotesApp` (`ProcessLifecycleOwner.onStart`).
- `ModelWarmupService.start()`: не запускать прогрев, если `ActivityManager.isLowRamDevice()` или `memoryClass < 192`. Модели занимают сотни МБ, и на 2–3 ГБ ОЗУ системный убийца процессов начинает выгружать UI. В Настройках показывать подпись «на этом устройстве недоступно».
- (P2) Тема не секрет. Читать `themeMode` из обычных `NotesPrefs`, а не из `EncryptedSharedPreferences`: открытие Keystore на главном потоке занимает 50–300 мс на слабых SoC. Миграция: если в `NotesPrefs` нет ключа темы, один раз скопировать его из secure-хранилища.

---

## Фаза 2. Слой данных (P1)

Проблема: `NotesRepository.observe` на **каждое** изменение таблицы декодирует JSON **всех** заметок на главном потоке (`stateIn(viewModelScope)` работает на Main). Автосохранение в редакторе срабатывает каждые 280 мс, а `NotesRoot` собирает `notesVm.notes` всегда, даже когда открыт редактор. В итоге набор текста тормозит тем сильнее, чем больше заметок.

### 2.1 Поиск в SQL и работа вне главного потока
- `data/local/NoteDao.kt`: новый запрос
  ```sql
  SELECT * FROM notes
  WHERE (:fromMs IS NULL OR createdAt >= :fromMs)
    AND (:toMs IS NULL OR createdAt < :toMs)
    AND (:needle = '' OR searchBlob LIKE '%' || :needle || '%' ESCAPE '\')
  ORDER BY createdAt DESC, id DESC
  ```
- `NotesRepository.observe`: экранировать в `needle` символы `\`, `%`, `_`. Фильтрацию в Kotlin удалить. Маппинг делать с `.flowOn(Dispatchers.Default)` и `distinctUntilChanged()`.
- Добавить UI-модель `NoteListItem(id, title, preview: List<PreviewPart>, updatedAt, hasAudio)` (`@Immutable`) в `domain/Models.kt`. Repository отдаёт `Flow<List<NoteListItem>>`. `previewParts` считать в репозитории, а не в composable.
- Кэш `LruCache<Long, Pair<Long, NoteListItem>>` (ключ — id, значение — updatedAt и item) на 256 записей. JSON заново декодировать только у изменившихся заметок.

### 2.2 Индекс и схема (P2)
- `NoteEntity`: `@Entity(tableName = "notes", indices = [Index(value = ["createdAt"])])`.
- `NotesDatabase`: `version = 2`, `exportSchema = true`. В `build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
- `AppModule`: `.addMigrations(MIGRATION_1_2)` с SQL `CREATE INDEX IF NOT EXISTS index_notes_createdAt ON notes(createdAt)`.
- Закоммитить `app/schemas/.../1.json` и `2.json`. Для `1.json` собрать предыдущий коммит с `exportSchema = true`.

### 2.3 Сохранение заметки
- `EditorViewModel.scheduleSave/flush/close`: кодировать JSON и считать `searchBlob` в `withContext(Dispatchers.Default)`.
- Хранить в VM `lastSaved: Note?`. `repository.save` вызывать, только если `snapshot != lastSaved`. Так уходят лишние `dao.get` и `decode` в `NotesRepository.save`. В `save` оставить проверку `sameStored` как страховку.
- `EditorScreen`: `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }`. Сейчас `flush()` нигде не вызывается, и если процесс убьют в фоне в течение 280 мс после ввода, правка потеряется.

### 2.4 Удаление пачкой
- `NoteDao`: `@Query("SELECT * FROM notes WHERE id IN (:ids)")` и `@Query("DELETE FROM notes WHERE id IN (:ids)")`.
- `NotesRepository.deleteMany(ids)`: удалить аудиофайлы на `Dispatchers.IO`, затем удалить строки одним запросом в `withTransaction`. `NotesViewModel.deleteMany` переключить на этот метод.

---

## Фаза 3. Перекомпозиции оболочки и списка (P1)

### 3.1 Уровень громкости и живой текст
`NotesRoot` читает `level` и `live` на верхнем уровне, поэтому весь корень перекомпонуется 20–50 раз в секунду во время записи.
- `NotesRoot`: убрать `val level by ...collectAsState()` и `val live by ...`. В `GlassBottomBar` и `MicButton` передавать `level: () -> Float`, в `RecordSheet` — `text: () -> String`. Читать значения внутри листового composable (`collectAsStateWithLifecycle` там же) и в draw-фазе.
- Во всех экранах заменить `collectAsState()` на `collectAsStateWithLifecycle()`.
- `notesVm.notes` собирать только в ветке `Screen.LIST`.

### 3.2 `NotesListScreen`
- `NoteCard` принимает `NoteListItem` (из 2.1) и не зовёт `NoteDocuments.previewParts`.
- `formatStamp`: вынести `DateTimeFormatter` в `private val` верхнего уровня, сейчас он создаётся в каждой карточке. `ZoneId` брать один раз.
- Заменить `pointerInput(selecting){ detectTapGestures }` на `combinedClickable(onClick, onLongClick)`: появятся ripple, доступность и правильная отмена при прокрутке.
- `LazyColumn`: `contentType = { "note" }`.
- `SpoilerPreview`: если в `parts` нет скрытых кусков, рисовать обычный `Text` без `rememberGraphicsLayer`/`TextMeasurer`. Сейчас на каждую карточку выделяется RenderNode.

### 3.3 `hazeSource` (P2)
- `NotesRoot`: оборачивать в `hazeSource` только `LIST` и `SETTINGS`. Редактор вынести из этого `Box`: в редакторе нет стеклянных элементов, а слой всё равно записывается.

---

## Фаза 4. Графика и анимации (P1/P2)

### 4.1 Уровень производительности устройства
- Новый файл `ui/theme/PerfTier.kt`: `enum class PerfTier { LOW, NORMAL }`, `CompositionLocal LocalPerfTier`, `val LocalReduceMotion`.
  - LOW, если `isLowRamDevice`, или `SDK < 28`, или `availableProcessors() <= 4`, или `memoryClass < 192`.
  - Reduce motion: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`.
- Значения передавать в `NotesTheme`. Использовать в 4.2–4.5 и 7.6.

### 4.2 `MicButton`
- В `IDLE` волна крутится бесконечно (3 бесконечные анимации), а спецификация требует «не грузить CPU в idle». Запускать `rememberInfiniteTransition` только для фаз `RECORDING`/`PROCESSING`. В `IDLE` при `PerfTier.LOW` или reduce motion рисовать статичную волну. В остальных случаях — медленную волну, только пока lifecycle в `RESUMED`.
- `outlineMic(size)` вызывается в каждом кадре и создаёт `Path`. Перенести в `drawWithCache { val glyph = outlineMic(size); onDrawBehind { ... } }`.
- `wavePath`: переиспользовать один `Path` через `rewind()` вместо нового объекта на кадр.
- `level` из 3.1 читать в draw-лямбде. Сглаживание (`animateFloatAsState`) оставить.

### 4.3 `SoftShadow`
- `drawSoftShapeShadow` на каждый draw делает до 40 `drawRoundRect` и создаёт `Path`. Перевести `softShadowPadding` и `SoftShadowLayer` на `drawWithCache`: путь и шаги считать один раз на размер.
- Для `PerfTier.LOW` `ShadowSteps = 12`.

### 4.4 `LiquidGlassPlate`
- `PerfTier.LOW` и `SDK < 31`: не вызывать `hazeEffect`, рисовать `palette.fill` с alpha 0.94 плюс sheen и border. Визуально почти то же, но без двух проходов размытия на кадр.

### 4.5 Размытие скрытого текста (`SpoilerText.kt`)
- `drawBlurred` в каждом draw создаёт `BlurEffect` и перезаписывает `GraphicsLayer`. Перенести в `Modifier.drawWithCache`: записывать слой только при смене `layout`/`size`/`ink`.
- `drawSmear` (API < 31) делает 16 `drawText` на кадр. Один раз отрисовать в `ImageBitmap` внутри `drawWithCache` и потом рисовать готовый битмап.

---

## Фаза 5. Производительность редактора (P1)

Файлы: `ui/notes/EditorScreen.kt`, `ui/editor/RichEditor.kt`, `ui/notes/NotesViewModels.kt`, `ui/editor/ListAutoFormat.kt`.

### 5.1 Убрать двойную вёрстку текста на каждую клавишу
`KeepCaretVisible` на каждое нажатие заново меряет **весь** текст через `TextMeasurer` и пишет `top` через `onGloballyPositioned`.
- Удалить из `EditorScreen` composable `KeepCaretVisible`, `annotatedNote`, state `top` и `onGloballyPositioned` у `NoteTextBlock`. Чистые функции `inferCaretAfterEdit`, `revealCaretDelta`, `trailingCaretBottom` и их тесты оставить.
- В `RichNoteEditor`: `val bring = remember { BringIntoViewRequester() }`, к полю добавить `Modifier.bringIntoViewRequester(bring)`. В `onTextLayout` есть готовый `layout`. После правки текста при фокусе (флаг `pendingReveal = true` ставить в `onValueChange`, если текст изменился) запускать `scope.launch { bring.bringIntoView(layout.getCursorRect(sel.end).inflate(16.dp.px)) }` и сбрасывать флаг.
- Проверка: в длинной заметке Enter внизу экрана держит курсор над клавиатурой и над `EditorFormatBar`; прокрутка не прыгает при перемещении курсора без ввода.

### 5.2 Стабильные параметры блоков
- `EditorScreen`: `val flow = remember(current.document) { NoteDocuments.flow(current.document) }`, то же для `structure` и `lastText`.
- `onDeleteTableAbove = run { ... }` создаёт новую лямбду с захватом `current.document` на каждую клавишу, и из-за этого перекомпонуются **все** блоки. Заменить на один колбэк уровня экрана `onDeleteTableAbove(index: Int)` и boolean-параметр `hasTableAbove`. Документ читать внутри колбэка через `rememberUpdatedState(current)`.
- `NoteTextBlock`: передавать `text` и `marks`, а `NoteDocument` создавать внутри `RichNoteEditor` или передавать готовые поля.

### 5.3 История отмены
Сейчас каждая буква — отдельный полный снимок, до 1000 штук. В заметке на 10 тыс. символов это до 20 МБ.
- `EditorViewModel`: в `rememberEdit(current, kind: EditKind)` добавить `enum EditKind { TYPING, STRUCTURE }`.
- Склеивать `TYPING` в одну запись, если с прошлой правки прошло меньше 1000 мс и вставка не содержит пробела или `\n`. `setTitle`/`setBlockText` — это `TYPING`; разметка, таблицы, список, вставка — `STRUCTURE`.
- Лимит: 200 записей и суммарно не больше 2 000 000 символов. Старые записи вытеснять.
- (P2) В `EditSnapshot` хранить `caret: BlockCaret`. После undo/redo восстанавливать курсор через существующий `CaretRequest`.

### 5.4 Быстрый путь в `ListAutoFormat`
- `afterEdit` на каждую букву делает `split('\n')` и прогоняет регулярку по каждой строке. Добавить ранний выход: если ни в `previous`, ни в `current` нет совпадения `(?m)^\d+\.` (одна предкомпилированная `Regex.containsMatchIn`) и это не одиночный `\n`, вернуть `Edit(current, cursor)`.
- Существующие тесты `ListAutoFormatTest` должны остаться зелёными.

### 5.5 Повторная вёрстка скрытого слоя
- `RichNoteEditor`: `rememberSpoilerPaint` вызывать только когда `censor == true` (вынести в отдельный composable под `if`). Иначе при каждой клавише пересоздаётся ключ `remember(text, ...)`.

---

## Фаза 6. Баги редактора (P1)

### 6.1 Тап мимо выделения не работает после таблицы
`EditorScreen` даёт общий `surface` только блоку `index == 0`, остальные получают свой `bridge`, к которому не подключён `observeSelectionTaps`. После таблицы выделение не снимается тапом, меню не закрывается.
- Сделать `NoteSurfaceBridge` маршрутизатором: хранить `activeBlock` (кто в фокусе или у кого непустое выделение). Каждый `RichNoteEditor` регистрирует свои лямбды под `blockIndex` (`surface.register(blockIndex, handlers)` в `DisposableEffect`), а `observeSelectionTaps` вызывает обработчик активного блока.
- Тест: в `SelectionTapTest` добавить проверку маршрутизации на чистом классе.

### 6.2 Дублирование букв и прыжки курсора на Samsung и Gboard
`onValueChange` меняет текст (автокапитализация после `N. `, перенумерация), но сохраняет `next.composition`. Регион composition у IME перестаёт совпадать с текстом.
- Если `edit.text != next.text`, присваивать `TextFieldValue(edit.text, selection, composition = null)`.
- Капитализацию и перенумерацию применять только при `next.composition == null`, то есть после коммита слова. Исключение — ветка Enter, её применять всегда.
- Проверить на Gboard и Samsung Keyboard: набор «1. привет», Enter, «мир».

### 6.3 Границы разметки
`MarkEdits.shift`: текст, набранный прямо **перед** жирным словом, тоже становится жирным (начало метки включительное).
- Разделить `mapIndex` на `mapStart` и `mapEnd`. Для начала при `index == prefix` и чистой вставке сдвигать на `newEnd`. Итог: обе границы исключающие, как сущности в Telegram.
- Тесты в новом `MarkEditsTest.kt`: вставка перед, внутри, после метки, удаление через границу.

### 6.4 Пункты меню пропадают при открытой клавиатуре
`SelectionMenu.OverflowMenu`: «Зачёркнутый» и «Подчёркнутый» скрываются при `maxHeight < 360.dp`, а с клавиатурой это почти всегда так.
- Убрать условие `extraStyles`. Обернуть список в `Column(Modifier.heightIn(max = ...).verticalScroll(rememberScrollState()))`.

### 6.5 Перенос строки в заголовке
- `EditorScreen` → `BasicTextField` заголовка: `singleLine = false, maxLines = 3`, `keyboardOptions.imeAction = ImeAction.Next`.
- В `onValueChange` удалять `\n`. Если `\n` был введён, перевести фокус в первый текстовый блок (`requesters.firstOrNull()`) с курсором в начале.

### 6.6 Битый или новый JSON затирает заметку
`NoteDocuments.decode` при ошибке возвращает `NoteDocument(text = raw)`. Пользователь видит JSON, и первая же правка перезаписывает документ. Это случится и при появлении новых `MarkStyle` (фаза 7).
- Сделать `TextMark.style` устойчивым: свой `KSerializer<MarkStyle>`, который неизвестное значение превращает в `null`, и `marks` с такими элементами отбрасывать при декодировании. Либо поле `style: String` с маппингом в домене.
- Если `raw` начинается с `{` и декодирование всё равно упало: залогировать через `AppLog.e` и открыть заметку **только для чтения** с тостом «Не удалось открыть заметку». Не сохранять.
- Тест: JSON с `"style":"FUTURE"` декодируется без потери текста.

---

## Фаза 7. Функции «как в Telegram» (P2, кроме отмеченных)

### 7.1 Меню выделения (P1)
`SelectionMenu.kt`, `RichEditor.kt`.
- Основной ряд: **Вырезать · Копировать · Вставить · ⋮**. «Вставить» показывать, если в буфере есть текст (`clipboard.hasText()`); вставка заменяет выделение.
- Меню ⋮ («Форматирование»): Выбрать всё, Жирный, Курсив, Зачёркнутый, Подчёркнутый, Скрытый, **Обычный**, Назад. Активные стили выделять галочкой: передавать в popup `activeStyles` (из `stylesTouching`).
- «Обычный»: новая функция `MarkEdits.clear(marks, start, end)` снимает все стили в диапазоне. Если диапазон задевает заблокированный скрытый текст, сначала запросить PIN.
- Переход между рядами: `AnimatedContent` с `SizeTransform`, 180 мс, `fadeIn + slideInHorizontally`.
- Тест: `MarkEdits.clear` режет метки на границах.

### 7.2 Стиль для следующего ввода
Если курсор свёрнут, кнопки панели (`EditorFormatBar`) не показывают тост «Выделите фрагмент», а включают стиль для следующего ввода.
- `NoteEditorController`: `pendingOn: Set<MarkStyle>`, `pendingOff: Set<MarkStyle>`. Подсветка кнопки: `(activeStyles + pendingOn) - pendingOff`.
- `RichNoteEditor.onValueChange`: при вставке со свёрнутым курсором после `MarkEdits.shift` вызвать новую `MarkEdits.applyTyping(marks, insStart, insEnd, pendingOn, pendingOff)`. Сброс pending при перемещении курсора без ввода и при потере фокуса.
- Для `HIDDEN` pending не применять: скрытый текст создаётся только через выделение.
- Тесты на `applyTyping`.

### 7.3 Копирование и вставка с форматированием
- Новый `ui/editor/RichClip.kt`: синглтон `RichClip.put(text, marks)` и `RichClip.match(inserted): List<TextMark>?`. Сравнивать с последним скопированным текстом; хранится только в памяти процесса.
- `copySelection/cutSelection`: класть в системный буфер plain-текст и вызывать `RichClip.put`. Если диапазон содержит `HIDDEN`, на API 33+ ставить `ClipDescription.EXTRA_IS_SENSITIVE = true` через платформенный `ClipboardManager`, чтобы клавиатура и история буфера не показывали текст.
- `onValueChange`: если вставленный фрагмент (разница prefix/suffix) совпадает с `RichClip`, добавить его метки со сдвигом. Это покрывает и меню, и вставку с клавиатуры.

### 7.4 Markdown-сокращения
- Новый `ui/editor/MarkdownShortcuts.kt` (`object`, чистая логика). При вводе закрывающего маркера сразу перед курсором: `**текст**` → BOLD, `__текст__` → ITALIC, `~~текст~~` → STRIKE, `||текст||` → HIDDEN.
- Правила как в Telegram: содержимое не пустое и не начинается и не кончается пробелом, внутри нет `\n`. Маркеры удаляются, остальные метки сдвигаются через `MarkEdits.shift`.
- Не срабатывает внутри заблокированного скрытого текста и пока идёт composition.
- Вызывать в `onValueChange` после `ListAutoFormat`. Тесты на каждый маркер и на граничные случаи.

### 7.5 Моноширинный текст и ссылки (P3)
- `MarkStyle.MONO` (шрифт `FontFamily.Monospace` и фон `surfaceVariant`). Добавлять только после 6.6.
- `TextMark.url: String? = null` и `MarkStyle.LINK`. Диалог «Ссылка» в меню 7.1. Разрешённые схемы: `http`, `https`, `mailto`, `tel`, всё остальное отклонять. Открывать через `Intent.ACTION_VIEW` в `try/catch (ActivityNotFoundException)`.

### 7.6 Анимация скрытого текста частицами, как в Telegram
Механика PIN остаётся. Меняется визуал.
- Новый `ui/editor/SpoilerParticles.kt`. Прямоугольники скрытых диапазонов брать из `TextLayoutResult` (логику переиспользовать из `expandedHiddenPath`). Частицы хранить в предвыделенных `FloatArray` (x, y, vx, vy, фаза).
  - Количество: одна частица на 48 px² при NORMAL и на 96 px² при LOW, не больше 600 на блок.
  - Рисовать через `drawIntoCanvas { it.nativeCanvas.drawPoints(array, paint) }` с переиспользуемым `Paint`, без аллокаций в кадре.
- Анимационный цикл: `LaunchedEffect` с `withFrameMillis`, только пока `locked`, composable на экране и lifecycle `RESUMED`.
- Раскрытие после верного PIN: `Animatable` 0→1 за 400 мс, круг из точки тапа. Частицы внутри радиуса гаснут, текст проявляется.
- При reduce motion остаётся текущее статичное размытие (4.5). Список (`SpoilerPreview`) всегда со статичным размытием.

---

## Фаза 8. Безопасность (P1)

### 8.1 Смена PIN без текущего PIN
В `SettingsScreen` кнопка «Сменить PIN» открывает `PinMode.CREATE`, и `SettingsViewModel.submitNewPin` сохраняет новый хэш **без проверки старого**. Любой, у кого в руках телефон, задаёт свой PIN и читает скрытый текст.
- Добавить `PinMode.CHANGE`: сначала «Текущий PIN» через `PinHasher.matches`, потом «Новый PIN» и «Повторите PIN».
- `submitNewPin` при существующем PIN принимать только с токеном успешной проверки (флаг в VM, сбрасывается после использования).

### 8.2 Перебор PIN
- Новый `data/secure/PinThrottle.kt` (чистая логика плюс хранение в `SecureSettingsStore`): после 5 неверных попыток блокировка на 30 с, дальше удвоение до 15 минут. Счётчик и `lockedUntil` хранить с `SystemClock.elapsedRealtime()` и меткой загрузки, чтобы перевод часов не обходил блокировку.
- Применить в `EditorViewModel.unlock` и `SettingsViewModel.clearWith`/CHANGE. В `PinDialog` показывать «Попробуйте через N с» и блокировать клавиатуру.
- Тесты `PinThrottleTest`.

### 8.3 Скрытый текст попадает в поиск
`NoteDocuments.searchBlob` включает скрытые фрагменты: поиск по слову из скрытого текста находит заметку.
- `searchBlob`: строить из `plainText`, вырезав диапазоны `HIDDEN` (в блоках и в ячейках таблиц через `cellMarks`).
- Одноразовая переиндексация: `NotesRepository.reindexSearchIfNeeded()` на `Dispatchers.IO` с флагом `search_index_v2` в `NotesPrefs`. Запуск из `NotesApp.onCreate` через application-scope (`@Singleton CoroutineScope` в `AppModule`).
- Тест: слово из скрытого фрагмента не попадает в `searchBlob`.

### 8.4 Скрытый текст читается экранным диктором
- `SpoilerPreview` и `NoteCard`: `Modifier.clearAndSetSemantics { contentDescription = <превью, где скрытые части заменены на «скрытый текст»> }`.

### 8.5 Код сервисного сброса в открытом виде
- `EditorPolicyTest` содержит код сброса цифрами, `NotesViewModels.kt` (комментарий к `saveCreatedPin`) тоже. Это противоречит `PRODUCT_SPEC.md`.
- В тесте проверять через `PinHasher.SERVICE_RESET_HASH` и перебор 0000–9999 (как в `PinHasherTest`), комментарий переписать без цифр.
- **Важно:** код останется в истории git, а хэш от 4 цифр с публичным pepper подбирается за миллисекунды. Владельцу решить: сменить код (новый хэш) или убрать сервисный сброс. Переписывать историю без явного запроса не нужно.

### 8.6 Бэклог (не делать без согласования)
- Release подписан debug-ключом из репозитория. Отдельный ключ из секретов CI сломает обновление у текущих пользователей.
- `security-crypto` устарела. В перспективе перейти на DataStore + Tink.
- Room-база не зашифрована: скрытый текст лежит в БД открытым текстом. Вариант — SQLCipher, это отдельная задача.

---

## Фаза 9. Приёмка

- [ ] `./gradlew testDebugUnitTest assembleDebug assembleOptimized` зелёные локально и в CI.
- [ ] Optimized APK ставится поверх debug, заметки и модели на месте.
- [ ] Список из 500 заметок: прокрутка без пропусков кадров на устройстве уровня Helio G35 / 3 ГБ (`adb shell dumpsys gfxinfo <pkg>`: доля janky frames < 5%).
- [ ] Набор текста в заметке на 10 000 символов без задержек; GPU-профиль не показывает пиков на каждую клавишу.
- [ ] В простое (список, без записи) CPU приложения < 2% при reduce motion или LOW.
- [ ] Редактор: тап мимо выделения работает в любом блоке; нет дублирования букв на Gboard и Samsung; Undo откатывает по словам.
- [ ] Меню выделения: Вырезать/Копировать/Вставить/Форматирование, «Обычный», все стили видны при открытой клавиатуре.
- [ ] Копирование и вставка внутри приложения сохраняют форматирование; markdown-сокращения работают.
- [ ] «Сменить PIN» требует текущий PIN; после 5 ошибок срабатывает блокировка; поиск не находит скрытый текст.
- [ ] Старые заметки (legacy JSON без `blocks`) открываются и сохраняются без изменений структуры.

## Порядок выполнения

1.1 → 1.2 → 8.1 → 8.3 → 6.6 → 2.1 → 2.3 → 3.1 → 3.2 → 5.1 → 5.2 → 5.3 → 6.1 → 6.2 → 6.3 → 6.4 → 6.5 → 4.1 → 4.2 → 4.3 → 4.4 → 4.5 → 5.4 → 5.5 → 7.1 → 8.2 → 8.4 → 8.5 → 7.2 → 7.3 → 7.4 → 2.2 → 2.4 → 1.4 → 3.3 → 7.6 → 1.3 → 7.5
