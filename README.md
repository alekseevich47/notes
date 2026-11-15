# Заметки

Голосовые заметки Loomixx. Запись с микрофона, распознавание речи на устройстве и обычный редактор.

- Package: `com.alekseevich47.notes` (debug: `com.alekseevich47.notes.debug`)
- minSdk 26, target/compile 35, versionName 1.0.0, versionCode 1
- Контакты: loomixx.dev@yandex.ru · https://t.me/aalekseevich47

## Сборка

Нужны JDK 17 и Android SDK 35 (platform + build-tools 35.0.0).

```bash
export ANDROID_HOME="$HOME/android-sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

`local.properties` не коммитится. AAR sherpa-onnx 2.0.1 Gradle скачивает сам в `app/libs/` при конфигурации. Каталог в `.gitignore`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Что внутри

Сразу после установки текст идёт через системный SpeechRecognizer (ru-RU). Модели T-one и GigaAM v3 ставятся в Настройках: прогресс, пауза, стоп, продолжение с HTTP Range. Запись с виджета 2×2 и 4×2 идёт через foreground-сервис. Готовая заметка открывается из уведомления.

## CI

`.github/workflows/android.yml` на push в `main` и `cursor/**`:

1. `testDebugUnitTest`
2. `assembleDebug`
3. артефакт `notes-debug-apk` (`app/build/outputs/apk/debug/*.apk`)

## Тесты

JVM-тесты без эмулятора: `PinHasher`, `ListAutoFormat`, `DownloadResume`.
