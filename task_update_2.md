# task_update_2 — жесты выделения, меню выделения, «Вставить», автоскролл, таблицы

Проект: `com.alekseevich47.notes` (Kotlin 2.0.21, Compose BOM 2024.10.01 → Compose 1.7.x, Room 2.6.1, Hilt, Haze 1.5.2, minSdk 26, targetSdk 35).
План для другого агента-исполнителя. Пути от корня репозитория. В коде ссылаться на **имена функций**, не на номера строк.

## 0. Правила исполнителя (обязательно)

Действуют все правила из `task_update_1.md` §0 (один шаг = один коммит; `./gradlew testDebugUnitTest assembleDebug` после каждого шага, перед финалом `assembleOptimized`; точечные правки; формат хранения и схему Room не трогать; STT, запись, виджеты не трогать; AGSL/`RuntimeShader` запрещены; свои диалоги и меню, системный текстовый тулбар не показывать; нет перекомпозиций на каждую клавишу; нет бесконечных анимаций в idle; в логах нет содержимого заметок и PIN). Дополнительно:

1. Новую чистую логику (без Compose и Context) класть в `internal fun` / `internal object` и покрывать JVM-тестами в `app/src/test/java/com/alekseevich47/notes/` по образцу `SelectionTapTest`, `SelectionMenuPlacementTest`, `SelectionMenuLayoutTest`.
2. Любое изменение редактора делать **в обоих местах**: `ui/editor/RichEditor.kt` (`RichNoteEditor`, тело заметки) и `ui/editor/TableEditor.kt` (`TableCellEditor`, ячейка). Они дублируют логику выделения, меню и «Вставить». Общее выносить в новые файлы `ui/editor/*` минимальным выносом, а не переписывать оба файла.
3. `Popup`/окна не должны держать анимацию и перехват касаний, пока скрыты (см. шаг 6).
4. Git: коммитит агент. **Перед `git push` выполнить `git log -n 5 --format=%B` и снять trailer `Co-authored-by: Cursor <cursoragent@cursor.com>`** (amend собственного ещё не запушенного коммита). Не пушить коммиты с этим trailer. Деструктивные операции (filter-repo, force-push, reset --hard) без явного запроса не выполнять.
5. Многое здесь зависит от поведения Compose/IME на устройстве (Tecno/HiOS в приоритете). Где написано «**проверить на устройстве**», сначала воспроизвести проблему и добавить временный лог через `AppLog.i("EditorSel", ...)` (без текста заметки, только числа: offsets, y, alpha), потом править. Временные логи убрать в том же шаге.
6. После всех шагов обновить `docs/STACK.md` (раздел «UI», абзац про меню выделения и «Вставить»), `docs/PRODUCT_SPEC.md`, `README.md` при необходимости (шаг 11).

## Карта кода (что где лежит сейчас)

| Что | Файл / функция |
|--|--|
| Двойной тап по слову | `ui/editor/WordDoubleTap.kt` — `Modifier.onWordDoubleTap` (Initial-pass, события не потребляет) |
| Тело заметки, выделение, меню, вставка | `ui/editor/RichEditor.kt` — `RichNoteEditor`, `RoutingTextToolbar`, `pasteAnchor`, `menuVisible`, `menuOverflow` |
| Ячейка таблицы | `ui/editor/TableEditor.kt` — `TableBlock` (состояние `menu: TableMenu?`), `TableCellEditor` (свои `menuVisible`, `pasteAnchor`, `RoutingTextToolbar`) |
| Меню строки/столбца/ячейки | `ui/editor/TableMenus.kt` — `TableActionsPopup`; строки меню формирует `menuLines(...)` в `TableEditor.kt` |
| Всплывающее окно выделения | `ui/editor/SelectionMenu.kt` — `SelectionActionsPopup`, `SelectionBubble`, `PasteActionPopup`, `PrimaryMenu`, `OverflowMenu`, `placeSelectionMenu`, `PlateReserve` |
| Тапы по телу, очистка выделения | `ui/editor/SelectionSurface.kt` (`NoteSurfaceBridge`, `observeSelectionTaps`), `ui/editor/SelectionTap.kt` |
| Экран редактора, скролл, IME, шапка | `ui/notes/EditorScreen.kt` — `scroll`, `surface.onEmptyTap`, `focusLastTextBlockAtEnd`, `shouldClearFocusAfterImeHide`, `revealCaretDelta` |
| Контроллер панели форматирования | `ui/editor/EditorFormatBar.kt` — `NoteEditorController` |
| Манифест | `app/src/main/AndroidManifest.xml` (`windowSoftInputMode=adjustResize`, edge-to-edge в `MainActivity`) |

Корень экрана: `Column` с `windowInsetsPadding(statusBars ∪ navigationBars ∪ ime)`. Поэтому при открытой клавиатуре область скролла **уменьшается**, а не перекрывается.

## Порядок выполнения

```
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11
```

Зависимости: 3, 4, 2(haptic) используют жестовый модификатор из шага 1. 7 использует контроллер видимости из шага 6. 8 использует сигнал «выделение тянут» из шага 6. 10 использует шаги 4–5.

