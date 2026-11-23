# task_update_1 — доработка редактора, настроек и первого запуска

Проект: `com.alekseevich47.notes` (Kotlin 2.0.21, Compose BOM 2024.10.01 → Compose 1.7.x, Room 2.6.1, Hilt, Haze 1.5.2, minSdk 26, targetSdk 35).
План написан для другого агента-исполнителя. Все пути указаны от корня репозитория; в коде ссылаться на **имена функций**, а не на номера строк (строки смещаются).

## 0. Правила исполнителя (обязательно)

Перенесены из `Task_update.md` §0, `docs/PRODUCT_SPEC.md` и `docs/STACK.md`, плюс правила владельца.

1. Один шаг = один коммит. После каждого шага: `./gradlew testDebugUnitTest assembleDebug`. Перед финалом ещё `assembleOptimized`. Красная сборка = дальше не идти.
2. Правки точечные. Рабочий код не переписывать «заодно». Если шаг требует рефакторинга, ограничиться минимальным выносом.
3. Не менять формат хранения так, чтобы старые заметки перестали открываться. Новые поля — только с default-значениями. `fallbackToDestructiveMigration` не использовать. Схему Room в этой задаче менять **не нужно**.
4. STT (`stt/*`), запись и виджеты не трогать, кроме шагов, где это написано прямо (7.1 — только замена `Log` на `AppLog`; 11.4 — чтение выбранного распознавателя; 13.9 — опционально, после ответа владельца).
5. AGSL и `RuntimeShader` не использовать (ломают композитинг на Tecno/HiOS). Блюр только через Haze.
6. Новую чистую логику (без Compose и без Context) класть в `object` / `internal fun` и покрывать JVM-тестами в `app/src/test/java/com/alekseevich47/notes/` по образцу `ListAutoFormatTest`, `SelectionTapTest`.
7. Все диалоги, тосты, меню — **свои**, не системные (`AppDialog`, `PinDialog`, `ToastHost`, `SelectionActionsPopup`). Системные `AlertDialog`, `DatePickerDialog`, системный текстовый тулбар не использовать.
8. Оптимизация: не добавлять перекомпозиции на каждую клавишу, не создавать лямбды с захватом `current.document` в списке блоков, не добавлять бесконечные анимации в idle (см. `Task_update.md` фазы 3–5). Новые `Popup`/`Dialog` не должны держать анимации, пока скрыты.
9. В исходниках, логах и тестах не писать цифры кода сервисного сброса PIN, содержимое заметок, транскрипты.
10. Правила владельца для git: коммитит агент, но **перед `git push` проверить `git log -n 5 --format=%B` и снять trailer `Co-authored-by: Cursor <cursoragent@cursor.com>`** (amend собственного ещё не запушенного коммита). Не пушить коммиты с этим trailer. Деструктивные операции (filter-repo, force-push, reset --hard по чужим коммитам) не выполнять без явного запроса.
11. После завершения всех шагов обновить `docs/STACK.md`, `docs/PRODUCT_SPEC.md`, `README.md` (см. §Z).

Приоритеты: **P1** — обязательно, **P2** — желательно, **P3** — по желанию.

## Порядок выполнения

Пункты задачи затрагивают одни и те же файлы, поэтому порядок не совпадает с нумерацией.

```
12 → 9 → 11 → 10 → 1 → 3 → 4 → 2 → 5 → 6 → 7 → 13 → 8 → Z
```

Зависимости: 4 строится на 3 (одно меню); 2 переиспользует каркас меню из 3/4; 8 прикладывает журнал из 7 и ссылается на `TermsDialog` из 13.

---

## 12. Темы: убрать «Как в системе» (P1)

**Что сейчас.** `domain/Models.kt`: `enum class ThemeMode { SYSTEM, LIGHT, DARK }`. `SettingsScreen.kt` рисует три `ThemeChip`. `ThemeModePrefs.decode` возвращает `SYSTEM` для пустого/неизвестного значения. `NotesTheme` (`ui/theme/Theme.kt`) ветвится по `SYSTEM -> isSystemInDarkTheme()`.

**Нюанс миграции.** У существующих пользователей в `NotesPrefs` может лежать `SYSTEM`. Нельзя просто убрать enum-значение: `valueOf` упадёт, а `decode` вернёт неожиданную тему. Выбранная тема должна быть детерминированной.

### 12.1 Модель и декодирование
- `app/src/main/java/com/alekseevich47/notes/domain/Models.kt`: `enum class ThemeMode { LIGHT, DARK }` (убрать `SYSTEM`).
- `data/secure/ThemeModePrefs.kt`: сигнатура `decode(raw: String?, systemDark: Boolean): ThemeMode`.
  - `"LIGHT"` → `LIGHT`, `"DARK"` → `DARK`.
  - `null`, `""`, `"SYSTEM"`, любой неизвестный → `if (systemDark) DARK else LIGHT`.
  - `rawToKeep` не менять.
- `data/secure/ThemeStore.kt`, `load(context)`: посчитать `systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES` и передать в `decode`. Существующий блок `if (!plainContains) prefs.edit().putString(THEME_MODE, decoded.name).commit()` оставить. Добавить: если `plainRaw == "SYSTEM"` (старое значение) — тоже один раз записать вычисленное `decoded.name`, чтобы тема больше не зависела от системы.
- `ui/theme/Theme.kt`: `val dark = themeMode == ThemeMode.DARK`. Убрать импорт `isSystemInDarkTheme`, если он больше не нужен.

### 12.2 UI
- `ui/settings/SettingsScreen.kt`: удалить `ThemeChip("Как в системе", ...)`. Остаются «Светлая» и «Тёмная».
- `ui/settings/SettingsViewModel.kt`: `setTheme` не менять.

### 12.3 Тесты
- `app/src/test/.../ThemeModePrefsTest.kt`: обновить под новую сигнатуру. Кейсы: `null`/`""`/`FUTURE`/`"light"`/`"SYSTEM"` при `systemDark=false` → `LIGHT`, при `true` → `DARK`; `"LIGHT"`/`"DARK"` не зависят от `systemDark`; `rawToKeep` — прежние тесты.

**Готово, когда:** в настройках два чипа; у пользователя со старым `SYSTEM` тема остаётся такой, какой была в момент первого запуска новой версии, и больше не меняется вслед за системой.

---

## 9. Тексты в настройках (P1)

Источник текстов — `ui/settings/SettingsCopy.kt` и `data/download/ModelCatalog.kt`. Менять только строки и тесты.

### 9.1 Автозагрузка
`SettingsCopy.WARMUP_SUBTITLE` заменить на (одна строка, без двойного пробела — в запросе владельца между «обрабатывался» и «мгновенно» опечатка с двумя пробелами, исправить на один):

```
Эта настройка нужна, чтобы записываемый текст отображался и обрабатывался мгновенно. На некоторых моделях телефона требуется в системных настройках установить автозапуск и работа без ограничения батареи. Нажатие на этот текст откроет возможные системные параметры. При возникновении трудностей обратитесь в поддержку по контактам ниже.
```

Поведение нажатия: в `SettingsScreen.kt` и заголовок, и подпись сейчас кликабельны только при `warmup && warmupAvailable`. Текст обещает «нажатие откроет параметры», поэтому сделать подпись (и заголовок) кликабельными при `warmupAvailable`, независимо от положения переключателя (`viewModel::openWarmupPowerSettings`, он только открывает системный экран и не меняет настройку). Если `!warmupAvailable` — показывается `WARMUP_UNAVAILABLE`, не кликабельно (как сейчас).

### 9.2 GigaAM
`data/download/ModelCatalog.kt`, `ModelKind.GIGA_AM.detail` → `"Финальная обработка текста."`. Заголовок (`title = "GigaAM v3"`) не менять.

### 9.3 Распознавание
В `SettingsCopy` добавить:

```kotlin
const val RECOGNIZER_HINT =
    "Не на всех смартфонах корректно работает \"системное распознавание\". Рекомендуем использовать T-one, предварительно скачав его по кнопке ниже."
```

В `SettingsScreen.kt` показать этот текст сразу под `SectionLabel(SettingsCopy.RECOGNIZER_TITLE)` и над чипами (стиль как у `SettingsCopy.MODELS`: `bodyMedium`, `onSurfaceVariant`, `NotesFont`, отступ снизу 8.dp).

### 9.4 Тесты
`SettingsCopyTest.kt`: тест `warmupCopyNamesOemAutostartBatteryAndUnlock` проверяет старый текст (`Tecno`, `Xiaomi`, `Samsung`, `разблокир`). Заменить проверки на новый текст: `contains("мгновенно")`, `contains("автозапуск")`, `contains("ограничения батареи")`, `contains("обратитесь в поддержку")`, `!contains("  ")`. Добавить `assertEquals` для `RECOGNIZER_HINT` и `ModelKind.GIGA_AM.detail`.

---

## 11. T-one недоступен без скачанной модели (P1)

**Что сейчас.** `SecureSettingsStore.recognizer` по умолчанию `tone` (`NotesPrefs.recognizerOf` возвращает `tone` для любого значения, кроме `system`). Чип T-one выбирается всегда, даже если модель не скачана. `MicRouting.liveMode` при `tone` без прогрева даёт `TONE_COLD`.

**Определение «доступен».** T-one доступен, если модель установлена (`ModelDownloadManager.isInstalled(ModelKind.T_ONE)`, в UI — `card.status == DownloadStatus.INSTALLED`) **и** включена (`SecureSettingsStore.isModelEnabled(T_ONE)`; кнопка «Отключить» означает, что запись T-one игнорирует — `MicRouting.toneStartup`).

