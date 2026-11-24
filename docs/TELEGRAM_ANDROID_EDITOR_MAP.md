# Telegram-Android → карта ввода, курсора, выделения

Источник: [TelegramMessenger/Telegram-Android](https://github.com/TelegramMessenger/Telegram-Android)  
Ревизия: `21f7d10` (v13.2.2 / 7303, 2026-11-12).  
Пути от корня репозитория Telegram. В коде ссылаться на **имена символов**, не на номера строк.

## Важно для нашего приложения (Compose)

Telegram — **View/EditText**, не Compose. Два разных контура:

| Контур | Назначение | Аналог у нас |
|--|--|--|
| **A. Поле ввода** | `EditTextBoldCursor` → `EditTextEffects` → `EditTextCaption` → `ChatActivityEnterView.messageEditText` | `RichNoteEditor`, `TableCellEditor` |
| **B. Выделение в чужих сообщениях / IV** | полностью своё: `TextSelectionHelper` + overlay + `FloatingToolbar` | `SelectionMenu`, ручки Compose, `SelectionAutoScroll` |

Таблицы в Telegram — **только отображение** Instant View / профильных key-value (`TableLayout` / `TableView` / `ArticleViewer.BlockTableCell`). **Редактируемых таблиц в композере нет** — CRUD строк/ячеек у нас своё (`TableEditor`).

Жесты клика / double-tap / long-press в поле ввода в основном оставляет **системный `EditText`**. Кастом — курсор, ручки, ActionMode/FloatingToolbar, форматирование, paste HTML, `requestRectangleOnScreen`.

---

## Иерархия поля ввода

```
EditText (Android)
  └─ EditTextEffects          # spoilers, quotes, offsetY
       └─ EditTextBoldCursor  # курсор, handles, FloatingActionMode
            └─ EditTextCaption # Bold/Italic/…, paste, ActionMode wrap
                 └─ ChatActivityEditTextCaption (inner в ChatActivityEnterView)
```

Создание: `ChatActivityEnterView.createMessageEditText()`.

---

## Сводная таблица

| # | Функционал | Файл | Символы / место | Поведение | Куда смотреть у нас |
|--|--|--|--|--|--|
| 1 | Базовый EditText + кастомный курсор | `TMessagesProj/.../Components/EditTextBoldCursor.java` | `drawCursor`, `updateCursorPosition`, `setCursorColor/Size/Width`, `setAllowDrawCursor`, `setForceCursorEnd`, `getTextCursorDrawable` | Курсор рисуется своим `GradientDrawable`/`ShapeDrawable`; системный blink на API≥Q гоняется через `Choreographer60FpsContent` | Цвет/мигание каретки Compose |
| 1 | Цвет ручек выделения | `EditTextBoldCursor` | `setHandlesColor`, `getTextSelectHandle{,Left,Right}` | API≥Q (не MIUI): `PorterDuffColorFilter` на system handles | Handles Compose |
| 1 | Touch в поле ввода | `EditTextBoldCursor.onTouchEvent` | пишет `lastTouchX`, дальше `super` | Одиночный тап / double-tap / long-press — **системные** | `EditorGestures` |
| 1 | Touch + клавиатура в чате | `ChatActivityEnterView.createMessageEditText` → anon `onTouchEvent` | `delegate.onKeyboardRequested()`, `fixHandlesColor()` | DOWN → запрос IME; bot-menu перехватывает «клик» | `EditorScreen` + IME |
| 1 | Long-press → слово (в **сообщениях**, не в input) | `Cells/TextSelectionHelper.java` | `onTouchEvent` + `startSelectionRunnable`, `isInterruptedCharacter` | После `ViewConfiguration.getLongPressTimeout()`; слово = letter/digit/`_`; emoji span целиком; haptic `LONG_PRESS` | `EditorGestures.onLongPress`, `SelectionHaptics`, `WordBounds` |
| 1 | Double-tap | системный EditText / Compose | — | В input Telegram не переопределяет double-tap | `TapClassifier` / `onDoubleTap` |
| 2 | Выделение в EditText | framework + `EditTextBoldCursor.setSelection` | try/catch вокруг `super.setSelection` | Старт ActionMode при selection | `TextFieldValue.selection` |
| 2 | Выделение в ленте | `TextSelectionHelper` | `selectionStart/End`, `TextSelectionOverlay`, `drawSelection`, `showHandleViews` | Свои ручки, path со скруглением `CornerPathEffect(6dp)`, magnifier API≥P | Selection handles + highlight |
| 2 | Тяга ручек | `TextSelectionOverlay.onTouchEvent` | `movingHandle`, `movingHandleStart`, snap по `isInterruptedCharacter` | Edge → `scrollRunnable` крутит parent Recycler/NestedScroll | `SelectionAutoScroll` |
| 3 | Формат: Bold/Italic/Mono/Strike/Underline/Spoiler | `EditTextCaption` | `makeSelectedBold/Italic/Mono/Strike/Underline/Spoiler`, `toggleStyleForSelection`, `applyTextStyleToSelection` | Toggle через `TextStyleSpan.TextStyleRun` + `MediaDataController.addStyleToText` | `EditorFormatBar`, spans документа |
| 3 | Quote / URL / Date / Translate | `EditTextCaption` | `makeSelectedQuote`, `makeSelectedUrl`, `makeSelectedDate`, `translateSelected` | Quote → `QuoteSpan.putQuoteToEditable`; URL → диалог + Paste | нет 1:1 (у нас HIDDEN/lists) |
| 3 | Флаги стилей | `Components/TextStyleSpan.java` | `FLAG_STYLE_*` | Bold=1<<0 … Marked=1<<16 | модель spans |
| 3 | Слияние/разрез spans | `messenger/MediaDataController.java` | `addStyleToText`, `getTextStyleRuns` | Пересечение/split CharacterStyle | логика apply/remove style |
| 3 | Меню формата в ActionMode | `ui/ChatActivity.java` | `extendActionMode`, `fillActionModeMenu` | Добавляет Quote, Spoiler, Bold… Regular к системному Copy/Cut/Paste | `SelectionActionsPopup` пункты |
| 3 | Шорткаты Ctrl+B/I/U/E… | `ChatActivityEnterView` / `ChatActivityEditTextCaption` | `handleFormattingShortcut` | Ctrl+B bold, I italic, U underline, E/M mono, S strike, P spoiler, K URL | опционально |
| 3 | Paste HTML с сущностями | `EditTextCaption.onTextContextMenuItem` | `android.R.id.paste` → `CopyUtilities.fromHTML` | Emoji + quotes normalize; selection → конец вставки | paste в `RichEditor` |
| 3 | Ввод / удаление текста | framework EditText + `EditTextEffects.onTextChanged` | spoilers/quotes invalidate | Удаление — системное; spoilers timeout | Compose `TextField` |
| 3 | Таблицы IV (read-only) | `Components/TableLayout.java`, `TableView.java`, `ArticleViewer.BlockTableCell` | `createTextLayout`, horizontal `ScrollView` | Long-press ячейки → `TextSelectionHelper.trySelect` | **не** CRUD; UI сетки можно подсмотреть |
| 3 | CRUD таблиц в чате | — | — | **Нет** | `TableEditor`, `TableMenus` |
| 4 | Floating toolbar (API≥23) | `ActionBar/FloatingToolbar.java`, `FloatingActionMode.java` | `show/hide/dismiss`, `repositionToolbar`, `FloatingToolbarVisibilityHelper` | Свой popup вместо system FloatingToolbar | `SelectionMenu` / `SelectionBubble` |
| 4 | Старт ActionMode у EditText | `EditTextBoldCursor.startActionMode` | создаёт `FloatingToolbar` + `FloatingActionMode`, `extendActionMode`, PreDraw → `updateViewLocationInWindow` | При движении: hide на `MOVING_HIDE_DELAY`, out-of-bounds → hide | `SelectionMenuController` fade |
| 4 | Wrap ActionMode у caption | `EditTextCaption.overrideCallback` | `copyPasteShowed`, `performMenuAction`, `onContextMenuOpen/Close` | Перехват кликов меню формата | — |
| 4 | Popup выделения в сообщениях | `TextSelectionHelper.showActions` / `hideActions` | `FloatingActionMode` или `ActionBarPopupWindow` (API<M / always popup) | Copy (+ Cut/Paste если `canCut/canPaste`) | `SelectionActionsPopup`, `PasteActionPopup` |
| 4 | Long-press empty → Paste (диалог URL) | `EditTextCaption.showInputDialog` | кнопка Paste из clipboard | Не floating caret-paste как у нас | `PasteActionPopup` у каретки |
| 4 | Tap вне выделения | `TextSelectionOverlay.checkOnTap` | <200ms + <slop → `clear()` + `hideActions` | Сброс selection | `SelectionTap` / `observeSelectionTaps` |
| 5 | Автоскролл при тяге выделения | `TextSelectionHelper.scrollRunnable` | `parentRecyclerView.scrollBy` / NestedScroll `setScrollY`, шаг ~½ lineHeight | Пока `scrolling` у края | `SelectionAutoScroll` |
| 5 | Автоскролл каретки input | `ChatActivityEditTextCaption.requestRectangleOnScreen` | `rectangle.bottom += dp(1000)` | Заставляет SizeNotifier/родителя поднять поле над IME | `revealCaretDelta` в `EditorScreen` |
| 5 | Рост поля при строках | `EditTextCaption.onLineCountChanged`, `ChatActivityEnterView` hook | `setMaxLines(6)`, gravity BOTTOM | Панель ввода растёт вверх | высота блока / scroll |
| 5 | Клавиатура | `Components/SizeNotifierFrameLayout.java` | `measureKeyboardHeight`, `onSizeChanged` | Высота IME → padding/layout | `windowInsets` IME |
| 6 | Скролл ленты + selection UI | `ChatActivity` scroll listener | `textSelectionHelper.onParentScrolled()` / `stopScrolling()` | **Во время скролла**: `hideActions()`, overlay invalidate; **после IDLE**: снова `showActionsRunnable` | fade меню при scroll |
| 6 | Скролл + FloatingActionMode (input) | `FloatingActionMode.updateViewLocationInWindow` | PreDraw listener | При движении content rect → `setMoving(true)` → toolbar **hide**, потом show | то же для `SelectionBubble` |
| 6 | Horizontal scroll таблицы IV | `BlockTableCell` `HorizontalScrollView.onScrollChanged` | `textSelectionHelper.invalidate()` | Ручки пересчитываются; меню через общий hide-on-scroll | скролл широкой таблицы |
| 6 | Курсор при scroll EditText | `EditTextBoldCursor.onScrollChanged` | horizontal → `requestDisallowInterceptTouchEvent` | Вертикальный scroll текста внутри EditText — system; ActionMode следит через PreDraw | — |

---

## Ключевые файлы (чеклист)

| Файл | Роль |
|--|--|
| `.../Components/EditTextBoldCursor.java` | Курсор, handles, FloatingActionMode bridge |
| `.../Components/EditTextEffects.java` | Spoilers/quotes, `onTouchEvent`, `setOffsetY` |
| `.../Components/EditTextCaption.java` | Форматирование, paste, ActionMode menu actions |
| `.../Components/EditTextEmoji.java` | Emoji panel поверх caption (вторично) |
| `.../Components/ChatActivityEnterView.java` | Композер чата, `createMessageEditText`, shortcuts, IME |
| `.../Components/TextStyleSpan.java` | Флаги стилей |
| `.../Components/QuoteSpan.java` | Цитаты в editable |
| `.../messenger/MediaDataController.java` | `addStyleToText` / entities ↔ spans |
| `.../messenger/utils/CopyUtilities.java` | HTML paste → Spannable |
| `.../ActionBar/FloatingToolbar.java` | UI всплывающего меню |
| `.../ActionBar/FloatingActionMode.java` | Позиционирование/hide при move/scroll |
| `.../ActionBar/ActionBarPopupWindow.java` | Popup API<M и кастомные меню |
| `.../Cells/TextSelectionHelper.java` | Полный selection engine для сообщений/IV |
| `.../Components/TextSelectionHint.java` | Онбординг «потяни за ручки» |
| `.../ui/ChatActivity.java` | `fillActionModeMenu`, wiring scroll↔selection |
| `.../ui/ArticleViewer.java` | `BlockTableCell`, table selection |
| `.../Components/TableLayout.java` / `TableView.java` | Layout таблиц (display) |
| `.../Components/SizeNotifierFrameLayout.java` | Keyboard height |

Ссылки на `master` (blob):  
https://github.com/TelegramMessenger/Telegram-Android/blob/21f7d10/TMessagesProj/src/main/java/org/telegram/ui/Components/EditTextBoldCursor.java

---

## Паттерны, которые стоит перенести в Loomixx Notes

1. **Hide menu while moving/scrolling, show after settle** — `FloatingToolbarVisibilityHelper` + `onParentScrolled`/`stopScrolling`. У нас уже заложено в `task_update_2` / `SelectionMenuController`.
2. **Word bounds = letter|digit|`_`** — `TextSelectionHelper.isInterruptedCharacter`.
3. **Haptic once on long-press select** — `performHapticFeedback(LONG_PRESS)`.
4. **Edge autoscroll while dragging handle** — `scrollRunnable` с шагом от line height.
5. **Caret reveal above IME** — идея `requestRectangleOnScreen` с завышенным `bottom` ↔ наш `revealCaretDelta`.
6. **Свой toolbar, не system** — Telegram подменяет ActionMode на `FloatingToolbar`; у нас правило «свои меню» совпадает.
7. **Таблицы**: у Telegram только selection/display; CRUD и меню строки/столбца — наша зона (`TableMenus`).

Не копировать буквально View/reflection/`mEditor` — переносить **поведение и разбиение ответственности**.