---

## 1. Единый жестовый модификатор редактора (основа для пунктов 1, 4, 9)

**Что сейчас.** `onWordDoubleTap` в `WordDoubleTap.kt` ждёт `longPressTimeoutMillis`; если палец не поднят — просто выходит (`released == null`, `lastPosition = null`). Именно в этой точке можно ловить долгое нажатие, не мешая Compose.

**Что сделать.**
- Переименовать файл/модификатор: создать `ui/editor/EditorGestures.kt` с `Modifier.editorTapGestures(callbacks: EditorGestureCallbacks)` и **удалить** `WordDoubleTap.kt` (вызовы в `RichEditor.kt` и `TableEditor.kt` заменить).
- `EditorGestureCallbacks` (обычный класс, обновляется через `rememberUpdatedState`, не создавать новый на каждую композицию):
  - `onTapUp(position: Offset)` — палец поднят после короткого нажатия (первый тап или любой следующий).
  - `onDoubleTap(position: Offset)` — как сейчас `onWordDoubleTap`.
  - `onLongPress(position: Offset)` — вызывается **один раз в момент истечения `longPressTimeoutMillis`**, пока палец удерживается и не вышел за `touchSlop` (не ждать отпускания).
  - `onGestureEnd()` — палец поднят после long press (нужно, чтобы снять временные состояния).
- Логика Initial-pass остаётся прежней: события **не потребляются**, Compose сам ставит каретку и выделяет слово при long press.
- Двойной тап: сохранить текущие проверки (`doubleTapTimeoutMillis`, `reach = slop * 2`), но `previous` считать только если предыдущий жест был именно коротким тапом (не long press).
- Чистую часть вынести в `internal object TapClassifier` (или функции) и покрыть тестом `EditorGesturesTest.kt`: `isDoubleTap(previousUp, nextDown, previousPos, nextPos, gapLimit, reach)`, длинное нажатие не считается тапом, движение больше slop отменяет жест.

**Файлы:** новый `ui/editor/EditorGestures.kt`; удалить `ui/editor/WordDoubleTap.kt`; правка вызовов в `RichEditor.kt`, `TableEditor.kt`.
**Готово, когда:** сборка зелёная, поведение двойного тапа прежнее, тест проходит.

---

## 2. Пункт 9. Долгое нажатие на слово: выделение + лёгкая вибрация

**Что сейчас.** Слово при long press выделяет сам Compose. Вибрации нет. В `AndroidManifest.xml` нет `VIBRATE`.

**Что сделать.**
- Новый файл `ui/editor/SelectionHaptics.kt`:
  - `internal object SelectionHaptics { fun tick(view: View) }`.
  - Основной путь: `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` (для API ≥ 30 допустим `CONFIRM`; не использовать `FLAG_IGNORE_GLOBAL_SETTING`, уважаем системную настройку пользователя).
  - Запасной путь, если системный хаптик выключен/не сработал (на части OEM): `Vibrator`/`VibratorManager` + `VibrationEffect.createPredefined(EFFECT_TICK)` на API ≥ 29, иначе `createOneShot(12, 60)`. Для запасного пути добавить `<uses-permission android:name="android.permission.VIBRATE" />` (normal permission, диалога нет). Запасной путь включать только если `Settings.System.HAPTIC_FEEDBACK_ENABLED != 0`.
  - Никаких повторяющихся вибраций: один вызов на один long press.
- В `RichNoteEditor` и `TableCellEditor` в `onLongPress` из шага 1: если `layout` есть и точка попала на **слово** (`WordBounds.at(field.text, layout.getOffsetForPosition(pos)) != null` **и** `glyphHit`-проверка: палец на самом тексте, а не в пустом хвосте строки; в `TableEditor.kt` уже есть `glyphHit`, вынести его в общий `internal` файл и использовать в обоих местах) — вызвать `SelectionHaptics.tick(LocalView.current)`.
- Если слово скрытое (`HiddenText.cursorInside(...)` и `!hiddenUnlocked`) — вибрацию не давать (выделения не будет).
- Для пустого места (шаг 4) вибрацию не давать, либо отдельный более слабый тик — на усмотрение, по умолчанию не давать.

**Файлы:** новый `ui/editor/SelectionHaptics.kt`; `RichEditor.kt`, `TableEditor.kt`; `AndroidManifest.xml`.
**Готово, когда:** на устройстве долгий тап по слову даёт один короткий тик и слово выделено; тап по пустому месту — без тика.

---

## 3. Пункт 1. Один тап = одиночная каретка, двойной тап = только выделение (без «трёх курсоров»)

**Причина.** После первого тапа Compose рисует каретку и ручку каретки (teardrop). Второй тап выделяет слово и показывает две ручки выделения, а каретка/ручка первого тапа ещё видны один-два кадра. Итого три «курсора».