### 11.1 Чистая логика
Новый файл `data/secure/RecognizerChoice.kt`:

```kotlin
object RecognizerChoice {
    /** stored: сохранённое значение или null, если ключа нет. */
    fun effective(stored: String?, toneUsable: Boolean): String = when {
        !toneUsable -> NotesPrefs.RECOGNIZER_SYSTEM
        stored == NotesPrefs.RECOGNIZER_SYSTEM -> NotesPrefs.RECOGNIZER_SYSTEM
        else -> NotesPrefs.RECOGNIZER_TONE
    }
}
```

Обратная совместимость: у пользователя с установленным T-one и без сохранённого ключа остаётся `tone` (как было).

### 11.2 Нормализация сохранённого значения
- `SecureSettingsStore`: добавить `fun normalizeRecognizer(toneUsable: Boolean)`. Читает сырое значение из `prefs` (может отсутствовать), считает `RecognizerChoice.effective`, при отличии вызывает `setRecognizer(...)` (он же зеркалит значение в `NotesPrefs` для виджетов). Писать в `AppLog.i("Settings", "recognizer normalized -> $value toneUsable=$toneUsable")` только при изменении.
- Вызывать:
  1. `NotesApp.onCreate` после `downloads.recoverInterrupted()` — через `applicationScope.launch(Dispatchers.IO)` (проверка файлов не на главном потоке): `settings.normalizeRecognizer(downloads.isInstalled(T_ONE) && settings.isModelEnabled(T_ONE))`. Для этого в `NotesApp` внедрить `SecureSettingsStore`.
  2. `SettingsViewModel.deleteModel(kind)`, `setModelEnabled(kind, enabled)`, `stopDownload`/завершение загрузки (если проще — слушать `downloads.cards`) — для `kind == T_ONE` после изменения вызвать нормализацию.
- Автопереключение на T-one после скачивания **не делать** (выбор остаётся за пользователем).

