# Заметки

Android-приложение голосовых заметок (Loomixx).

- Диктовка из приложения и с виджетов 2×2 / 4×2
- Онлайн-partials (SpeechRecognizer / T-one) + финализация GigaAM v3 e2e
- Заметки с форматированием, списками, таблицами, скрытым текстом
- Модели скачиваются в настройках (APK без тяжёлых весов)

## Сборка

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Также: GitHub Actions → artifact `notes-debug-apk`.

## Стек

См. [docs/STACK.md](docs/STACK.md).