**Что сделать.**
- В `RichNoteEditor` и `TableCellEditor` добавить состояние `caretHold: Boolean` (`mutableStateOf(false)`).
- В `onTapUp` (шаг 1): если выделение свёрнуто — `caretHold = true`, отменить прошлый `holdJob`, запустить `holdJob = scope.launch { delay(viewConfiguration.doubleTapTimeoutMillis.coerceAtMost(260)); caretHold = false }`. Если за это время пришёл `onDoubleTap` — `holdJob.cancel()`, `caretHold` оставить `true` до применения выделения (оно применяется в `scope.launch { withFrameNanos {}; field = field.copy(selection = range) ... }`), затем сразу `caretHold = false` (при ранжированном выделении каретка всё равно не рисуется).
- Пока `caretHold == true`:
  - `cursorBrush = SolidColor(Color.Transparent)` вместо `SolidColor(NotesPurple)`;
  - ручку каретки скрыть: обернуть `BasicTextField` в `CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(handleColor = Color.Transparent, backgroundColor = <текущий backgroundColor из LocalTextSelectionColors.current>))`. Не менять цвет ручек в обычном состоянии.
- Каретка набираемого текста не должна мигать с задержкой: `caretHold` включается **только** от тапа по тексту, не от ввода, не от `caretRequest`, не от фокуса при открытии клавиатуры.
- Чистая логика задержки вынести в `internal class CaretHoldGate(delayMillis)` с функциями `onTap(now)`, `onDoubleTap()`, `isHeld(now)` и покрыть `CaretHoldGateTest.kt` (тап → hold; через delay → не hold; double → release; повторный тап продлевает).

**Файлы:** `RichEditor.kt`, `TableEditor.kt`, новый `ui/editor/CaretHoldGate.kt`, новый тест.
**Готово, когда:** одиночный тап показывает каретку с задержкой не больше `doubleTapTimeoutMillis`, двойной тап показывает **только** выделение с двумя ручками, без каретки до выделения.

---

## 4. Пункт 4. «Вставить» только по долгому нажатию на пустое место

**Причина.** Сейчас `PasteActionPopup` открывается из `RoutingTextToolbar.showMenu(...)` (`toolbar.onPasteMenu`), то есть **по запросу Compose** (после тапа по каретке, после фокуса и т.д.), а не по жесту. Флаг `suppressPlatform` сбрасывается в `releasePlatformMenu` и по набору текста, поэтому окно всплывает «в рандомных ситуациях».

**Что сделать.**
- В `RoutingTextToolbar` (`RichEditor.kt`): **полностью отключить показ вставки от Compose**. Ветку без `onCopyRequested/onCutRequested` в `showMenu` заменить на: `selectionMenu = false; pasteMenu = false; platform.hide(); return`. Поле `onPasteMenu`, `onPasteMenuHidden`, `suppressPlatform`, `releasePlatformMenu`, `shouldOfferPlatformPaste` и связанные места в `RichEditor.kt` и `TableEditor.kt` удалить (и тесты на `shouldOfferPlatformPaste` в `SelectionTapTest.kt` удалить/переписать). Системный тулбар по-прежнему не показываем.
- Источник `pasteAnchor` теперь **только** `onLongPress` (шаг 1):
  - условие: выделение свёрнуто (до жеста), в буфере есть текст (`clipboard.hasText()`), диалог PIN не открыт, блок не `readOnly`;
  - условие «пустое место»: нет слова под пальцем. Вынести чистую функцию `internal fun isBlankLongPress(text: String, offset: Int, onGlyph: Boolean): Boolean` в `EditorGestures.kt`: `true`, если `!onGlyph` (хвост строки, пустая строка, пространство под текстом внутри блока) **или** символ под/рядом с offset — пробел/перевод строки/пунктуация-разделитель (не буква/цифра). Не использовать `WordBounds.at` напрямую: он засчитывает разделитель, стоящий сразу после слова, как слово.
  - якорь: `layout.getCursorRect(offset)` по offset из `getOffsetForPosition(pos)`.
- Погасить `pasteAnchor` (`= null`) при: любом новом `down` на теле (уже есть `surface.addBodyDownListener(clearPaste)`), потере фокуса, изменении текста или выделения, открытии диалога, появлении ранжированного выделения, `BackHandler`, уходе приложения в фон.
- Gap: после long press на слове (выделение пошло) окно «Вставить» **не** показывать — `isBlankLongPress == false`.
- `BackHandler` в `RichNoteEditor`/`TableCellEditor`, завязанный на `pasteAnchor`, оставить: первый «назад» убирает окно «Вставить».

**Файлы:** `RichEditor.kt`, `TableEditor.kt`, `EditorGestures.kt`, `SelectionTap.kt` (удалить `shouldOfferPlatformPaste`), `SelectionTapTest.kt`.
**Тесты:** `BlankLongPressTest.kt` — пробел между словами, конец строки, пустая заметка, середина слова (false), буква (false).
**Готово, когда:** окно «Вставить» не появляется при обычных тапах, тапах по тексту после предыдущего показа и при наборе; появляется только по долгому нажатию на пустое место/между слов.

---

## 5. Пункт 5. «Вставить» переносим в расширенное меню выделения