### 11.3 UI
- `SettingsViewModel`: `val toneUsable: StateFlow<Boolean>` = комбинация `models` (`T_ONE` в статусе `INSTALLED`) и `modelEnabled` (`modelEnabled[T_ONE] != false`) через `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. Initial считать синхронно из `downloads.isInstalled`, чтобы не мигало.
- `SettingsScreen.kt`:
  - `ThemeChip` получает параметр `enabled: Boolean = true`. При `enabled == false`: `Modifier.alpha(0.4f)`, `clickable(enabled = false)`; в `semantics { disabled() }`.
  - Чип T-one: `enabled = toneUsable`, `selected = recognizer == RECOGNIZER_TONE && toneUsable`. Чип «Системное»: `selected = recognizer == RECOGNIZER_SYSTEM || !toneUsable`.
  - Нажатие на недоступный чип T-one — `onToast("Сначала скачайте T-one ниже")` (чип остаётся `clickable`, но не меняет выбор; поэтому «disabled» только визуально и в semantics, а клик показывает тост).
  - `viewModel.setRecognizer(TONE)` защитить в `SettingsViewModel`: `if (value == TONE && !toneUsable.value) return`.

### 11.4 Защита на стороне записи (defense in depth)
`stt/RecordingSession.kt` читает `settings.recognizer.value` перед `MicRouting.liveMode(...)`. Заменить на `RecognizerChoice.effective(settings.recognizer.value, toneInstalled && toneEnabled)`, используя уже посчитанные в этом месте `toneInstalled`/`toneEnabled`. Больше в STT ничего не менять.

### 11.5 Тесты
`RecognizerChoiceTest.kt`: `effective(null,false)=system`, `effective("tone",false)=system`, `effective(null,true)=tone`, `effective("system",true)=system`, `effective("tone",true)=tone`.

**Готово, когда:** на чистой установке выбран «Системное», чип T-one серый и по нажатию тост; после скачивания T-one чип активен; удаление или «Отключить» T-one возвращают выбор на «Системное»; у существующих пользователей с T-one ничего не меняется.

---

## 10. Белый «многогранник» на кнопке «+» (P1)

**Диагноз (по коду).** `NotesListScreen` рисует FAB через `LiquidGlassPlate(hazeState = hazeState, ...)`, но сам `NotesListScreen` находится внутри `Box(...hazeSource(state = hazeState))` в `NotesRoot`. Haze-эффект FAB читает источник, в который записан он же (белая заливка `palette.fill` попадает в источник и блюрится обратно) — получается самосэмплинг с ломаными полупрозрачными гранями. Нижняя плашка (`GlassBottomBar`) вынесена из источника, поэтому у неё артефакта нет. Блюр под кнопкой 60dp всё равно ничего полезного не даёт.

### 10.1 Правка
- `ui/components/LiquidGlass.kt`: в `LiquidGlassPlate` добавить параметр `blur: Boolean = true`. `val liveBlur = blur && LocalPerfTier.current == PerfTier.NORMAL && Build.VERSION.SDK_INT >= S`. Для `blur = false` срабатывает уже существующая ветка плоской заливки (`palette.fill.copy(alpha = 0.94f)` + sheen + border). Halo-ветку (`halo && liveBlur`) не трогать.
- `ui/notes/NotesListScreen.kt`: в FAB передать `blur = false`. Параметр `hazeState` из `NotesListScreen` и `NotesListPage` можно оставить (убирать только если он больше нигде не используется; тогда убрать аккуратно и в `NotesRoot`).
- Тень: `Modifier.shadow(elevation, shape, clip = false)` на пустом `Box` оставить, как у остальных плашек. Если после правки на устройстве остаётся светлый ореол вокруг «+», заменить на `softShadowPadding` из `SoftShadow.kt` (как в `SelectionMenu`), `elevation = 0.dp` у плашки.

### 10.2 Проверка
Скриншот FAB в светлой и тёмной теме на API 31+ (блюр есть у плашки снизу, у «+» плоская заливка) и на API 28 (`PerfTier.LOW`).

**Готово, когда:** вокруг и поверх «+» нет светлых граней; нижняя плашка выглядит как раньше.

---

## 1. Время в списке = время создания (P1)

**Диагноз (по коду).** `NotesRepository.toListItem` кладёт в `NoteListItem` `updatedAt`, а `NotesListScreen.NoteCard` форматирует именно его (`formatStamp(note.updatedAt)`). Любая правка (`NotesRepository.save` → `updatedAt = now`) меняет подпись в списке. В самой заметке `EditorScreen` уже показывает «последнее изменение» через `lastEditedCaption(createdAt, updatedAt)` и скрывает подпись, если правок не было (`updatedAt <= createdAt + 1500`). Сортировка и фильтр по дате уже идут по `createdAt` (`NoteDao.observe`).

### 1.1 Модель и репозиторий
- `domain/Models.kt`, `NoteListItem`: заменить поле `updatedAt: Long` на `createdAt: Long`.
- `data/repository/NotesRepository.kt`, `NoteEntity.toListItem()`: `createdAt = createdAt`. **Ключ кэша `LruCache` оставить на `updatedAt`** (`hit.first == updatedAt`): по нему определяется, что превью изменилось.

### 1.2 Список
- `ui/notes/NotesListScreen.kt`, `NoteCard`: `formatStamp(note.createdAt)`.

### 1.3 Заметка
Логику `lastEditedCaption` не менять. Проверить вручную: открыть заметку без правок → подписи нет; изменить → «последнее изменение: дд.ММ.гггг чч:мм» растёт, а в списке время не меняется. `EditorViewModel` обновляет `updatedAt` в памяти при вводе — это нужно для подписи в редакторе и остаётся.

### 1.4 Тесты
- Вынести `formatStamp` из `NotesListScreen.kt` в `internal fun formatStamp(epoch: Long, zone: ZoneId = stampZone): String` и покрыть тестом на фиксированной дате (UTC). Формат `d MMM, HH:mm` не менять.
- `EditorPolicyTest` (проверка `lastEditedCaption`) должен остаться зелёным.

**Готово, когда:** после правки заметки её время в списке не изменилось, порядок в списке прежний, в редакторе виден «последнее изменение».

---

## 3. Меню выделения: три точки и расширенное меню на 6 позиций (P1)

Файл: `ui/editor/SelectionMenu.kt`. Меню общее для тела заметки (`RichEditor.kt`) и ячеек таблицы (`TableEditor.kt`), поэтому правка действует в обоих местах.

### 3.1 «Палочка» вместо трёх точек
**Диагноз (гипотеза, подтвердить воспроизведением).** Иконка `Icons.Outlined.MoreVert` в `PrimaryMenu` не менялась, но с коммита «Match the selection menu to the Telegram row» в ряд добавилась кнопка «Вставить». Ряд `Вырезать` + `Копировать` + `Вставить` + разделитель + кнопка 36dp по ширине не помещается в окно `Popup` на смартфонах шириной ~360dp (особенно с крупным шрифтом). `Row` измеряет детей по порядку, и последний ребёнок — блок с иконкой — получает остаток ширины и сжимается в узкую полоску; видна только «палочка».

Воспроизвести: эмулятор 360dp, `fontScale = 1.3`, в буфере есть текст, выделить слово.

Правка в `PrimaryMenu`:
- Кнопку «⋮» и разделитель вынести из зависимости от ширины подписей: подписи положить во вложенный `Row(Modifier.weight(1f, fill = false))`, разделитель и кнопку — после него без веса. Тогда они измеряются первыми, а подписи получают остаток.
- `BubbleLabel`: `maxLines = 1`, `softWrap = false`; горизонтальный padding 14.dp → 12.dp.
- Иконку заменить на горизонтальные точки `Icons.Outlined.MoreHoriz` **только если** владелец скажет, что нужны именно горизонтальные; по умолчанию вернуть то, что было (вертикальные три точки `MoreVert`), размер 20.dp, `contentDescription = "Ещё"`.
- Ширина плиты не должна превышать `windowSize.width - 2 * marginPx`; кнопка «⋮» всегда видна и не сжимается (ни при каком шрифте).

### 3.2 Расширенное меню: 6 позиций + прокрутка + статичная «Назад»
Сейчас `OverflowMenu` — один `verticalScroll` на все строки, включая «Назад».

Новый порядок позиций (сверху вниз):
1. Выбрать всё
2. Обычный
3. Жирный
4. Курсив
5. Подчёркнутый
6. Скрытый
7. Зачёркнутый
8. Моно
9. Ссылка

Верстка:

```
Column(Modifier.widthIn(min = 212.dp, max = 280.dp)) {
    Box(Modifier.height(OverflowRowHeight * visibleRows)) {   // 6 строк, прокручиваемая область
        Column(Modifier.verticalScroll(scrollState)) { 9 × OverflowLabel }
    }
    Divider (0.6.dp, как сейчас)
    BackRow (вне прокрутки, как сейчас)
}
```

- Ввести `private val OverflowRowHeight = 48.dp` и применить `Modifier.height(OverflowRowHeight)` в `OverflowLabel` (вместо `padding(vertical = 12.dp)`; текст по центру по вертикали), чтобы высота окна под ровно 6 строк была детерминирована, а не зависела от шрифта.
- `visibleRows = 6`, но если доступной высоты не хватает (`BoxWithConstraints.maxHeight` конечна), `visibleRows = ((maxHeight - BackRowHeight - dividerHeight) / OverflowRowHeight).toInt().coerceIn(3, 6)`.
- Сохранить `heightIn(max = ...)`-страховку при бесконечном `maxHeight` (фолбэк 480.dp, как сейчас).
- Подсказка о прокрутке: у нижней границы прокручиваемой области, пока есть что прокручивать (`scrollState.canScrollForward`), рисовать полупрозрачный градиент 12.dp цвета плашки; у верхней — когда `canScrollBackward`. Через `drawWithContent` (без `graphicsLayer` offscreen).
- Строка «Назад» и разделитель остаются вне `verticalScroll`.
- Галочки активных стилей (`activeStyles`) сохраняются; «Выбрать всё» и «Обычный» без галочки.

### 3.3 Тесты
Чистая функция высоты: `internal fun overflowVisibleRows(maxHeightDp: Float, rowDp: Float, backBlockDp: Float): Int` + `SelectionMenuLayoutTest` (6 при достаточной высоте, 5/4/3 при нехватке, нижняя граница 3).

**Готово, когда:** при выделении видны «Вырезать | Копировать | Вставить | ⋮» без искажений на 360dp; в расширенном меню сразу видны 6 строк, остальные прокручиваются, «Назад» и разделитель неподвижны.

---

## 4. Расширенное меню: убрать «скачки» при открытии (P1)

**Диагноз (по коду).** `SelectionActionsPopup`:
1. `AnimatedContent` с `SizeTransform` анимирует размер содержимого 180 мс. `Popup` — это отдельное окно; его размер меняется каждый кадр, и на каждом кадре `PopupPositionProvider.calculatePosition` вызывается заново с новым `popupContentSize`.
2. `placeSelectionMenu` центрирует меню по `popupContentSize.width / 2` и решает «над выделением или под ним» по текущей высоте. Пока высота растёт, меню то центрируется заново (горизонтальный дрейф), то в середине анимации перестаёт помещаться сверху и переворачивается вниз.
3. Горизонтальный slide (`slideInHorizontally`/`slideOutHorizontally`) выезжает за границу окна и обрезается окном.

**Инварианты решения:**
- размер окна `Popup` не меняется на каждом кадре;
- решение «выше/ниже» принимается один раз для обоих экранов меню по **максимальному** размеру;
- при переходе «основное ↔ расширенное» неподвижны: центр по горизонтали и **ближний к тексту край** (нижний, если меню над выделением; верхний, если под ним).

### 4.1 (P1) Стабильное позиционирование
- `placeSelectionMenu` сделать `internal` и ввести параметр `reserve: IntSize` — размер плиты максимальной из страниц меню (без отступов под тень). Источник: измерить обе страницы один раз через `SubcomposeLayout` (композиция `PrimaryMenu` и `OverflowMenu` с ограничениями окна) и держать `reserve = IntSize(max(w1, w2), max(h1, h2))` в `remember`; пересчитывать только при смене `canPaste`, ширины окна или набора активных стилей (высота страниц от них не зависит).
- Центр: `centerX = clamp((selLeft + selRight) / 2, marginPx + reserve.width / 2, windowWidth - marginPx - reserve.width / 2)`. Окно позиционируется как `x = centerX - plateWidth / 2 - shadowPadPx`, где `plateWidth` — **текущая** ширина плиты страницы. Так клампа по текущему размеру нет, а центр не дрейфует.
- Вертикаль: `above = anchorTop + selTop - gapPx - reserve.height >= marginPx`; решение не зависит от текущей страницы. Если `above`: нижняя грань плиты `= anchorTop + selTop - gapPx`, `y = bottom - plateHeight - shadowPadPx`. Иначе верхняя грань `= anchorTop + selBottom + gapPx + 18`, `y = top - shadowPadPx`. Кламп по экрану — по `reserve.height`.
- Константу `+ 18` (смещение под выделением, чтобы не закрывать нижний handle) сохранить.

### 4.2 (P1) Переход без анимации размера окна
- В `AnimatedContent` заменить `SizeTransform(clip = false) { _, _ -> tween(180) }` на `SizeTransform(clip = false) { _, _ -> snap() }`; убрать `slideInHorizontally`/`slideOutHorizontally`, оставить `fadeIn(tween(140)) togetherWith fadeOut(tween(100))`.
- Результат: окно один раз меняет размер на целевой в первом кадре перехода; положение плиты не меняется благодаря 4.1.

### 4.3 (P2) Плавное изменение размера плиты внутри окна
Если после 4.1–4.2 переключение выглядит слишком резким: окно на время перехода держать размером `reserve`, плиту (фон и тень рисуются на ней) анимировать по ширине/высоте внутри окна (`animateIntAsState` → `Modifier.size(...)`, выравнивание `BottomCenter` при `above`, иначе `TopCenter`), по окончании снять резерв. Окно во время анимации прозрачное и не перехватывает ничего, кроме собственных кликов (`Popup focusable = false` уже так настроен). Не использовать `animateContentSize` на самом окне.

### 4.4 Что не ломать
- `KeepMenuAboveHandles` (перенос окна в тип 1005 и `alpha = 0f` до переноса) оставить как есть. Проверить, что при переходе страниц не происходит повторного `removeView/addView`.
- `softShadowPadding(shape, room = shadowRoom, ...)`: комната под тень входит в `popupContentSize`; формулы выше вычитают `shadowPadPx` так же, как сейчас.

### 4.5 Тесты
`SelectionMenuPlacementTest`: чистая `placeSelectionMenu` — (а) при смене `plateWidth/plateHeight` с одним и тем же `reserve` центр по X и нижний край (для `above`) не меняются; (б) «выше/ниже» зависит только от `reserve` и выделения; (в) крайние значения: выделение у левого/правого края, у верха экрана, окно уже `reserve`.

**Готово, когда:** при нажатии «⋮» плита меняется на месте, без дрейфа и без переворота «над/под»; визуально нет прыжков ни в светлой, ни в тёмной теме, ни при открытой клавиатуре.

---

## 2. Своё меню «Вставить» вместо системного (P1)

**Когда сейчас появляется системное «Вставить».** `RoutingTextToolbar` (`ui/editor/RichEditor.kt`) перехватывает `TextToolbar.showMenu`. Для **выделения** (`onCopyRequested`/`onCutRequested` не `null`) показывается наше `SelectionActionsPopup`. Для **свёрнутого курсора** Compose вызывает `showMenu(rect, onPasteRequested = ..., onSelectAllRequested = ...)`: так бывает при долгом нажатии на пустое место поля (за концом строки или в пустой строке), при нажатии на ручку курсора, а также после тапа мимо выделения. Если `shouldOfferPlatformPaste(...)` (`ui/editor/SelectionTap.kt`) разрешает, вызывается `platform.showMenu(...)` — это и есть системное окно «Вставить». «Выбрать всё» из этого запроса никогда не пробрасывается.

**Что нужно.** Показывать вместо системного окна наш `Popup` с одной кнопкой «Вставить» в том же стиле, что и `SelectionActionsPopup` (плита `surface`, `RoundedCornerShape(16.dp)`, мягкая тень, `BubbleLabel`).

### 2.1 Общий каркас меню
В `SelectionMenu.kt` выделить из `SelectionActionsPopup` private-composable `SelectionBubble(startHandle: Rect, endHandle: Rect, onDismiss: () -> Unit, content: @Composable () -> Unit)`: `Popup` + `PopupPositionProvider` + `PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false)` + плита + `KeepMenuAboveHandles()`. `SelectionActionsPopup` продолжает использовать его для `AnimatedContent`.

### 2.2 `PasteActionPopup`
```kotlin
@Composable
fun PasteActionPopup(anchor: Rect, onPaste: () -> Unit, onDismiss: () -> Unit)
```
Содержимое: `Row { BubbleLabel("Вставить", onPaste) }`. `startHandle = endHandle = anchor` (прямоугольник курсора), то есть тот же `placeSelectionMenu` (из 4.1) ставит меню над курсором или под ним.

### 2.3 Перехват в `RoutingTextToolbar`
- Добавить поля `var onPasteMenu: (rect: Rect) -> Unit = {}` и `var onPasteMenuHidden: () -> Unit = {}` и флаг `private var pasteMenu = false`.
- В ветке свёрнутого курсора: если `shouldOfferPlatformPaste(...)` разрешает — `pasteMenu = true; platform.hide(); onPasteMenu(rect); return`. **`platform.showMenu` больше не вызывать.** Иначе — `pasteMenu = false; onPasteMenuHidden(); platform.hide()`.
- `status`: `Shown`, если `selectionMenu || pasteMenu`, иначе `platform.status`. (Compose проверяет статус, чтобы решить, скрывать ли тулбар.)
- `hide()`: если `pasteMenu` — сбросить и вызвать `onPasteMenuHidden()`.
- Функция `shouldOfferPlatformPaste` и её тесты остаются (имя можно оставить; при желании переименовать в `shouldOfferPaste` вместе с `SelectionTapTest`).

### 2.4 Подключение в `RichNoteEditor`
- Состояние `var pasteAnchor by remember { mutableStateOf<Rect?>(null) }`.
- `toolbar.onPasteMenu = { pasteAnchor = layout?.getCursorRect(field.selection.end.coerceIn(0, field.text.length)) }`. Берём прямоугольник курсора из `layout` (локальные координаты поля — как у `SelectionActionsPopup`), а не `rect` из Compose, чьи координаты не гарантированы.
- `toolbar.onPasteMenuHidden = { pasteAnchor = null }`.
- Рядом с `shown != null && menuVisible` рисовать `if (anchor != null && field.selection.collapsed && blockFocused) PasteActionPopup(anchor, onPaste = { pasteSelection(); pasteAnchor = null }, onDismiss = { pasteAnchor = null })`. `pasteSelection()` уже учитывает буфер, RichClip, скрытый текст и PIN.
- Закрывать меню (`pasteAnchor = null`): в `hideMenu()`, при любом изменении текста и выделения (в `applyIncoming`), при потере фокуса (`onFocusChanged`), в `BackHandler`, на `observeSelectionTaps.down` (любое новое касание тела заметки). Меню не должно переживать открытие диалога PIN (`pinDialogVisible`).
- Если буфер пуст (`!clipboard.hasText()`), меню не показывать (Compose в этом случае и не передаёт `onPasteRequested`).

### 2.5 Ячейки таблицы (P2)
В composable ячейки в `TableEditor.kt` есть свой `RoutingTextToolbar(platformToolbar)` и `SelectionActionsPopup`. Повторить 2.4: `pasteAnchor`, `PasteActionPopup`, `onPaste` = та же логика, что в `onPaste` у `SelectionActionsPopup` ячейки (`commit(...)`, проверка скрытого текста).

### 2.6 Тесты и проверка
- `SelectionTapTest` — существующие тесты `shouldOfferPlatformPaste` должны пройти без изменений.
- Вручную: долгое нажатие на пустое место поля → появляется наше «Вставить» (не системное); тап по нему вставляет текст с сохранением форматирования из приложения; тап в другое место или ввод — меню исчезает; вставка в позицию внутри заблокированного скрытого текста запрашивает PIN.

**Готово, когда:** системное окно «Вставить» не появляется нигде в редакторе (ни в теле, ни в ячейках).

---

## 5. Двойной тап по слову открывает меню выделения (P1)

**Что сейчас.** `BasicTextField` с `TextFieldValue` (legacy `CoreTextField`) выделяет слово долгим нажатием; двойной тап пальцем не обрабатывается. Меню открывается только после жеста выделения (`toolbar.onSelectionGestureEnded`).

### 5.1 Чистая логика границ слова
Новый файл `ui/editor/WordBounds.kt`:

```kotlin
internal object WordBounds {
    /** Диапазон слова вокруг offset или null, если там пробел/пунктуация/конец строки. */
    fun at(text: String, offset: Int): TextRange?
}
```
Правила: символ слова — `Character.isLetterOrDigit(ch) || ch == '_'`; внутри слова допускаются `'`, `’`, `-`, `.` **только между** двух символов слова (например, `по-русски`, `don't`, `3.14`); если `offset` указывает на разделитель сразу после слова — взять предыдущее слово; суррогатные пары (эмодзи) не разрывать (`Character.offsetByCodePoints`). Эмодзи/символы без букв слова не образуют. Пустой текст → `null`.

### 5.2 Распознавание двойного тапа
Новый `Modifier.onWordDoubleTap(onDoubleTap: (Offset) -> Unit)` в `ui/editor/WordDoubleTap.kt` на базе `pointerInput` + `awaitEachGesture`, `PointerEventPass.Initial`, **без потребления** событий (как `followLinkTaps`). Условия: два коротких нажатия (каждое короче `longPressTimeoutMillis`, смещение ≤ `touchSlop`), интервал ≤ `viewConfiguration.doubleTapTimeoutMillis`, расстояние между ними ≤ `touchSlop * 2`. Состояние предыдущего тапа держать в локальных переменных внутри `pointerInput`-блока (без `remember`-объектов на каждый кадр).

### 5.3 Применение в `RichNoteEditor`
- Добавить модификатор к `BasicTextField` рядом с `followLinkTaps` (позиция в координатах поля, как у `followLinkTaps`).
- Обработчик: `val result = layout ?: return; val range = WordBounds.at(field.text, result.getOffsetForPosition(pos)) ?: return`. Затем:
  - если `!hiddenUnlocked && HiddenText.cursorInside(marks, range.start, range.end)` — `requestUnlock()` и выйти (выделять заблокированный скрытый текст нельзя);
  - иначе `scope.launch { withFrameNanos { }; field = field.copy(selection = range); menuOverflow = false; menuVisible = true; pasteAnchor = null }` — кадр ожидания нужен, чтобы стандартная обработка тапа в `BasicTextField` (установка курсора) не затёрла наше выделение.
- Проверить: после установки выделения появляются ручки выделения; если ручки не появились (внешнее изменение `selection` не включает режим выделения у Compose), оставить выделение с нашим меню, ручки — не требуются (допустимо), но проверить, что тап вне выделения его снимает (логика `observeSelectionTaps`).
- Двойной тап по пустому месту/пробелу — ничего.

### 5.4 Ячейки таблицы (P2)
Применить тот же модификатор в `TableEditor.kt` (`menuVisible = true`, `field = field.copy(selection = range)`).

### 5.5 Тесты
`WordBoundsTest`: слово посередине/в начале/в конце; offset сразу после слова; на пробеле между словами → слово слева либо `null` (зафиксировать выбранное поведение); дефис и апостроф внутри слова; цифры; кириллица; эмодзи; пустая строка; знак препинания в конце.

**Готово, когда:** двойной тап по слову выделяет его и показывает компактное меню («Вырезать | Копировать | Вставить | ⋮»); двойной тап по пробелу ничего не делает; одиночные тапы и ссылки работают как раньше.

---

## 6. Фокус по тапу и скрытие курсора при закрытии клавиатуры (P1)

### 6.1 Тап по тексту при закрытой клавиатуре
**Диагноз (по коду, подтвердить логом).** В `EditorScreen` контейнер тела заметки имеет `.clickable(... onClick = { requesters.getOrNull(lastText)?.requestFocus(); keyboard?.show() })`. Клик по тексту обрабатывается и самим `BasicTextField` (он ставит курсор в точку тапа), а затем срабатывает родительский `onClick`, который **переводит фокус на последний текстовый блок** и принудительно открывает клавиатуру. Если в заметке есть таблица или несколько текстовых блоков, курсор оказывается не там, куда нажали. На первом тапе (поле без фокуса) гонка особенно заметна.

Подтверждение до правки: временная строка `AppLog.i("EditorFocus", "focus block=$blockIndex gained=$focused sel=...")` в `onFocusChanged` `RichNoteEditor` (убрать после проверки или оставить как `AppLog.i` без текста заметки — разрешено).

Правка:
- `ui/editor/SelectionSurface.kt`, `NoteSurfaceBridge`: добавить `fun hitsTextBlock(root: Offset): Boolean` — истина, если точка внутри границ любого зарегистрированного текстового блока (для этого `BlockSurfaceHandlers` получает поле `bounds: (Offset) -> Boolean`; в `RichNoteEditor` реализуется через уже существующие `textCoordinates` + `pointInsideTextBounds(local.x, local.y, layout.size.width, layout.size.height)`). Ячейки таблиц в мост не регистрируются — таблица обрабатывает тап сама; поэтому дополнительно считать тап по таблице «занятым»: `TableBlock` регистрирует в мосте свою область (`bounds`) либо тело заметки проверяет `formats.editingCell`.
- `NoteSurfaceBridge.up(...)`: после `handlers.onGestureSettle` вызывать новый колбэк `onEmptyTap(root)` **только если** жест был тапом (дрейф ≤ `touchSlop`, длительность < `longPressTimeout`) и `!hitsTextBlock(root)`. Значения `dragDistance`, `elapsedMillis`, `touchSlop`, `longPressTimeoutMillis` уже передаются в `up`.
- `EditorScreen`: убрать `.clickable { requestFocus; keyboard.show }` у контейнера тела; вместо него передать мосту `surface.onEmptyTap = { focusLastTextBlockAtEnd() }`, где `focusLastTextBlockAtEnd` = `caretRequest = CaretRequest(token + 1, lastText, offset = текст последнего блока .length)` + `requesters[lastText].requestFocus()` + `keyboard?.show()`. Для доступности оставить на контейнере `Modifier.semantics { onClick(label = "Ваш текст") { focusLastTextBlockAtEnd(); true } }` (без `clickable`, чтобы не перехватывать фокус при тапе по тексту).
- `down()` в мосте сейчас выбирает активный блок только при наличии фокуса/выделения. Для тапа в пустом месте без фокуса `downIndex == null` и `up()` возвращается раньше. Перенести вызов `onEmptyTap` до раннего возврата `val handlers = ... ?: return`.

**Дополнительно (P2, только если после 6.1 каретка всё ещё не там, куда нажали):** в `RichNoteEditor` запоминать позицию последнего `down` (Initial-проход, без потребления) и в `onFocusChanged(isFocused = true)` без активного `caretToken` выставлять `field = field.copy(selection = TextRange(layout.getOffsetForPosition(pos)))`, если текущее выделение свёрнуто и стоит в конце текста по умолчанию.

### 6.2 Системная «Назад» с открытой клавиатурой
**Диагноз.** IME обрабатывает «Назад» сам и скрывается раньше приложения; поле остаётся в фокусе, поэтому курсор продолжает мигать, а панель форматирования (`formats.bodyFocused`) остаётся.

Правка в `ui/notes/EditorScreen.kt`:
- Внутри `EditorScreen`:
  ```kotlin
  @OptIn(ExperimentalLayoutApi::class)
  val imeVisible = WindowInsets.isImeVisible
  val focusManager = LocalFocusManager.current
  val dialogOpen = askPin || askTable || confirmDelete
  var imeWasShown by remember { mutableStateOf(false) }
  LaunchedEffect(imeVisible) {
      if (imeVisible) { imeWasShown = true; return@LaunchedEffect }
      if (imeWasShown && !dialogOpen) { focusManager.clearFocus(); }
      imeWasShown = false
  }
  ```
  (`dialogOpen` читать через `rememberUpdatedState`, чтобы не перезапускать эффект при открытии диалога.) Диалоги (`PinDialog`, `TableSizeDialog`, `AppDialog`) и `LinkDialog` в `RichNoteEditor` — отдельные окна, они скрывают IME; в этот момент фокус сбрасывать нельзя.
- Для `LinkDialog` в `RichNoteEditor` флаг `linkDraft != null` недоступен на уровне экрана; допускается либо поднять `linkDialogOpen` в `NoteEditorController` (`formats.linkDialogOpen`), либо не сбрасывать фокус, если `formats.dialogOpen` (новое булево поле контроллера, выставляется при показе любого диалога из блока).
- `focusManager.clearFocus()` снимает фокус и с заголовка, и с блоков, и с ячеек таблицы; `onFocusChanged` обнуляет `formats.bodyFocused`, панель форматирования уезжает вниз (уже есть `AnimatedVisibility`). Выделение меню при потере фокуса схлопывается штатно (`collapseSelection` / `menuVisible` сбрасывается в `RichNoteEditor`); проверить.
- Проверить вторую «Назад»: теперь `NotesRoot`'s `BackHandler` закрывает редактор (`editorVm.close`).
- Аппаратная клавиатура и плавающий IME: `imeWasShown` не станет `true` — поведение прежнее.

### 6.3 Тесты
Чистой логики мало; вынести решение в `internal fun shouldClearFocusAfterImeHide(wasShown: Boolean, visible: Boolean, dialogOpen: Boolean): Boolean` и покрыть в `EditorPolicyTest`. Для 6.1 — `NoteSurfaceBridgeTest`: `up` вызывает `onEmptyTap` только для короткого тапа вне границ блоков, не вызывает при попадании в блок, при дрейфе, при долгом нажатии.

**Готово, когда:** тап по тексту ставит курсор туда, куда нажали, и открывает клавиатуру; тап под текстом ставит курсор в конец последнего блока; «Назад» при открытой клавиатуре скрывает клавиатуру **и** курсор вместе с панелью форматирования; следующая «Назад» закрывает заметку.

---

## 7. Журнал: достаточно ли и правильно ли ведётся (P1)

**Вывод аудита: недостаточно.** Что уже хорошо: `AppLog` пишет в `filesDir/logs/app.log`, ротация 1.5 МБ, шапка с моделью/Android/ABI/локалью/версией, санитайзер вырезает URL, абсолютные пути и цифры PIN, глобальный `UncaughtExceptionHandler`, события записи (`RecordingSession`) и прогрева модели, навигация. Что мешает разбирать ошибки по присланным логам:

| № | Проблема | Где |
|---|---|---|
| a | Часть критичных ошибок идёт только в `android.util.Log` и **не попадает в файл**: инициализация T-one и GigaAM, ошибка декодирования GigaAM, подготовка и отправка M4A | `stt/SherpaEngines.kt`, `player/NoteAudioShare.kt`, дубли в `stt/RecordingSession.kt` |
| b | Нет логирования плеера, виджетов, сохранения заметки (ошибки `dao.update`), открытия/закрытия редактора | `player/NotePlayer.kt`, `service/PlaybackService.kt`, `ui/widgets/WidgetUpdater.kt`, `ui/notes/NotesViewModels.kt`, `data/repository/NotesRepository.kt` |
| c | Нативные падения (sherpa-onnx / onnxruntime), ANR и убийство по памяти не видны: Java-хендлер их не ловит | `NotesApp` |
| d | В `optimized`/release включён R8: стек-трейсы обфусцированы, а `mapping.txt` нигде не сохраняется | `app/proguard-rules.pro`, `.github/workflows/android.yml` |
| e | Шапка перезаписывается при каждом старте процесса, в теле нет границ между процессами — при серии падений непонятно, какая строка к какому запуску | `AppLog.rewriteHeader` |
| f | В шапке нет состояния, нужного для разбора: свободная память/место, класс устройства, состояние моделей (установлены/включены), выбранный распознаватель, прогрев, права, ограничения батареи, тип сборки | `AppLog.processStartLines` |
| g | Стек: только 8 кадров первого исключения, причины (`cause`) без кадров | `AppLog.renderError` |
| h | Санитайзер заменяет любой URL на `[url]`, теряя хост (для ошибок загрузки модели хост важен) | `AppLog.sanitize` |

### 7.1 Все ошибки — в `AppLog` (P1)
- `stt/SherpaEngines.kt`: заменить `Log.e(TAG, ..., it)` на `AppLog.e(TAG, ..., it)` (T-one init, GigaAM init, GigaAM decode). Импорт `android.util.Log` убрать.
- `player/NoteAudioShare.kt`: три `Log.e` → `AppLog.e`.
- `stt/RecordingSession.kt`: рядом с каждым `Log.e(TAG, ...)` уже есть парный `AppLog.e` — удалить дублирующие `Log.e` (AppLog сам зеркалит в logcat). Больше в STT ничего не менять.
- Правило на будущее: в `docs/STACK.md` добавить строку «Логи — только через `AppLog`, `android.util.Log` в коде приложения не использовать».

### 7.2 Недостающие события (P1)
Добавить через `AppLog.i/w/e`, **без** текста заметок, названий, транскриптов, путей (допустим basename файла, как сейчас):
- `EditorViewModel` (`ui/notes/NotesViewModels.kt`): `editor open id=<id> unreadable=<bool>`, `editor close id=<id>`, ошибка сохранения (`try/catch` вокруг `repository.save` с `AppLog.e`, не проглатывая `CancellationException`), `flush on stop`.
- `NotesRepository`: `save` и `create` — `try/catch` → `AppLog.e("Notes", "save failed id=<id>", error)` и повторный `throw`; `deleteMany`: `deleted count=<n>`; `get`: уже логирует `note json unreadable` — добавить `id`.
- `NotePlayer` / `PlaybackService`: старт/пауза/стоп/seek (без пути, только id заметки), `Player.Listener.onPlayerError` → `AppLog.e`, потеря аудиофокуса, ошибки MediaSession.
- `WidgetUpdater`: ошибки обновления `RemoteViews` (`try/catch` вокруг `appWidgetManager.updateAppWidget`), тип виджета.
- `ModelDownloadManager`: убедиться, что в ошибках загрузки пишется класс исключения, HTTP-код, `kind`, размер скачанного и общий (уже частично) — дополнить недостающим.
- `RecordingService`: причина остановки (`ACTION_STOP`, система убила, нет разрешения).
- Низкое место: перед записью `AppLog.w` если свободно < 100 МБ (`File.usableSpace`) — одна строка, не блокирует запись.

### 7.3 Расширенная шапка и маркер процесса (P1)
- `AppLog.processStartLines(...)` — добавить поля (чистая функция с параметрами, чтобы тестировать без Context): `buildType` (`BuildConfig.BUILD_TYPE`), `debuggable`, `pid`, `memoryClass`, `lowRam`, `totalMemMb`, `availMemMb` (`ActivityManager.getMemoryInfo`), `freeDiskMb` (`filesDir.usableSpace`), `perfTier` (`PerfTier`), `toneInstalled/toneEnabled`, `gigaInstalled/gigaEnabled`, `recognizer`, `warmupOnBoot`, `micGranted`, `notificationsGranted`, `batteryOptIgnored` (`PowerManager.isIgnoringBatteryOptimizations`), `timezone`, `theme`.
- Состояние моделей/настроек берётся в `NotesApp.onCreate` после инъекции (`ModelDownloadManager.isInstalled`, `SecureSettingsStore`) и **после** первого `AppLog.logProcessStart()`; для быстрого старта собрать строки в `applicationScope.launch(Dispatchers.IO)` и вызвать `AppLog.rewriteHeader(...)` ещё раз (метод идемпотентный).
- В тело файла в начале каждого процесса дописывать маркер `===== process start pid=<pid> session=<n> =====` (простая запись через `AppLog.i("NotesApp", ...)` с особым тегом достаточна). Номер сессии — счётчик в `NotesPrefs` (`log_session`), инкремент на старте. Шапка остаётся только про последний процесс.

### 7.4 Причины завершения прошлого процесса (P1, API 30+)
`NotesApp.onCreate` (в `applicationScope`, IO): `ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 5)`; для записей новее сохранённого `last_exit_logged_ts` (`NotesPrefs`) писать `AppLog.w("ProcessExit", "reason=<имя> status=<n> importance=<n> pssKb=<n> rssKb=<n> at=<время> desc=<санитизированный description>")`. Имя причины — через `internal fun exitReasonName(code: Int): String` (`REASON_CRASH`, `REASON_CRASH_NATIVE`, `REASON_ANR`, `REASON_LOW_MEMORY`, `REASON_EXCESSIVE_RESOURCE_USAGE`, `REASON_USER_REQUESTED`, ...; на API < 30 функцию не вызывать). Это единственный способ увидеть нативные падения sherpa/onnxruntime и убийство по памяти.

### 7.5 Глубина стека (P2)
`AppLog.renderError`: 24 кадра для первого исключения, по 6 кадров для каждой из `cause` (до 4 уровней), `suppressed` — только класс и сообщение. Лимит `MAX_MESSAGE = 240` для строки сообщения оставить; кадры стека лимитом не резать. Размер лога по-прежнему ограничен ротацией.

### 7.6 R8: читаемые стеки (P1)
- `app/proguard-rules.pro`: добавить
  ```
  -keepattributes SourceFile,LineNumberTable
  -renamesourcefileattribute SourceFile
  ```
- `.github/workflows/android.yml`: после `assembleOptimized` загрузить `app/build/outputs/mapping/optimized/mapping.txt` артефактом `notes-optimized-mapping` (`if-no-files-found: error`). В `docs/STACK.md` описать расшифровку: `retrace mapping.txt trace.txt` (R8 retrace из `cmdline-tools`).
- Версию в логе (`versionName`/`versionCode`) уже пишет шапка — по ней подбирается mapping.

### 7.7 Санитайзер (P2)
`AppLog.sanitize`: `https://huggingface.co/...` → `[url huggingface.co]` (оставлять только хост, без пути и query). Правило пути/PIN не менять. Обновить `AppLogTest` (ожидание `"HTTP 404 [url]"` → `"HTTP 404 [url example.com]"`), добавить тест на хост с портом и на URL без хоста.

