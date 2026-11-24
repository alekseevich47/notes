# Заметки — продуктовая спецификация v1

**Компания:** Loomixx  
**Контакты:** loomixx.dev@yandex.ru · Telegram [@aalekseevich47](https://t.me/aalekseevich47)  
**Package:** `com.alekseevich47.notes`  
**minSdk / target / compile:** 26 / 35 / 35 (широкая совместимость смартфонов)  
**UI:** Kotlin · Jetpack Compose · Material 3 · Manrope · Haze liquid glass (без AGSL)  
**STT:** sherpa-onnx AAR 2.0.1 · T-one streaming (realtime) · GigaAM v3 e2e RNNT (финал: пунктуация, регистр, цифры) · SpeechRecognizer fallback  
**Данные:** Room · EncryptedSharedPreferences · OkHttp (resume Range) · Media3 · WorkManager  

> Старое решение в ветке `cursor/android-notes-app-dc83` **не копировать**. Пишем с нуля по этой спеке. Технические URL моделей/AAR можно взять как reference.

---

## 1. Запись из приложения

1. Нижнее sticky-меню: liquid glass (как CodePen qErQmXO / callmemo `LiquidGlassPlate` + Haze, без AGSL).
2. Центр — кнопка микрофона **выше плашки** (круг выступает вверх и вниз).
3. Слева «Заметки», справа «Настройки».
4. Покой: плавная фиолетовая волна-градиент внутри контурной светлой иконки микрофона.
5. Старт: тихий beep → RECORDING: заливка иконки тёмно-фиолетовыми градиентами по уровню громкости + glow.
6. Появляется почти fullscreen sheet с realtime текстом (пунктуация/списки по возможности на финале GigaAM).
7. Параллельно PCM/WAV/M4A сохраняется на устройстве.
8. Стоп: PROCESSING (mic → 3 точки-волна) → DONE (галочка в круге) → редактирование title+body.
9. Автоназвание: `Заметка #<N>` (следующий номер).
10. Toolbar: bold / italic / strike / underline / hidden / table / lists.

## 2. Виджеты

- **2×2:** скруглённый квадрат, mic по центру, те же состояния/анимации.
- **4×2:** mic слева (компакт), справа live transcript: старые строки уходят вверх с alpha↓, новые ярче.
- После обработки — notification → tap открывает заметку в приложении.

## 3. Заметки

- Поиск по title+body (быстрый).
- Иконка календаря → кастомный date/range picker (стиль tennis/callmemo).
- FAB «+» glass → fullscreen ручная заметка.
- Скрытый текст: мозаичная цензура без AGSL/RenderEffect; tap → PIN dialog (кастомный).
- Списки: авто `1.` в начале строки, Enter → `2.`, пустой Enter → выход; вставка между → ренумерация; после маркера пробел + Capitalize.
- Таблицы: UX как Telegram (диалог rows×cols, редактируемые ячейки). «Вставить» в меню ячейки. Хранение — JSON документа.
- Выделение: короткое меню «Вырезать / Копировать», «Вставить» в расширенном. Меню над выделением, прячется на время тяги ручек и скролла. Долгое нажатие на пустое место предлагает «Вставить». Ручка у края клавиатуры прокручивает текст.
- При первом запуске окно «Информация». Закрывается кнопкой «Продолжить».

## 4. Настройки

- Скачивание моделей T-one + GigaAM: progress, **pause / stop / resume** (HTTP Range), сеть offline→online продолжить.
- Светлая (около-белый) / тёмная тема.
- PIN 4 цифры. Сервисный сброс существует только как хэш (SHA-256 + pepper). Цифры кода нигде не публикуются.
- «Правила использования» (`TermsOfUse`, редакция 1.0) — пункт внизу настроек.
- «Сообщить об ошибке»: письмо через почтовое приложение, без SMTP. Скриншоты — JPEG без EXIF. Журнал прилагается по желанию.
- Блок Loomixx + контакты.

## 5. Плеер

- Кастомный мини-плеер в UI заметки.
- MediaSession + foreground notification controls при свёрнутом приложении.

## 6. Качество

- Плавные анимации 2–3 ключевых motion, без jank.
- Не грузить CPU в idle; STT только во время записи/финализации.
- ABI: arm64-v8a + armeabi-v7a.
- Release minify; debug `.debug` suffix.
- CI: GitHub Actions → artifact `notes-debug-apk` на push `main` и `cursor/**`.
- Unit-тесты: PinHasher, ListAutoFormat, ModelDownload resume helpers.
- Все диалоги/уведомления внутри app — кастомные.

## 7. Структура пакетов

```
com.alekseevich47.notes/
  NotesApp.kt, MainActivity.kt
  di/
  domain/
  data/ local, repository, secure, download, audio
  stt/
  service/  RecordingService, PlaybackService, ModelDownloadWorker
  ui/ theme, components, notes, editor, record, settings, widgets
```

## 8. Acceptance

- [ ] assembleDebug + testDebugUnitTest зелёные (локально и CI)
- [ ] APK artifact скачивается с GitHub Actions
- [ ] Запись → partial → finalize GigaAM → заметка + audio file
- [ ] Виджеты 2×2 / 4×2
- [ ] Редактор: spans, hidden+PIN, lists, table
- [ ] Download pause/resume
- [ ] Плеер + notification controls
- [ ] Светлая/тёмная тема; mic выступает из glass bar