**Что сделать (`ui/editor/SelectionMenu.kt`).**
- `PrimaryMenu`: оставить только «Вырезать», «Копировать» и ⋮. Убрать параметры `canPaste` и `onPaste`, убрать `if (canPaste) BubbleLabel("Вставить", ...)`.
- `OverflowMenu`: первой строкой (до «Выбрать всё») добавить `OverflowLabel("Вставить", onClick = onPaste)`, **только если `canPaste`**. Параметры `canPaste: Boolean`, `onPaste: () -> Unit` добавить.
- `SelectionActionsPopup`: сигнатура без изменений снаружи (`onPaste` остаётся), внутри передавать `canPaste` в `OverflowMenu` (оба места: резерв `ReserveOverflow` и основной `AnimatedContent`).
- `PlateReserve.matches(...)` уже учитывает `canPaste`; оставить, так как высота overflow теперь зависит от `canPaste` (на одну строку больше).
- `overflowVisibleRows`: максимум 6 видимых строк остаётся; список теперь до 10 строк, прокрутка и `menuScrollFades` работают как раньше. Обновить док-комментарий над `SelectionActionsPopup`.
- Для выделения, созданного двойным кликом или долгим нажатием, поведение одинаково (одно меню).
- `PasteActionPopup` (одна кнопка «Вставить» над каретой) не менять.

**Файлы:** `SelectionMenu.kt`, `SelectionMenuLayoutTest.kt` (обновить, если зависел от числа строк).
**Готово, когда:** в коротком меню «Вырезать / Копировать / ⋮», в расширенном «Вставить» первым пунктом (только при непустом буфере).

---

## 6. Пункты 2 и 3. Контроллер видимости меню выделения: плавное скрытие при тяге ручек и скролле, возврат после остановки

**Причины.**
- Пункт 3: после двойного тапа меню включается напрямую (`menuVisible = true` в `onWordDoubleTap`), но `RoutingTextToolbar.selectionMenu` остаётся `false`, поэтому `TextToolbarStatus` для Compose не `Shown`. Compose не вызывает `hide()` при начале тяги ручки и не вызывает `showMenu(...)` по её окончании как «новый жест»; меню не возвращается. Кроме того, при `hide()` меню удаляется из композиции мгновенно, без анимации.
- Пункт 2: нет единого состояния «меню временно скрыто», только булев `menuVisible`.

**Что сделать.**
- Новый файл `ui/editor/SelectionMenuController.kt` — `@Stable class SelectionMenuState` (по экземпляру на редактор/ячейку, `remember`):
  - `wanted: Boolean` — выделение существует и меню «логически» нужно (заменяет `menuVisible` как причину показа);
  - `suppressedBy: Set<Reason>` где `Reason ∈ {HANDLE_DRAG, SCROLL, SELECTION_CHANGING}`;
  - `alpha: Float` (`Animatable`): цель `0f`, если `suppressedBy` непусто или `!wanted`, иначе `1f`. Скрытие `tween(110)`, появление `tween(180)` после **задержки успокоения** 220 мс (debounce) с момента, когда `suppressedBy` стало пустым. Анимация идёт **только** при смене цели, никаких `infiniteTransition`.
  - `fun onSelectionChanged(old: TextRange, new: TextRange)` — если выделение осталось ранжированным и границы изменились: добавить `SELECTION_CHANGING` и перезапустить таймер debounce (это универсальный детектор тяги ручек, не зависящий от поведения `TextToolbar`).
  - `fun onScroll(inProgress: Boolean)` — добавляет/снимает `SCROLL`.
- Подписать:
  - `SELECTION_CHANGING`: в `RichNoteEditor`/`TableCellEditor` вызывать `state.onSelectionChanged` из места, где `field` получает новое значение (`applyIncoming`/`commit`) **и** в обработчике двойного тапа. Первое показание меню после создания выделения (двойной тап, long press, «Выбрать всё») проходит тот же debounce, но без пропадания: `alpha` стартует с `0f` и плавно поднимается.
  - `SCROLL`: `EditorScreen` отдаёт `scroll.isScrollInProgress` (через `snapshotFlow`) в общий `NoteEditorController` (поле `scrolling: Boolean`, snapshot state). Редактор читает его в `LaunchedEffect(formats.scrolling)` → `state.onScroll(...)`. Для ячеек таблицы так же (горизонтальный скролл `tableScroll` тоже учитывать).
  - `HANDLE_DRAG`: дополнительно к детектору выше использовать `toolbar.onSelectionGestureBegan` (добавить `HANDLE_DRAG`) и `toolbar.onSelectionGestureEnded` (снять). **Исправление пункта 3:** при создании выделения (двойной тап, «Выбрать всё») вызвать новый метод `RoutingTextToolbar.markSelectionShown()` (`selectionMenu = true`), чтобы `status` был `Shown`, и Compose стал вызывать `hide()` в начале тяги и `showMenu(...)` в конце. Не полагаться только на это: debounce из `SELECTION_CHANGING` обязателен как страховка.