### 7.8 Тесты
`AppLogTest`: расширенная шапка (`processStartLines` содержит новые ключи), маркер процесса, глубина стека (`cause` с кадрами), хост в `[url host]`; `ExitReasonTest`: `exitReasonName` для известных кодов и неизвестного.

**Готово, когда:** по одному присланному `app.log` видно: сборку, устройство, память/диск, состояние моделей и разрешений, границы запусков, причины предыдущих падений (в т.ч. нативных), ошибки STT, плеера, сохранения; стеки из `optimized` расшифровываются артефактом `mapping.txt`.

---

## 13. Окно «Информация» при первом запуске и «Правила использования» (P1)

### 13.1 Хранение флага
`data/secure/NotesPrefs.kt`: ключи `INFO_SEEN_ID = "info_seen_id"` (Int, 0 по умолчанию), `TERMS_ACCEPTED_AT = "terms_accepted_at"` (Long), `TERMS_VERSION = "terms_version"` (String). Обычные (не шифрованные) prefs, чтобы не открывать keystore на первом кадре — по образцу `ThemeStore`.

Новый `data/secure/InfoNoticeStore.kt` (`@Singleton`, Hilt):
```kotlin
class InfoNoticeStore @Inject constructor(@ApplicationContext context: Context) {
    val pendingId: StateFlow<Int?>   // id уведомления, которое надо показать, или null
    fun markSeen(id: Int, termsVersion: String?)   // commit(): сохранить id, время и версию правил
}
```
Чистая функция `internal fun pendingNotice(seenId: Int, latestId: Int): Int? = if (seenId < latestId) latestId else null` + тест. Так тот же механизм послужит для «Что нового» (увеличить `latestId`, добавить контент; для него `dismissible = true`).

