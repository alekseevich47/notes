# Telegram-Android → карта ввода, курсора, выделения, таблиц

Источник: [TelegramMessenger/Telegram-Android](https://github.com/TelegramMessenger/Telegram-Android)  
Ревизия: `21f7d10` (v13.2.2 / 7303). Номера строк указаны для этой ревизии, при обновлении ориентироваться на **имена символов**.  
Корень путей: `TMessagesProj/src/main/java/org/telegram/` (далее `…/`).

> Версия 2. Первая версия карты (предыдущий агент) пропустила **Rich-редактор** (`…/ui/iv/`), поэтому утверждения
> «редактируемых таблиц нет» и «выделение/double-tap в input только системные» были неверны. См. раздел «Проверка».

## Три контура Telegram

| Контур | Где | Что это | Аналог у нас |
|--|--|--|--|
| **A. Поле ввода чата** | `…/ui/Components/EditTextBoldCursor` → `EditTextEffects` → `EditTextCaption` → `ChatActivityEnterView.ChatActivityEditTextCaption` | Системный `EditText`, свой только курсор/ручки/floating-toolbar/форматирование. Жесты и выделение системные | `RichNoteEditor` (базовое поле) |
| **B. Rich-редактор (long-form, блоки, таблицы)** | `…/ui/iv/*` (`RichEditor`, `RichEditorListView`, `RichTextCell`, `RichEditText`, `RichTableCell…`) | **Самый близкий к нашему редактору.** Список блоков (RecyclerView), в каждом блоке свой `RichEditText`; **свой движок жестов и выделения поверх EditText**; редактируемые таблицы | `RichEditor`, `EditorGestures`, `TableEditor`, `TableMenus`, `SelectionMenu` |
| **C. Выделение в чужом тексте** | `…/ui/Cells/TextSelectionHelper.java` | Движок выделения (ручки, лупа, меню, автоскролл). Используется контуром B как база (`ArticleTextSelectionHelper`), а также в чате/Instant View | `SelectionSurface`, `SelectionAutoScroll`, `SelectionMenuController` |

Ключевая идея контура B: **одиночный тап → системный EditText ставит каретку; double-tap и long-press по тексту → перехват и переход на свой `TextSelectionHelper`, EditText-ы блокируются (`setEditTextsLocked(true)`)**; если система сама создала выделение в EditText, `RichTextCell` «угоняет» его в helper (`hijackingSelection`).

---

## 1. Курсор: клики, double-tap, long-press

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Отрисовка каретки, цвет, ширина | `…/ui/Components/EditTextBoldCursor.java` | `drawCursor`, `setCursorColor/Size/Width`, `setAllowDrawCursor`, `setForceCursorEnd`; `Choreographer60FpsContent` (~96, 1191) | Свой drawable каретки, мигание на 60fps через Choreographer. Rich: `setCursorWidth(1.5f)` (`RichEditText` ~140) | цвет/мигание каретки Compose |
| Цвет ручек каретки/выделения | `EditTextBoldCursor` | `setHandlesColor`, `getTextSelectHandle*`; `ChatActivityEnterView.createMessageEditText` → `fixHandlesColor()` (~5809) | Tint системных handle (API ≥ Q) | handles Compose |
| Одиночный тап в поле | `EditTextBoldCursor.onTouchEvent` (788) | Только `lastTouchX` и `super` | Каретку ставит система | `EditorGestures` (tap) |
| Тап → запрос клавиатуры | `…/ui/Components/ChatActivityEnterView.java` | anon `onTouchEvent` в `createMessageEditText` (~5770): DOWN → `delegate.onKeyboardRequested()` | IME запрашивается на DOWN | показ IME |
| Тап → запрос focusable окна | `…/ui/iv/RichEditText.java` | `onTouchEvent` (~500): DOWN → `listener.onRequestWindowFocusable` | Rich делегирует host-у | — |
| Тап по math/inline-кнопке | `RichEditText.onTouchEvent` | `inlineButtonSpanAt`, `mathSpanAt`, long-press runnable | Тап < long-press timeout и в slop → открыть редактор; long-press на кнопке → её редактор | ссылки/спойлеры |
| **Double-tap** (Rich) | `…/ui/iv/RichEditorListView.java` | `handleSelectionTouch` (2240), блок `doubleTap` (~2251) | `ViewConfiguration.getDoubleTapTimeout()` + смещение ≤ 24dp, не в режиме выделения → `tryStartTextSelection` (выделяет слово), `finishOneTouchSelection()` | `EditorGestures.onDoubleTap`, `TapClassifier` |
| **Long-press** (Rich) | `RichEditorListView.handleSelectionTouch` | `longPressRunnable` (~2260–2300), `postDelayed(.., getLongPressTimeout())` | По тексту → выделение слова; по таблице → ручки строк/столбцов или drag-выделение ячеек (+haptic LONG_PRESS); по пустому EditText → ничего (пусть система покажет «Вставить»); иначе → `startBlockDrag` (перетаскивание блока) | `EditorGestures.onLongPress` |
| Отмена long-press | там же | ACTION_MOVE: смещение > 8dp → `removeCallbacks(longPressRunnable)` | slop 8dp | slop в `EditorGestures` |
| Старт выделения по тапу | `RichEditorListView.tryStartTextSelection` (2398) | `isPressOnText`, `setMaybeView`, `trySelect` для `RichTextCell/RichTableCell/RichCaptionHost/RichDetailsCell/RichMathCell` | Выделение стартует только если палец на тексте | hit-test текста |
| Long-press в сообщениях (не в Rich) | `…/ui/Cells/TextSelectionHelper.java` | `startSelectionRunnable` (194), `longpressDelay` (315), haptic `LONG_PRESS` (282) | Слово: `isInterruptedCharacter` (822) = `isLetter\|isDigit\|'_'` | `WordBounds` |
| Тап вне выделения | `TextSelectionHelper.TextSelectionOverlay.checkOnTap` (899) | < 200 мс и сдвиг < touchSlop → `onTapToDismiss` + `hideActions` + `clear` | Сброс выделения; Rich после сброса возвращает фокус в точку тапа (`restoreFocusAtScreenPoint`) | `SelectionTap` |
| Роутинг касаний | `…/ui/iv/RichEditor.java` | `dispatchTouchEvent` контейнера (~370–392) | Порядок: overlay ручек → `checkOnTap` → `handleSelectionTouch` → `super` | порядок в `SelectionSurface` |

## 2. Выделение текста, каретки (ручки)

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Режим выделения | `TextSelectionHelper` | `selectionStart/End` (103), `isInSelectionMode` (513) | Состояние = offsets в «ячейке» | `TextFieldValue.selection` |
| Перехват системного выделения | `…/ui/iv/RichTextCell.java` | `editText` listener `onSelectionChanged` (~310), `hijackingSelection` | Как только EditText получил непустой range → `helper.selectRangeOf(...)`, EditText оставляется с кареткой в конце | синхронизация selection |
| Блокировка EditText при выделении | `RichEditorListView.setEditTextsLocked` (2625) + callback `onStateChanged` (~224–265) | при старте: сохранить `restoreFocus*`, lock, `hideEditTextActionModes`; при конце: unlock, `restoreFocusAt` | Каретка возвращается в точку якоря/тапа | восстановление каретки |
| Ручки: drag | `TextSelectionHelper.TextSelectionOverlay.onTouchEvent` (~925–1230) | `movingHandle`, `movingHandleStart`, snap по `isInterruptedCharacter` | Ручка «прилипает» к границам слов; start/end меняются местами при пересечении | `SelectionSurface` ручки |
| Лупа (magnifier) | `TextSelectionHelper.showMagnifier/hideMagnifier` (409–460) | `Magnifier` (API ≥ P), не в one-touch | Показывается только при перетаскивании ручки | опционально |
| One-touch (long-press + тянуть) | `TextSelectionHelper` | `isOneTouch` (299, 814), `finishOneTouchSelection` (810) | После отпускания пальца — показать меню | long-press+drag |
| Выделить всё: эскалация | `RichEditorListView` | `expandSelectionToWholeCurrentBlock` → `tryEscalateSelectAll` (7049), `isWholeDocumentSelected` (7032), `forceShowSelectAll` | 1-й раз: весь блок; 2-й: весь документ | «Выделить всё» |
| Выделение через блоки | `TextSelectionHelper.ArticleTextSelectionHelper` (2908) | `selectRangeOf` (3269), `selectAllBlocksRange` (3440), `multiselect` | Мультиблочное выделение в RecyclerView | — |
| Подсветка выделения | `TextSelectionHelper` | `drawSelection`, `CornerPathEffect` | Скруглённый path | подсветка |
| Кнопки Ctrl+C/X/V/A, Del | `RichEditorListView` | `dispatchKeyEvent` (~4186–4222), `copyHelperSelection` (7157), `cutHelperSelection` (7163), `pasteAtHelperSelection` (7204), `deleteHelperSelection` (7393) | Операции над мультиблочным выделением | hardware keys |

## 3. Таблицы, форматирование, ввод, удаление

### 3.1 Таблицы (редактируемые — в Rich-редакторе)

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| **Создание** | `…/ui/iv/RichTextCell.java` | `newEmptyTable(rows, cols)` (1194); `/table [RxC]` (~1141, лимит 1..20) | Таблица `bordered=true`, пустые ячейки; после неё вставляется пустой параграф | вставка таблицы |
| Создание из тулбара | `…/ui/iv/RichEditor.java` (777), `ChatAttachAlertRichLayout.java` (311, `BLOCK_TABLE`) | `listView.addBlock(newEmptyTable(2,2))` | 2×2 по умолчанию | кнопка «Таблица» |
| Создание при трансформации блока | `RichEditorListView.transformRow` (5728, ветка `pageBlockTable` ~5740) | добавляет параграф после | — | — |
| Модель | `…/ui/iv/TableModel.java` | `addRow/addColumn`, `insertRowAt` (222), `insertColumnAt` (249), `mergeCells` (276), `unmergeCell` (329), `deleteRows` (368), `deleteColumns` (405), `setHeader/setAlign/setVAlign`, `normalizeForSend` (465) | Сетка с rowspan/colspan, anchor-ячейки | `TableEditor` модель |
| Лимит колонок | `…/ui/iv/RichEditorLimits.java`; `MessagesController.config.richMessageMaxTableCols` | `LIMIT_TABLE_COLS` | Insert-колонки отключается на лимите | лимит столбцов |
| Блок-вью таблицы | `…/ui/iv/RichTableCell.java` | `bind`, title (`bindTitle` 216), `findCellAt` (416), `findRowHandleAt` (440), `findColHandleAt` (445), `selectCellRectangle` (347), `selectWholeRows/Columns` (478/491) | Таблица = заголовок + сетка + ручки-точки у строк/столбцов | `TableEditor` UI |
| Сетка | `…/ui/iv/RichTableCellGrid.java` | `onMeasure` (163), `dispatchDraw` (324), `drawHandleDots` (569), `drawSelectionOutline` (713), `rowHandleAtGrid` (613), `colHandleAtGrid` (634) | Рисует границы, выделение ячеек с округлыми углами, «bulge»-ручки | отрисовка |
| Ячейка = свой EditText | `…/ui/iv/RichTableCellHost.java` | `bind`, `applyHeaderWithDefaultBold`, `setLocked` | Каждая ячейка — `RichEditText` | `TableCellEditor` |
| Tab / Shift+Tab | `RichTableCell.wireCellListeners` (881), `moveFocusByTab` (954); `RichEditText.dispatchKeyEvent` (~640) | `onTab` | Переход между ячейками | Tab |
| Выделение ячеек: long-press+drag | `RichEditorListView` | `startCellSelectionDrag` (3067), `updateCellSelectionDrag` (3078), `finishCellSelectionDrag` (3089) → меню | haptic, `requestDisallowInterceptTouchEvent` | мультивыбор ячеек |
| Выделение строки/столбца | `RichEditorListView.handleTableHandleTap` (3103) | тап по точке-ручке; повторный тап по уже выбранному → меню | `dotSelectedRow/Col` | ручки строк/столбцов |
| Вход/выход из режима ячеек | `beginCellSelection` (3044), `enterCellSelectionMode` (3062), `exitCellSelectionMode` (3131) | Блокирует EditText-ы, чистит text-selection | — |
| **Меню таблицы** | `RichEditorListView.showTableCellMenu` (3187) → `ItemOptions` | Выравнивание H/V (6 кнопок), «Выделить цветом» (header), Merge/Split, Insert left/right/above/below, Delete column/row/**table**; переключатели Bordered и Compact (`m.block.bordered/compact`) | Пункты зависят от выделения (`computeCanMerge` 3392, `computeSpansFullRows/Columns` 3410/3424, `computeAllSelected` 3438) | `TableMenus` |
| Операции меню | `RichTableCell.applyMergeFromSelection` (673), `applyUnmergeFromSelection` (706), `applyDeleteRowsFromSelection` (724), `applyDeleteColumnsFromSelection` (740), `applyInsertRowFromSelection` (756), `applyInsertColumnFromSelection` (778), `applyHeaderToggle` (580), `applyHorizontalAlign` (602), `applyVerticalAlign` (612), `applyBordered/applyCompact` (622/629), `applyDeleteToSelectedCells` (658) | — | операции |
| **Удаление таблицы** | `RichEditorListView.showTableCellMenu` (~3249): пункт `ArticleDeleteTable`; также как блок через `onCellBackspaceAtStart` (5211) / `deleteHelperSelection` (7393) | Есть явный пункт меню «Удалить таблицу» (красный) | пункт меню + удаление блока |
| Форматирование в ячейках | `RichEditorListView.onFormattingClickedTable` (1812), `isStyleFullyAppliedTable` (1855), `onLinkClickedTable` (1873), `persistTableCell` (1781) | Те же стили, применяются по ячейкам выделения | форматирование в таблице |
| Undo/redo | `…/ui/iv/RichEditorHistory.java` | `DEBOUNCE_MS=800`, `MAX_DEPTH=150`, `FocusState(rowId, childIndex, selStart, selEnd)` | Снимки блоков + фокус ячейки | история |

### 3.2 Форматирование и ввод

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Стили (Rich) | `…/ui/iv/RichTextStyle.java` | `setStyle`, `stylesFullyCovering`, `SUPPORTED`, MONO/SUB/SUPER взаимоисключения (`clearMaskFor` в `RichEditorListView` ~925) | toggle: если стиль полностью покрывает — снять, иначе добавить | `EditorFormatBar` |
| Применение к выделению | `RichEditorListView.onFormattingClicked` (~932) | Идёт по блокам `sCell..eCell`, `history.flush/record` | Мультиблочное форматирование | форматирование выделения |
| Панель форматирования | `…/ui/iv/RichEditorToolbar.java` | `showFormattingPanel` (546), `setFormattingState` (516), `setSelectedBlockType` (502), `BLOCK_TABLE=4` (43), `setHistoryEnabled` (495) | Панель над клавиатурой, меняется между «блоки» и «форматирование» | `EditorFormatBar` |
| Стили (чат) | `…/ui/Components/EditTextCaption.java` | `makeSelectedBold/Italic/Mono/Strike/Underline/Spoiler` (154–188), `toggleStyleForSelection` (195), `applyTextStyleToSelection` (660), `makeSelectedQuote` (236), `makeSelectedUrl` (320) | Через `TextStyleSpan.TextStyleRun` + `MediaDataController.addStyleToText` (6933) | — |
| Флаги стилей | `…/ui/Components/TextStyleSpan.java` | `FLAG_STYLE_*` (106+) | Bold=1<<0 … | модель спанов |
| Шорткаты | `ChatActivityEnterView.ChatActivityEditTextCaption.handleFormattingShortcut` (5513) | Ctrl+B/I/U/E (mono)/K (ссылка); **Ctrl+Shift+X или S** — strike, **+M** mono, **+P** spoiler | — | hardware keys |
| Markdown-триггеры | `RichTextCell.matchMarkdownTrigger` (1040), `matchEnterTrigger` (1109) | `# `…`###### ` (заголовки), `- `/`* `/`+ ` (список), `\| ` (цитата), `/table`, `/quote`, … | Превращение блока по набранному префиксу | `MarkdownShortcuts`, `ListAutoFormat` |
| Slash-команды | `RichCommand.java`, `RichCommandSuggestions.java`, `RichTextCell.matchCommand` (1181) | `/img /vid /audio /map /latex /toggle /button` | Подсказки при вводе `/` | — |
| Enter / newline | `RichEditText.dispatchKeyEvent` (~640), `insertNewlineAtSelection` (~690), `RichEditorListView.onCellEnter` (5069) | Enter → новый блок; Shift/soft-enter → `\n` в блоке | — | Enter |
| Backspace | `RichEditText.onKeyDown` (~660): `onBackspaceOnEmpty`, `onBackspaceAtStart`; `RichEditorListView.onCellBackspaceAtStart` (5211) | Backspace в начале блока → слияние/деградация блока | Backspace |
| Не рвать удержание Backspace | `RichEditText.setInputType` (~300–325) | `DELETE_HOLD_MS=500`, `lastDeleteTime`, `pendingInputType` | `setInputType` (рестарт IME) откладывается, пока идёт удаление — иначе удержанный backspace обрывается | **важно** для `inputType`/IME-рестартов |
| Paste | `RichEditText.onTextContextMenuItem` (~690) → `listener.onPaste` → `RichEditorListView.onCellPaste` (7246); `ChatActivityEnterView.onTextContextMenuItem` (~5762) → `handleRichHtmlPaste`; `EditTextCaption` paste → `CopyUtilities.fromHTML` | HTML → блоки/спаны; курсор после вставки в конец | `RichClip` |
| Разметка «Marked» | `RichEditText.buildMarkPath/onDraw` (~205–265) | Подсветка по `FLAG_STYLE_MARKED` через `LinkPath` | — |
| Авто-bold заголовка | `RichEditText.setAutoBold`, `RichTableCell.initializeTitleAutoBold` (231) | — | — |

## 4. Всплывающие окна (меню выделения, «Вставить»)

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Меню выделения в Rich/сообщениях | `TextSelectionHelper.showActions` (560) | API ≥ M: `FloatingToolbar` + `FloatingActionMode`; иначе / `showActionsAsPopupAlways` — `ActionBarPopupWindow` (`SelectionPopupLayout` 524) | Copy; **в Rich ещё Cut/Paste/Select all** (`canCut/canPaste/forceShowSelectAll` в `RichEditorListView` 170–185) | `SelectionMenu` |
| Когда показывать | `showActionsRunnable` (555) | Только если `!movingHandle && isInSelectionMode && canShowActions` | Меню скрыто пока тянут ручку | `SelectionMenuController` |
| Меню у системного EditText | `EditTextBoldCursor.startActionMode` (1233) | Создаёт `FloatingToolbar`+`FloatingActionMode`, `extendActionMode`, PreDraw → `updateViewLocationInWindow` | Свой toolbar вместо системного | — |
| Rich: меню отключено у EditText | `RichEditText` ctor (~143–170) | `setCustomSelectionActionModeCallback(noop)`; **insertion callback**: при пустом тексте оставляет **только Paste / Paste as plain** | Тап по каретке в пустом поле → только «Вставить»; выделение отдают helper-у | `PasteActionPopup` |
| Long-click только для пустого | `RichEditText.updateLongClickForEmpty` (~195) | `setLongClickable(length()==0)` | Long-press в непустом тексте не открывает системное меню | — |
| Меню таблицы | `RichEditorListView.showTableCellMenu` (3187); `tableMenuAnchor` (3168) | Якорь = верх/низ выделенной ячейки; `dismissTableCellMenu` (3148) | Для тапа по ручке и по завершении drag | `TableMenus` |
| Меню чата (формат) | `ChatActivity.extendActionMode/fillActionModeMenu` | Quote/Spoiler/Bold… в ActionMode | — | — |
| Ссылка (диалог) | `EditTextCaption.showInputDialog` (381) | С кнопкой «Вставить» из буфера | — | — |

## 5. Автоскролл

| Функционал | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Автоскролл при тяге ручки | `TextSelectionHelper.scrollRunnable` (159); триггер в `TextSelectionOverlay.onTouchEvent` (~1030–1048) | `canScrollDown` = палец ниже `parentView.height - parentBottomPadding`; `canScrollUp` = выше `parentTopPadding`. Шаг: `getLineHeight()>>1` (для multiselect `dp(8)`); `parentRecyclerView.scrollBy` каждый кадр | `SelectionAutoScroll` |
| Паддинги для скролла | `RichEditorListView.getParentTopPadding/BottomPadding` (~190) | = padding RecyclerView (под тулбар и IME) | отступы под IME/панель |
| Каретка над клавиатурой (Rich) | `RichEditorListView.onLayout` (~280–330) | Если высота списка **уменьшилась** (открылась IME): `overflow = child.bottom + 8dp - (h - paddingBottom)`; `post(scrollBy(0, overflow))` | `revealCaretDelta` |
| Закрыть клавиатуру, когда поле ушло | `RichEditorListView.hideKeyboardIfFocusScrolledAway` (~290) | `child.bottom <= 0 \|\| child.top >= height` → `clearFocus` + hideKeyboard | — |
| Каретка в чате | `ChatActivityEnterView.ChatActivityEditTextCaption.requestRectangleOnScreen` (5566) | `rectangle.bottom += dp(1000)` | Заставляет родителя высоко поднять поле над IME |
| Рост поля | `ChatActivityEnterView.createMessageEditText` (`setMaxLines(6)` ~5868), hook `onLineCountChanged` (4713, пустой), `lineCount` listener (~5985) | **Не** в `EditTextCaption`. Панель растёт вверх до 6 строк | высота поля |
| Клавиатура | `…/ui/Components/SizeNotifierFrameLayout.java`; `RichEditor` (`container`, `sizeDelegate`, `keyboardHeight`, `setInsets` в списке) | Высота IME → `imeInset`/`emojiPadding` → padding списка | `windowInsets` IME |
| Автоскролл после удаления | Отдельной логики **нет**: RecyclerView/EditText сами; фокус и каретка восстанавливаются (`restoreFocusAt` 2682, history `FocusState`) | — | проверять `EditorCaretScrollTest` |

## 6. Поведение при скролле

| Что | Файл | Символ / строки | Поведение | У нас |
|--|--|--|--|--|
| Выделение + скролл списка | `RichEditorListView` OnScrollListener (~265–279) | `onScrolled` → `textSelectionHelper.onParentScrolled()`; IDLE → `stopScrolling()` | Скролл: ручки перерисовываются, **меню прячется** (`hideActions`); IDLE: `showActionsRunnable` — меню возвращается | fade меню |
| Реализация | `TextSelectionHelper.onParentScrolled` (795), `stopScrolling` (803) | `parentIsScrolling`, `hideActions` | то же | — |
| Floating-меню EditText | `FloatingActionMode.updateViewLocationInWindow` (151) | `setMoving(true)` + `MOVING_HIDE_DELAY=50` мс; вне границ → `setOutOfBounds(true)` → скрыть | меню следует за якорем, прячется при движении | `SelectionBubble` |
| Таблица: гориз. скролл | `ArticleViewer.BlockTableCell` (9600) `HorizontalScrollView.onScrollChanged` → `textSelectionHelper.invalidate()` | (Только чтение, не Rich) | — |
| EditText hor. scroll | `EditTextBoldCursor.onScrollChanged` (628) | `requestDisallowInterceptTouchEvent(true)` | — |
| Tap/ручки в таблице при скролле | `RichEditorListView.startCellSelectionDrag` | `stopScroll()` + `requestDisallowInterceptTouchEvent(true)` на время drag | запрет скролла на время drag |
| «Вставить» у каретки при скролле | Отдельной логики нет: системный insertion-ActionMode (`FloatingActionMode` в `EditTextBoldCursor`) скрывается по `setMoving` | — | `PasteActionPopup` |

---

## Проверка ответа предыдущего агента

**Верно** (проверено по коду): иерархия `EditTextBoldCursor → EditTextEffects → EditTextCaption`; `EditTextBoldCursor.onTouchEvent` лишь пишет `lastTouchX`; `requestRectangleOnScreen` с `bottom += dp(1000)` (но в `ChatActivityEnterView.ChatActivityEditTextCaption`); `FloatingToolbar`/`FloatingActionMode` с `MOVING_HIDE_DELAY`; `TextSelectionHelper.scrollRunnable` (шаг `lineHeight/2`); `onParentScrolled` / `stopScrolling`; `isInterruptedCharacter = letter|digit|_`; `checkOnTap` (<200 мс, < slop); формат-методы `EditTextCaption.makeSelected*`.

**Неверно / неполно:**

1. **«Редактируемых таблиц в композере нет, CRUD — наша зона»** — неверно в общем виде. В Telegram 13.x есть полноценный редактируемый Rich-редактор (`…/ui/iv/`): создание (`/table RxC`, кнопка), правка ячеек (каждая — `RichEditText`), merge/split, вставка/удаление строк и столбцов, выравнивание, выделение ячеек drag-ом и ручками. Верно только то, что **в обычном поле чата** таблиц нет.
2. **«Double-tap / long-press в input — только системные»** — верно лишь для поля чата. В Rich-редакторе `RichEditorListView.handleSelectionTouch` реализует **свой double-tap и long-press** (таймауты, slop 24dp/8dp, haptic).
3. **«Выделение в input системное»** — в Rich-редакторе `RichTextCell` перехватывает системное выделение и переносит в `TextSelectionHelper`, EditText блокируется; системное меню выключено (`noop` callback), у пустого поля — только Paste.
4. **`EditTextCaption.onLineCountChanged`** — нет такого метода; `onLineCountChanged` — пустой hook в `ChatActivityEnterView` (4713). Рост поля: `setMaxLines(6)` там же.
5. **Шорткаты:** `S`, `M`, `P`, `X` требуют **Shift** (Ctrl+Shift+…); `E` без Shift = mono.
6. **Пропущено:** `RichEditText.setInputType` + `DELETE_HOLD_MS` (защита удержания Backspace от рестарта IME), insertion-меню только с Paste, эскалация «Выделить всё» (блок → документ), восстановление фокуса после выхода из выделения, обработка IME-resize в `RichEditorListView.onLayout`, блокировка скролла на время drag ячеек, `RichEditorHistory`, markdown/slash-триггеры.
7. **Формулировка «Long-press empty → Paste (диалог URL)»** — смешивает два разных механизма: «Вставить» у каретки — системный insertion ActionMode (в Rich оставлены только Paste/Paste as plain); кнопка Paste в диалоге ссылки — `EditTextCaption.showInputDialog`.

## Приоритет переноса в Notes

1. **Hide-menu-on-scroll/drag, show after IDLE** — `TextSelectionHelper.onParentScrolled/stopScrolling` (+ `showActionsRunnable` только если `!movingHandle`).
2. **Double-tap/long-press → свой выбор слова, EditText блокируется** — `RichEditorListView.handleSelectionTouch`, `tryStartTextSelection`, `setEditTextsLocked`, `restoreFocusAt*`.
3. **Word bounds** `letter|digit|_`, snap ручек к словам, один haptic — `TextSelectionHelper`.
4. **Edge autoscroll при тяге ручки** — `scrollRunnable` (шаг `lineHeight/2`, учёт padding списка).
5. **Caret reveal при открытии IME** — `RichEditorListView.onLayout` (вычисление `overflow` + `scrollBy`).
6. **Таблицы:** модель (`TableModel`), ручки строк/столбцов (`RichTableCellGrid`), drag-выделение ячеек, единое меню (`showTableCellMenu`), Tab-навигация, лимит колонок.
7. **Пустое поле → только «Вставить»**; непустой long-press не открывает системное меню (`RichEditText` ctor).
8. **Не перезапускать IME во время удержания Backspace** (`setInputType` + `DELETE_HOLD_MS`).
9. **Эскалация Select All**: блок → документ.
10. **Undo/redo со снимком фокуса ячейки** — `RichEditorHistory.FocusState`.

Не копировать буквально View/reflection/`mEditor`: переносить поведение и разбиение ответственности.