- `SelectionActionsPopup`: добавить параметр `alpha: Float` (читать **внутри** `graphicsLayer { this.alpha = alpha }` лямбды, чтобы изменение не вызывало рекомпозицию списка меню). Ставить `graphicsLayer` на содержимое внутри `SelectionBubble`, **не** на `root.alpha` (им уже управляет `KeepMenuAboveHandles` для скрытия во время переноса окна — не ломать).
- Пока `alpha == 0f` и цель `0f` (меню скрыто) — **убирать `Popup` из композиции** (вызывать `SelectionActionsPopup` только при `alpha > 0f || state.target > 0f`), чтобы невидимое окно не перехватывало касания и не держало анимации. При возврате меню снова добавляется и плавно проявляется с `0 → 1`.
- Места вызова в `RichEditor.kt` и `TableEditor.kt` заменить: `if (shown != null && menuVisible)` → `if (shown != null && state.shouldCompose)`.
- Сохранить текущее поведение: свернулось выделение (`field.selection.collapsed`) → `wanted = false`, меню убирается без задержки.
- Меню не должно мигать при смене страницы primary/overflow (резерв размера остаётся).

**Файлы:** новый `ui/editor/SelectionMenuController.kt`; `SelectionMenu.kt` (`alpha`, `graphicsLayer`); `RichEditor.kt`; `TableEditor.kt`; `EditorScreen.kt` (проброс `scroll.isScrollInProgress` в `formats`); `EditorFormatBar.kt` (`NoteEditorController.scrolling`).
**Тесты:** `SelectionMenuStateTest.kt` — чистая модель без Compose: набор причин, debounce (вынести время в параметр, использовать `TestCoroutineScheduler`/ручной таймер), после последней смены выделения +220 мс → цель `1f`, скролл подавляет, коллапс выделения гасит сразу.
**Готово, когда:** после двойного тапа и растягивания ручек меню плавно исчезает во время тяги и плавно появляется после остановки; то же при скролле; меню не остаётся невидимым окном над текстом.

---

## 7. Пункт 2. Позиционирование меню: всегда над выделением, под ним только у верха экрана, с закреплением вне шапки

**Что сейчас.** `placeSelectionMenu` считает `above` относительно окна без учёта шапки (кнопки назад/отменить/вернуть/удалить) и без учёта панели форматирования/IME; когда выделения нет в видимой области, меню клампится к краю окна и может уходить под шапку. Симптом «меню наверху экрана при выделении выше середины» **проверить на устройстве** (залогировать `anchorBounds.top`, `selTop`, `reserved.height`, `above`, итоговый `y`); подозрение: `anchorBounds` приходит из контейнера, который прокручивается, и положение считается до последнего layout.

**Что сделать.**
- Ввести видимую область редактора в координатах окна: `data class VisibleViewport(top: Int, bottom: Int)` + `CompositionLocal` `LocalSelectionViewport` (`staticCompositionLocalOf<VisibleViewport?>{ null }`).
  - Провайдер в `EditorScreen.kt`: на контейнер `BoxWithConstraints(weight(1f))` (тот, что держит `verticalScroll`) повесить `onGloballyPositioned { it.boundsInWindow() }` и положить `top = bounds.top`, `bottom = bounds.bottom` в `remember` + `SideEffect` (не state, чтобы не вызывать рекомпозицию на каждый кадр скролла; `SelectionBubble` читает значение внутри `calculatePosition`).
  - `top` — это низ шапки (ниже кнопок), `bottom` — верх панели форматирования/миниплеера/клавиатуры.
- Переписать `placeSelectionMenu` (оставить `internal`, добавить параметры `viewportTop: Int`, `viewportBottom: Int`; старые вызовы в тестах адаптировать):
  1. Координаты: `selTop/selBottom` + `anchorBounds.top` → окно.
  2. Если выделение **полностью вне видимой области выше** (`selBottomWin < viewportTop`): меню закрепляется сверху: `y = viewportTop + marginPx`.
  3. Если **полностью ниже** (`selTopWin > viewportBottom`): меню закрепляется снизу: `y = viewportBottom - marginPx - plateHeight`.
  4. Иначе (выделение хотя бы частично видно): по умолчанию **над** выделением: `y = max(selTopWin, viewportTop) - gapPx - reservedHeight`. Если места над выделением нет (`y < viewportTop + marginPx`) — **под** выделением: `y = min(selBottomWin, viewportBottom) + gapPx`; если и снизу не помещается — закрепить сверху `viewportTop + marginPx`.
  5. Горизонталь не менять (центр по выделению, зажим по окну).
- Высоту для решения «выше/ниже» всё так же брать из `reserve` (не из показанной страницы), чтобы окно не прыгало при смене primary/overflow.
- Окно скрывается/показывается по шагу 6 при скролле, поэтому позиция пересчитывается, пока меню прозрачно; выставлять позицию нужно по последнему состоянию, а не по анимированному.
- Удалить магическую константу `+ 18` в ветке «под выделением» или вынести в `gapBelowPx`.