### 13.2 Контент (`ui/legal/InfoNotices.kt`)
```kotlin
internal data class InfoNotice(
    val id: Int,
    val title: String,
    val body: AnnotatedString,
    val footer: AnnotatedString?,   // мелкий текст внизу
    val action: String,             // подпись кнопки
    val dismissible: Boolean,
)
```
Первое уведомление (`id = 1`, `dismissible = false`, `title = "Информация"`, `action = "Продолжить"`). Тело строится `buildAnnotatedString` (жирный — `SpanStyle(fontWeight = Bold)`, подчёркнутый жирный — `Bold + TextDecoration.Underline`). **Текст дословно:**

1. Первая строка, жирным: `Будем очень признательны, если поставите нам оценку 🙏🥹`
2. Абзац: `Мы очень рады, что вы решили попробовать наше приложение. Мы стабильно занимаемся развитием и исправлением работы данного сервиса. Если появятся идеи нового функционала, либо обнаружите ошибки - обязательно сообщите нам по контактам в разделе "Настройки". ` и далее **подчёркнутым жирным**: `Также рекомендуется прочитать короткую информацию ниже.`
3. Абзац: `Для корректного использования голосовых заметок крайне рекомендуется установить 2 модели распознавания и обработки в настройках приложения. Без них возможна полная (или частичная) неработоспособность функции записи аудиозаметок. Все заметки и аудиозаписи обрабатываются исключительно на Вашем устройстве и не отправляются в интернет. При отправке логов содержимое записей (заметок) не отправляется; логирование происходит только системных показателей для обработки ошибок.`
4. Пустая строка, жирным: `Приятного использования!`

