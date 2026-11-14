# Заметки — стек и правила

**Последнее обновление:** 2026-11-14 — v0.1.0

## Продукт

| | |
|--|--|
| Имя | Заметки |
| Package | `com.alekseevich47.notes` (+ `.debug`) |
| minSdk / target / compile | 26 / 35 / 35 |
| Компания | Loomixx |
| Контакты | loomixx.dev@yandex.ru · [@aalekseevich47](https://t.me/aalekseevich47) |

## Стек

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · EncryptedSharedPreferences · OkHttp · Media3 · Haze liquid glass · sherpa-onnx (reflection bridge) · T-one streaming + GigaAM v3 e2e · SpeechRecognizer fallback

## STT

1. Pass1: T-one streaming (если модель + native) иначе Android SpeechRecognizer (ru-RU, partials).
2. Pass2: GigaAM v3 e2e punct при стопе (если скачано и включено в настройках).
3. Модели качаются в Settings → `files/models/`.

## CI

`.github/workflows/android.yml` → artifact `notes-debug-apk`

## Chrome

- Bottom glass: `LiquidGlassPlate` + Haze, без AGSL.
- Mic states: IDLE / RECORDING / PROCESSING / DONE.
- Widgets 2×2 и 4×2 через RemoteViews + `RecordingService`.