**Файлы:** `SelectionMenu.kt`, `EditorScreen.kt`, `SelectionMenuPlacementTest.kt` (новые кейсы), при необходимости `SelectionMenuLayoutTest.kt`.
**Тесты:** выделение в верхней трети (под), в середине (над), у самого низа (над), выделение выше видимой области (закреплено у `viewportTop`), ниже видимой области (закреплено у `viewportBottom`), очень высокое выделение, меню шире окна.
**Готово, когда:** меню всегда над выделением (если не мешает шапка/верх экрана), никогда не заходит на шапку, при выделении вне экрана закреплено на видимой границе.

---

## 8. Пункт 6. Тяга ручек выделения под клавиатуру: плавный автоскролл с нарастанием скорости

**Причина.** Рабочая область уменьшается под клавиатуру (`windowInsetsPadding(...ime)`). Когда палец заходит в зону клавиатуры, Compose не получает новых позиций в пределах поля: смещение «упирается» в последнюю видимую строку, выделение застывает вместе с лупой. Автоскролла нет.

**Что сделать.**
- Новый файл `ui/editor/SelectionAutoScroll.kt`:
  - `internal fun autoScrollVelocity(edgeY: Float, viewportTop: Float, viewportBottom: Float, zonePx: Float, maxPxPerSec: Float, heldMillis: Long): Float` — отрицательная у верхней границы (вверх), положительная у нижней, 0 вне зон. Зона: `zonePx = 56.dp`. Скорость: `lerp(minPxPerSec = 80.dp, maxPxPerSec = 900.dp, f)`, где `f = clamp(depth / zonePx) * rampFactor(heldMillis)`, `rampFactor` растёт от 0.35 до 1.0 за 900 мс (чем дольше держим, тем быстрее). Если палец ниже `viewportBottom` (в зоне клавиатуры) — глубина берётся за 1.0.
  - `internal fun extendSelectionOffset(layout: TextLayoutResult, activeEdgeOffset: Int, dyPx: Float): Int` — новое смещение активной границы после сдвига на `dyPx` в координатах слоя: `layout.getOffsetForPosition(Offset(cursorRect.center.x, cursorRect.center.y + dyPx))` с зажимом в `0..length`.
- Определение «тянут ручку» и какой конец тянут: по смене `field.selection` (если изменился `start` — тянут начало, если `end` — конец), результат хранится в `SelectionMenuState` из шага 6 (`dragging: Boolean`, `activeIsEnd: Boolean`, `anchor: Int`). Окончание: `RoutingTextToolbar.showMenu(ranged)` (Compose зовёт его после отпускания ручки; благодаря `markSelectionShown()` из шага 6 это надёжно) **или** 600 мс без изменений выделения и без собственного автоскролла (страховка).
- Цикл автоскролла (`LaunchedEffect(state.dragging)` в `EditorScreen.kt`, один на экран, не на блок):
  - работает только при `dragging`; каждый кадр (`withFrameNanos`): берёт окно позиции активной границы (`localToWindow` + `getCursorRect` из активного редактора через `NoteEditorController.activeEdgeRectInWindow: () -> Rect?`, регистрируется редактором, как уже делают `formats.bold` и др.), считает `velocity` по `VisibleViewport` из шага 7, при `velocity != 0` вызывает `scroll.scrollBy(delta)` и просит редактор сместить активную границу: `formats.extendActiveEdge(dyPx)` → в `RichNoteEditor`/`TableCellEditor` обновляет `field.selection`, сохраняя якорь.
  - Для таблицы: вертикальный автоскролл основного `scroll`, горизонтальный `tableScroll` не трогать.
  - Останов: `dragging == false`, или `velocity == 0` дольше 200 мс, или свёрнуто выделение, или экран ушёл с `ON_STOP`.
- Во время автоскролла меню скрыто (шаг 6, `SELECTION_CHANGING`/`HANDLE_DRAG`), лупу (`Magnifier`) не трогаем — она пойдёт вместе с обновлением выделения.
- Сопутствующее: проверять, что `BringIntoViewRequester` из `scheduleReveal` не конфликтует с автоскроллом (при `dragging` реагировать на `scheduleReveal` не нужно — добавить условие `if (state.dragging) return`).
- **Риск.** Compose не даёт публичного API «позиция пальца при тяге ручки». Если автоскролл по `field.selection` окажется нестабильным, запасной вариант (P3, отдельным коммитом после согласования с владельцем): свои ручки выделения (два маленьких `Popup`/слоя с `detectDragGestures`) вместо стандартных. Без согласования не делать.

**Файлы:** новый `ui/editor/SelectionAutoScroll.kt`; `SelectionMenuController.kt`; `EditorFormatBar.kt` (`NoteEditorController`: `activeEdgeRectInWindow`, `extendActiveEdge`); `EditorScreen.kt`; `RichEditor.kt`; `TableEditor.kt`.
**Тесты:** `SelectionAutoScrollTest.kt` — `autoScrollVelocity` (0 вне зон, знак, рост от `heldMillis`, зажим по максимуму, палец ниже viewport), `extendSelectionOffset` на фиктивных входах там, где возможно без `TextLayoutResult` (вынести арифметику в чистую функцию).
**Готово, когда:** на устройстве потянуть нижнюю ручку под клавиатуру — экран плавно скроллится вниз, выделение растёт по строкам, скорость нарастает при удержании; лупа не «залипает».