Мелкий текст внизу (`footer`, `bodySmall`, `onSurfaceVariant`): `Нажимая кнопку "Продолжить", вы соглашаетесь с ` + кликабельное подчёркнутое `правилами использования` + ` приложения.` — через `LinkAnnotation.Clickable(tag = "terms")` (`androidx.compose.ui.text.LinkAnnotation`, есть в Compose 1.7); обработчик открывает `TermsDialog`.

Эмодзи `🥹` (Unicode 14) отсутствует в системных шрифтах Android < 13 и покажется «квадратиком». Правка: функция `internal fun supportsGlyph(text: String): Boolean = Paint().hasGlyph(text)` (API 23+, minSdk 26 — безопасно); в `InfoNotices` строка `🙏🥹` собирается как `🙏` + (`🥹`, если `hasGlyph("🥹")`). Текст владельца иначе не менять.

### 13.3 Окно `InfoDialog` (`ui/components/InfoDialog.kt` или `ui/legal/InfoDialog.kt`)
- `Dialog(onDismissRequest = { if (notice.dismissible) onAction() }, properties = DialogProperties(dismissOnBackPress = notice.dismissible, dismissOnClickOutside = notice.dismissible, usePlatformDefaultWidth = false))`. Для первого запуска — **ни Back, ни тап снаружи не закрывают окно**.
- Вёрстка как у `AppDialog` (`surface`, скругление 28.dp, отступы 22.dp), но в `Column` с ограничением высоты `heightIn(max = maxHeight * 0.88f)` (через `BoxWithConstraints`):
  1. заголовок `Информация` (фиксирован);
  2. `Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))` — прокручиваемое тело;
  3. мелкий футер (фиксирован);
  4. кнопка «Продолжить» (фиксирована; стиль как primary: фон `NotesPurple`, белый текст `SemiBold`, скругление 16.dp, на всю ширину).
- Градиентные затухания (12.dp) у верхней и нижней границы прокручиваемой области, когда есть что прокручивать (`canScrollBackward/Forward`) — как в 3.2.
- Анимации: стандартное появление `Dialog`. Дополнительные бесконечные анимации не добавлять.
- Доступность: заголовок `semantics { heading() }`; кнопка с `role = Button`; ссылка в футере помечена `LinkAnnotation`.

### 13.4 Подключение
- `ui/ShellViewModel.kt`: `val infoPending: StateFlow<Int?>` из `InfoNoticeStore`, `fun acceptInfo(id: Int)` → `store.markSeen(id, TermsOfUse.VERSION)` + `AppLog.i("Shell", "info accepted id=$id terms=${TermsOfUse.VERSION}")`.
- `ui/NotesRoot.kt`: в конце корневого `Box` (после `ToastHost` и диалога разрешений, чтобы окно было сверху) — `val pending by shell.infoPending.collectAsStateWithLifecycle(); pending?.let { InfoDialog(...) }`; `showTerms` — локальное состояние; при `showTerms` поверх рисуется `TermsDialog`.
- Окно показывается на любом экране (в т. ч. при запуске из виджета/уведомления с `pendingNoteId`/`askForMic`): диалог поверх всего, остальной поток не блокируется; пока окно открыто, нижняя плашка недоступна из-за модальности.
- Состояние диалога переживает поворот/пересоздание: флаг «принято» пишется только по «Продолжить», поэтому при убийстве процесса окно покажется снова.

### 13.5 Экран правил `TermsDialog`
`ui/legal/TermsDialog.kt`: диалог поверх (для первого запуска — второй `Dialog` над `InfoDialog`; Back и «Закрыть» закрывают только его): заголовок «Правила использования» (фиксирован), прокручиваемый текст, снизу кнопка «Закрыть». Текст берётся из `ui/legal/TermsOfUse.kt`:

```kotlin
internal object TermsOfUse {
    const val VERSION = "1.0"
    const val EDITION = "Редакция 1.0 от 23 ноября 2026 г."
    val sections: List<TermsSection>   // TermsSection(title: String, paragraphs: List<String>)
}
```
Заголовки разделов — жирным, абзацы — `bodyMedium`.

### 13.6 Текст правил использования
(Перенести в `TermsOfUse.sections` без изменения смысла. Юридическая вычитка владельцем рекомендуется до публикации в магазинах.)

**Правила использования приложения «Заметки»** — Редакция 1.0 от 23 ноября 2026 г.

**1. Общие положения**
1.1. Приложение «Заметки» (далее — Приложение) предназначено для создания текстовых и голосовых заметок на вашем устройстве. Правообладатель и разработчик — Loomixx (далее — Разработчик). Контакты: loomixx.dev@yandex.ru, Telegram @alekseevich47.
1.2. Устанавливая и используя Приложение, вы подтверждаете, что ознакомились с настоящими Правилами и принимаете их. Если вы не согласны с Правилами, удалите Приложение.
1.3. Приложение предоставляется на условиях простой (неисключительной) лицензии для личного некоммерческого использования «как есть», без гарантий бесперебойной работы.

**2. Какие данные хранятся и где**
2.1. Тексты и названия заметок, оформление, таблицы, аудиозаписи, хэш PIN-кода и настройки хранятся только в памяти вашего устройства, в закрытом каталоге Приложения.
2.2. В Приложении нет учётных записей, серверной части, рекламы и сервисов сбора статистики. Разработчик не получает заметки, аудиозаписи и расшифровки и не имеет к ним доступа.
2.3. Так как данные заметок и аудио не передаются Разработчику, он ими не располагает и не может предоставить их третьим лицам, в том числе по запросам государственных органов и иных лиц. Обработка таких данных осуществляется вами на вашем устройстве для личных нужд; тайна частной жизни и переписки защищена статьями 23 и 24 Конституции Российской Федерации.
2.4. Облачное резервное копирование Android для Приложения отключено. При удалении Приложения или очистке его данных заметки и аудио удаляются безвозвратно. Сохраняйте нужное самостоятельно (например, функцией «Поделиться» у аудиозаписи).

**3. Запись и распознавание речи**
3.1. Запись начинается только после вашего нажатия на микрофон (в Приложении, на виджете или в уведомлении) и идёт, пока вы её не остановите. Во время записи показывается уведомление.
3.2. Модели распознавания T-one и GigaAM работают на вашем устройстве без подключения к интернету. Аудио и текст при этом никуда не передаются.
3.3. Режим «Системное» использует службу распознавания речи вашего устройства (например, сервис Google или производителя). Такая служба может передавать аудио на серверы её владельца по собственным правилам; Разработчик на это не влияет. Для полностью локальной обработки используйте T-one.
3.4. Результат распознавания может содержать ошибки. Не используйте его как единственный источник юридически, медицински или финансово значимой информации.

**4. Подключение к интернету**
4.1. Интернет нужен Приложению только для скачивания моделей распознавания по вашей кнопке в Настройках с общедоступного сервера Hugging Face (huggingface.co). Серверу при обычном запросе становятся доступны ваш IP-адрес и технические параметры соединения; их обработка регулируется правилами этого сервиса.
4.2. Других данных Приложение в интернет не отправляет.

**5. Журнал работы и сообщения об ошибках**
5.1. Приложение ведёт технический журнал на устройстве: версия Приложения, модель устройства и версия Android, состояние моделей и настроек, события и ошибки. Тексты заметок, их названия, расшифровки, аудио и PIN в журнал не попадают.
5.2. Журнал и сообщения об ошибках отправляются только по вашему действию («Отправить лог», «Сообщить об ошибке») через выбранное вами почтовое приложение. Вы видите письмо до отправки.
5.3. В форме «Сообщить об ошибке» вы указываете имя и описание проблемы и по желанию прикрепляете скриншоты. Скриншоты могут содержать фрагменты ваших заметок — проверьте их перед отправкой. Эти сведения поступают на почту Разработчика, используются только для анализа проблемы и ответа вам, не передаются третьим лицам и хранятся не дольше, чем это нужно для рассмотрения обращения.
5.4. Отправляя сообщение, вы даёте согласие на обработку указанных в нём сведений (в том числе имени и адреса вашей электронной почты) для этой цели в соответствии с Федеральным законом от 27.07.2006 № 152-ФЗ «О персональных данных». Согласие можно отозвать и потребовать удаления сведений, написав на loomixx.dev@yandex.ru.

**6. Разрешения**
Микрофон — запись заметок. Уведомления — показ статуса записи и готовых заметок. Службы переднего плана — запись, воспроизведение и удержание моделей в памяти. Запуск при включении устройства и работа без ограничений батареи — только если вы включили «Автозагрузку моделей». Интернет — только для п. 4.

**7. PIN-код и «Скрытый текст»**
7.1. PIN-код хранится в виде необратимого хэша в защищённом хранилище устройства.
7.2. «Скрытый текст» скрывает фрагмент в интерфейсе Приложения, но не является шифрованием. Не используйте его как единственную защиту особо важных сведений (пароли, реквизиты платёжных карт).
7.3. Для сброса забытого PIN-кода обратитесь к Разработчику по контактам из п. 1.1.

**8. Ваши права**
8.1. Вы можете в любой момент удалить любую заметку и её аудио в Приложении, а также все данные — удалив Приложение или очистив его данные в настройках Android.
8.2. По сведениям, отправленным Разработчику по п. 5, вы вправе запросить доступ, уточнение или удаление, написав на loomixx.dev@yandex.ru. Разработчик ответит в сроки, установленные законодательством Российской Федерации.

**9. Ответственность**
9.1. Резервное копирование данных — ваша ответственность. Разработчик не отвечает за потерю данных из-за сбоя, удаления Приложения, действий операционной системы или производителя устройства (в том числе агрессивного энергосбережения).
9.2. Запись разговоров и звуков с участием других лиц может требовать их согласия. Вы самостоятельно отвечаете за соблюдение законодательства (в частности, о неприкосновенности частной жизни) при использовании Приложения.

**10. Сторонние компоненты**
Приложение использует открытые компоненты и модели распознавания речи, которые распространяются на условиях собственных открытых лицензий их авторов.

**11. Изменение Правил и применимое право**
11.1. Разработчик может изменять Правила; актуальная редакция доступна в Приложении (Настройки → «Правила использования»). Продолжая использование после обновления, вы принимаете новую редакцию.
11.2. К Правилам применяется законодательство Российской Федерации.

### 13.7 Кнопка в настройках
`ui/settings/SettingsScreen.kt`, **в самом низу** колонки (после «Отправить лог»): `ActionText("Правила использования") { showTerms = true }` и `if (showTerms) TermsDialog(onClose = { showTerms = false })`. Состояние `showTerms` — `rememberSaveable`.

### 13.8 Тесты
- `InfoNoticeTest`: `pendingNotice(0,1)=1`, `pendingNotice(1,1)=null`; содержимое `InfoNotices.first`: `body.text` содержит ключевые фразы дословно, жирный диапазон на первой строке и на «Приятного использования!», подчёркнутый жирный на «Также рекомендуется…», `dismissible == false`, `action == "Продолжить"`, `title == "Информация"`.
- `TermsOfUseTest`: непустые разделы, есть упоминания `152-ФЗ`, `loomixx.dev@yandex.ru`, `Hugging Face`, `«Системное»`.

### 13.9 Пункты для решения владельца (не блокируют шаги выше)
1. **Согласованность текста.** В тексте владельца сказано, что «все заметки и аудиозаписи обрабатываются исключительно на устройстве». Для режима «Системное» это не гарантировано (системная служба может ходить в сеть, см. п. 3.3 правил), а модели скачиваются из сети. В правилах это раскрыто. Также скриншоты в «Сообщить об ошибке» могут содержать текст заметок. Формулировку диалога менять только по решению владельца.
2. **(P2, STT, малое изменение)** `stt/PlatformTranscriber.listen()`: добавить `putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)` — подсказка системной службе использовать офлайн-модель, если она есть. Правка в одну строку; выполнять только после согласия владельца (правило §0.4).
3. **Реквизиты** (ФИО/ИП/ИНН/адрес правообладателя) для публикации в RuStore/Google Play и ссылка на опубликованный URL политики конфиденциальности — прислать владельцу отдельно; в код не добавлять заглушки.

---

## 8. «Сообщить об ошибке» (P1)