---

## 9. Пункт 7. Тап в нижней части заметки: фокус каретки не должен уезжать под клавиатуру

**Причина.** `RichNoteEditor.scheduleReveal` запускается только при изменении текста (`pendingReveal`). Тап по тексту у нижнего края открывает клавиатуру, область скролла уменьшается, каретка остаётся под клавиатурой, пока пользователь не проскроллит руками. `bringIntoView` при тапе не вызывается; после фокуса `reveal` отработает до того, как пройдёт анимация IME и пересчитается высота viewport.

**Что сделать.**
- В `EditorScreen.kt` добавить один экранный эффект «держать каретку в видимой области»:
  - `LaunchedEffect(Unit) { snapshotFlow { Triple(WindowInsets.ime.getBottom(density), formats.bodyFocused || formats.editingCell, formats.caretToken) }.distinctUntilChanged().collectLatest { ... } }`; внутри: `delay` до стабильности (пока `ime.getBottom` не перестанет меняться ~2 кадра, максимум 400 мс), затем:
  - получить `rect = formats.caretRectInWindow()` (новое поле в `NoteEditorController`, регистрируется активным редактором: тело заметки — `layout.getCursorRect(selection.end)` + `textCoordinates.localToWindow`; ячейка — аналогично; `null`, если ничего не в фокусе);
  - `delta = revealCaretDelta(scrollValue = 0, viewport = (viewportBottom - viewportTop), caretBottom = (rect.bottom - viewportTop).toInt(), marginPx = 48.dp)`; при `delta > 0` вызвать `scroll.animateScrollBy(delta.toFloat(), tween(200))`. Для каретки выше верхней границы видимой области (`rect.top < viewportTop`) прокрутить вверх на разницу.
  - `VisibleViewport` берётся из шага 7.
- `NoteEditorController.caretToken` увеличивать (`formats.caretToken++`) в `RichNoteEditor`/`TableCellEditor` **при** `onFocusChanged(isFocused = true)` и при тапе, переставившем каретку (`onTapUp` из шага 1 с задержкой `withFrameNanos` на кадр). Не увеличивать на каждую клавишу (оставлен существующий `scheduleReveal` для набора).
- `surface.onEmptyTap` → `focusLastTextBlockAtEnd()`: после установки `caretRequest` также увеличить `formats.caretToken`, чтобы пустой тап внизу страницы тоже докручивал каретку.
- Учесть высоту `EditorFormatBar` (она лежит под областью скролла в `Column`, поэтому уже вне viewport) и `MiniPlayer`.
- Добавить `AppLog.i("EditorFocus", "reveal caret delta=$delta")` (только число).
- `revealCaretDelta` расширить симметричным `revealCaretUpDelta` (каретка выше окна), покрыть в `EditorCaretScrollTest.kt`.

**Файлы:** `EditorScreen.kt`, `EditorFormatBar.kt`, `RichEditor.kt`, `TableEditor.kt`, `EditorCaretScrollTest.kt`.
**Готово, когда:** нажатие на последние строки заметки открывает клавиатуру, и экран сам прокручивается так, что строка с кареткой видна над клавиатурой/панелью форматирования, без ручного скролла; то же для ячеек таблицы внизу.

---

## 10. Пункт 8. Таблицы: «Вставить» в меню ячейки и порядок системной кнопки «Назад»

### 10.1 «Вставить» в меню ячейки (вместо плавающего окна)

- В `TableCellEditor` **удалить** показ `PasteActionPopup` и обработчики `toolbar.onPasteMenu`/`onPasteMenuHidden`/`pasteAnchor` (после шага 4 они уже не от Compose; здесь убрать и жестовый источник: для ячеек долгое нажатие на пустое место открывает меню ячейки, см. ниже).
- В `TableBlock` завести `val cellPaste = remember { HashMap<Pair<Int, Int>, () -> Unit>() }` (не state). `TableCellEditor` получает `registerPaste: (() -> Unit)?`/`onRegisterPaste: ((() -> Unit)?) -> Unit` и в `DisposableEffect(row, col)` регистрирует `pasteIntoCell`, снимает при `onDispose`.
- `pasteIntoCell` не менять по логике (учёт HIDDEN и `onRequestUnlock`), но если ячейка не в фокусе — перед вставкой запросить фокус, каретку ставить в конец текста ячейки (`selection = TextRange(text.length)`) либо заменить выделение, если оно есть.
- В `menuLines(...)` (`TableEditor.kt`) для `TableMenu.Cell` первой строкой добавить `TableMenuLine("Вставить", Icons.Outlined.ContentPaste, enabled = clipboard.hasText(), onClick = { cellPaste[row to col]?.invoke(); menu = null })`. Для `TableMenu.Row`/`TableMenu.Column` «Вставить» **не** добавлять. Строку показывать только при непустом буфере (не рисовать неактивную).
- Для ранжированного выделения внутри ячейки «Вставить» уже перенесён в расширенное меню (шаг 5).
- Проверка: долгое нажатие по пустому месту ячейки (`cellLongPressOpensCellMenu(onGlyph)` уже существует) открывает меню ячейки; плавающее окно «Вставить» в таблице больше нигде не показывается.