**Решение по способу отправки.** Письмо уходит через **почтовое приложение пользователя** (`Intent.ACTION_SEND_MULTIPLE`, `message/rfc822`, адрес `SettingsCopy.EMAIL` из `loomixx.dev@yandex.ru`). Бесшумную отправку по SMTP из приложения **не делать**: пароль почты окажется в APK и будет извлечён (захват ящика, спам), а свой сервер потребует политики обработки данных и защиты от злоупотреблений. Если позже понадобится отправка без почтового клиента — отдельная задача: HTTPS-эндпоинт-посредник с ограничением частоты, без секретов в приложении.

### 8.1 UI: пункт и форма
- `SettingsScreen.kt`, секция «Диагностика»: сначала `ActionText("Сообщить об ошибке") { showReport = true }`, затем существующий `ActionText("Отправить лог")`.
- Новый файл `ui/settings/BugReportDialog.kt` — полноэкранный/почти полноэкранный `Dialog` в стиле `AppDialog`/`PinDialog` (свои компоненты, без `AlertDialog`):
  - заголовок «Сообщить об ошибке» (фиксирован), кнопка закрытия;
  - поле «Ваше имя» (`BasicTextField`, одна строка, до 60 символов, обязательное);
  - поле «Что случилось» (многострочное, 10–2000 символов, обязательное; счётчик символов);
  - блок «Скриншоты (по желанию)»: превью-миниатюры 64.dp с кнопкой удаления, кнопка «Прикрепить» (до 5 штук);
  - переключатель «Приложить технический журнал» (по умолчанию включён; подпись: «Без текста ваших заметок»);
  - мелкий текст согласия с кликабельным «правилами использования» (открывает `TermsDialog` из шага 13): `Отправляя сообщение, вы соглашаетесь с правилами использования приложения.`;
  - кнопки «Отмена» и «Отправить» (активна, когда имя и описание валидны); в конце — подсказка, что откроется почтовое приложение.
  - Контент формы в `verticalScroll`; шапка и кнопки зафиксированы; клавиатура не перекрывает поле (`imePadding()`/`windowInsetsPadding(WindowInsets.ime)`, как в `EditorScreen`).

### 8.2 Выбор скриншотов
`rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5))` с `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)` — системный Photo Picker, **разрешения не нужны**. Общий лимит 5; дубликаты по `Uri` отбрасывать.

### 8.3 Подготовка вложений (вне главного потока)
Новый `data/report/BugReportMailer.kt`:
```kotlin
object BugReportMailer {
    suspend fun prepare(context: Context, form: BugReportForm, screenshots: List<Uri>, withLog: Boolean): PreparedReport
    fun launch(context: Context, prepared: PreparedReport)
    fun purgeStale(context: Context)  // удалить cache/bugreport старше 1 суток
}
```
- Каждый скриншот: декодировать с `inSampleSize` и ограничением длинной стороны 1600px, пересохранить как JPEG quality 85 в `cacheDir/bugreport/shot_<n>.jpg`. Перекодирование **удаляет EXIF** (геометки, модель камеры) — это требование приватности. Недекодируемый файл пропускается с `AppLog.w("BugReport", "screenshot skipped")`. Размер вложения ≤ 1.5 МБ (понижать quality до 60), суммарно ≤ 8 МБ.
- Журнал: копия `AppLog.file()` в `cacheDir/bugreport/app.log` (снимок, чтобы ротация не изменила файл при отправке). Если файла нет — отправка без него.
- `FileProvider`: в `app/src/main/res/xml/file_paths.xml` добавить `<cache-path name="bugreport" path="bugreport/" />` (путь `share/` уже есть).
- Intent: `ACTION_SEND_MULTIPLE`, `type = "message/rfc822"`, `EXTRA_EMAIL = arrayOf(EMAIL)`, `EXTRA_SUBJECT = "Заметки <versionName>: сообщение об ошибке"`, `EXTRA_TEXT = BugReportMail.body(form, appInfo)`, `EXTRA_STREAM = ArrayList<Uri>`, `clipData` со всеми Uri, `FLAG_GRANT_READ_URI_PERMISSION`. Запуск через `Intent.createChooser(...)`; если нет `Activity` — `FLAG_ACTIVITY_NEW_TASK`.
- Нет почтового клиента (`ActivityNotFoundException`): скопировать адрес в буфер, показать тост «Не найдено почтовое приложение. Напишите на loomixx.dev@yandex.ru» (адрес в буфер — через `ClipboardManager`), диалог не закрывать, введённые данные не терять.
- В `onFailure` и всех ветвях писать `AppLog.i("BugReport", ...)` с количеством вложений, флагом журнала, длиной текста; **сам текст, имя и пути в журнал не писать**.
- `purgeStale` вызывать из `NotesApp.onCreate` (в `applicationScope`, IO).

### 8.4 Тело письма — чистая функция
`BugReportMail.body(form, info)` (без Android-зависимостей) формирует:
```
Имя: <name>

Что случилось:
<text>

—
Заметки <versionName> (<versionCode>), <buildType>
Android <release> (API <sdk>), <manufacturer> <model>
```
Ограничить итоговую длину 8000 символов; удалить управляющие символы кроме `\n`. Валидация (`BugReportForm.validate()` → `ok / nameBlank / textTooShort / textTooLong`) — также чистая функция.

### 8.5 Тесты
`BugReportMailTest`: тело письма, обрезка, удаление управляющих символов, валидация (граничные значения 9/10/2000/2001 символов, пробелы), тема письма. Для `prepare` (Android) — без юнит-теста.

**Готово, когда:** в настройках над «Отправить лог» есть «Сообщить об ошибке»; форма требует имя и описание; выбираются до 5 скриншотов; открывается почтовое приложение с готовым письмом на `loomixx.dev@yandex.ru`, вложениями (без EXIF) и журналом; при отсутствии почтового клиента данные не теряются.

---

## Z. Документация и финальная проверка

### Z.1 Документы
- `docs/STACK.md`: тема — «светлая / тёмная» (убрать «система»); логи только через `AppLog`; mapping и retrace; информационное окно (`InfoNoticeStore`) и правила использования; форма «Сообщить об ошибке» (почтовый клиент, без SMTP); меню выделения (общий каркас, позиционирование по `reserve`); распознаватель не может быть T-one без модели.
- `docs/PRODUCT_SPEC.md` §3–4: убрать «Светлая / тёмная — Как в системе»; добавить «Информация при первом запуске», «Правила использования», «Сообщить об ошибке».
- `README.md`: в «CI» — артефакт `notes-optimized-mapping`.

### Z.2 Автотесты
`./gradlew testDebugUnitTest assembleDebug assembleOptimized` — зелёные. Новые тесты: `RecognizerChoiceTest`, `ThemeModePrefsTest` (обновлён), `SettingsCopyTest` (обновлён), `SelectionMenuLayoutTest`, `SelectionMenuPlacementTest`, `WordBoundsTest`, `NoteSurfaceBridgeTest`, `EditorPolicyTest` (дополнен), `InfoNoticeTest`, `TermsOfUseTest`, `BugReportMailTest`, `ExitReasonTest`, `AppLogTest` (обновлён).

### Z.3 Ручная приёмка (устройства: 360dp-ширина, API 26/30/34, Tecno HiOS; fontScale 1.0 и 1.3; светлая и тёмная тема)

- [ ] 1: правка заметки не меняет время в списке; в заметке «последнее изменение» обновляется.
- [ ] 2: долгое нажатие на пустом месте поля → наше «Вставить»; системного окна нигде нет (тело и ячейки таблицы).
- [ ] 3: в компактном меню видны три точки; в расширенном — 6 строк (Выбрать всё, Обычный, Жирный, Курсив, Подчёркнутый, Скрытый), остальные прокручиваются, «Назад» и разделитель неподвижны.
- [ ] 4: открытие расширенного меню без скачков и переворотов над/под; тест при выделении у верха экрана и при открытой клавиатуре.
- [ ] 5: двойной тап по слову → выделение + компактное меню; по пробелу — ничего.
- [ ] 6: тап по тексту при закрытой клавиатуре ставит курсор в нужное место (в заметке с таблицей — в нужный текстовый блок); «Назад» скрывает клавиатуру и курсор; вторая «Назад» закрывает заметку.
- [ ] 7: присланный `app.log` содержит расширенную шапку, маркеры процессов, причины прошлых завершений, ошибки STT/плеера; `mapping.txt` есть в артефактах CI.
- [ ] 8: форма отправляет письмо с вложениями; без вложений тоже; без почтового клиента — тост, данные на месте.
- [ ] 9: новые тексты в «Автозагрузке», у GigaAM и в «Распознавании».
- [ ] 10: у кнопки «+» нет светлых граней в обеих темах.
- [ ] 11: без T-one чип T-one недоступен; после удаления T-one выбор возвращается на «Системное».
- [ ] 12: в «Темах» два варианта; у пользователя со старым «Как в системе» тема зафиксирована.
- [ ] 13: при первом запуске окно «Информация» не закрывается ни Back, ни тапом снаружи; прокручивается тело при фиксированных заголовке/футере/кнопке; ссылка на правила открывает окно правил поверх; «Продолжить» закрывает окно, оно больше не показывается; «Правила использования» внизу настроек открывает тот же текст.

### Z.4 Git
Коммиты по шагам (`Show creation time in the notes list`, `Add a custom paste bubble` и т. д.). Перед `git push` — проверить сообщение каждого коммита на trailer `Co-authored-by: Cursor <cursoragent@cursor.com>` и снять его (§0.10).