### 10.2 Порядок системной «Назад»: сначала меню ячейки/строки/столбца, потом клавиатура

**Причина (проверить на устройстве).** `BackHandler(enabled = menu != null) { menu = null }` в `TableBlock` и `dismissOnBackPress = true` у `TableActionsPopup` ждут кнопку «Назад», но когда клавиатура открыта, первая «Назад» уходит в IME и скрывает её, прежде чем приложение получит событие. Дополнительно `shouldClearFocusAfterImeHide` в `EditorScreen.kt` снимает фокус при скрытии IME.

**Что сделать (в порядке попыток).**
1. Проверить, включён ли `android:enableOnBackInvokedCallback="true"` в `<application>`/`<activity>` манифеста; если нет — включить (Android 13+ тогда доставляет «Назад» в `OnBackInvokedCallback` приложения, включая случай открытой IME; проверить на API 33+ и на более старых).
2. Вынести флаг `formats.tableMenuOpen` (snapshot state в `NoteEditorController`), выставлять из `TableBlock` (`menu != null`). `BackHandler(enabled = menu != null)` оставить в `TableBlock`, убедиться, что он зарегистрирован **после** `BackHandler` редактора (последний включённый обработчик побеждает) и **до** `BackHandler` экрана в `NotesRoot.kt` (`ui.screen == Screen.EDITOR`).
3. Если на устройстве IME всё равно получает «Назад» первой (старые API, Tecno/HiOS): запасной вариант — `shouldClearFocusAfterImeHide` дополнить параметром `tableMenuOpen`; при `wasShown && !visible && tableMenuOpen` **не** снимать фокус, закрыть меню (`menu = null`), и вернуть клавиатуру (`keyboard?.show()` на активной ячейке; для этого `NoteEditorController` получает `restoreImeForCell: () -> Unit`). Так пользователь воспринимает первое «Назад» как закрытие меню, второе скрывает клавиатуру.
4. Диалоги (PIN/ссылка/размер таблицы) не затрагивать: при `formats.dialogOpen` логика прежняя.
- Обновить `shouldClearFocusAfterImeHide` и тест (`EditorPolicyTest.kt`) под новый параметр.

**Файлы:** `TableEditor.kt`, `TableMenus.kt` (иконка/строка), `EditorFormatBar.kt`, `EditorScreen.kt`, `AndroidManifest.xml`, `EditorPolicyTest.kt`.
**Готово, когда:** в таблице нет плавающего «Вставить»; в меню ячейки первая строка «Вставить» (при непустом буфере) и она вставляет в эту ячейку; при открытом меню ячейки/строки/столбца первое «Назад» закрывает только меню, второе закрывает клавиатуру, третье — выходит из заметки (как раньше).

---

## 11. Финал

1. `./gradlew testDebugUnitTest assembleDebug assembleOptimized` — всё зелёное.
2. Ручная проверка на устройстве (Tecno/HiOS, светлая и тёмная тема), чек-лист:
   - [ ] Один тап — одна каретка (с задержкой ≤ `doubleTapTimeoutMillis`), двойной — только выделение слова, без третьего курсора. Тело заметки и ячейка.
   - [ ] Тап по тексту после показа «Вставить» не открывает «Вставить» снова. Набор текста не открывает.
   - [ ] «Вставить» появляется только по долгому нажатию на пустое место/между слов.
   - [ ] Короткое меню: «Вырезать/Копировать/⋮». Расширенное: «Вставить» первым.
   - [ ] Меню выделения всегда над выделением; у верха экрана — под ним; не заходит на шапку; при прокрутке плавно скрывается и возвращается над выделением; если выделение вне экрана — закреплено у шапки/у нижней границы.
   - [ ] После двойного тапа и растягивания ручек меню плавно исчезает и возвращается.
   - [ ] Ручка вниз под клавиатуру — автоскролл с нарастанием скорости, лупа не залипает.
   - [ ] Тап по нижним строкам — каретка автоматически видна над клавиатурой.
   - [ ] Таблица: «Вставить» в меню ячейки; «Назад» закрывает меню, потом клавиатуру.
   - [ ] Долгое нажатие на слово — выделение + один короткий тик.
   - [ ] Скрытый текст (HIDDEN): нет выделения/вибрации/вставки без PIN, как раньше.
   - [ ] Нет лишних перекомпозиций при скролле (Layout Inspector / compose-stability: `compose_stability.conf`), нет бесконечных анимаций в idle.
3. Обновить `docs/STACK.md` (раздел «UI»: меню выделения, «Вставить», автоскролл, подавление меню при тяге/скролле), при необходимости `docs/PRODUCT_SPEC.md`, `README.md`.
4. Перед `git push`: `git log -n 5 --format=%B`, снять `Co-authored-by: Cursor <cursoragent@cursor.com>`.
