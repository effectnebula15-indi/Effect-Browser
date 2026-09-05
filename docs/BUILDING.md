# Локальная сборка

APK не публикуется через CI/релизы этого проекта — GeckoView и бинарники Tor
делают файл слишком большим для обычных каналов доставки (~150–530 МБ на
ABI), поэтому собирать нужно локально. Ниже — весь путь от чистой машины до
установленного APK.

---

## Вариант A: Android Studio (проще всего)

1. Установите [Android Studio](https://developer.android.com/studio) (актуальная
   стабильная версия уже несёт JDK 17 и умеет доустанавливать SDK-компоненты
   сама).
2. `File → Open` → выберите корень репозитория.
3. При первом открытии Studio попросит доустановить недостающие SDK-платформы
   — согласитесь. Если не предложит сама, откройте `SDK Manager` и поставьте:
   - **Android SDK Platform 37.2** (GeckoView 155 требует compileSdk ≥ 37.1;
     обычная «Android 15.0» из списка — это платформа 35, её недостаточно,
     нужна именно 37.2 через вкладку показа отдельных ревизий)
   - **Android SDK Build-Tools 37.0.0**
4. `Build → Generate Signed Bundle / APK…` для release со своей подписью,
   либо просто `Run ▶` для debug-сборки на подключённом устройстве/эмуляторе.

## Вариант B: командная строка

### Требования

- **JDK 17** (`java -version`)
- **Android SDK** с двумя компонентами:
  ```bash
  sdkmanager "platforms;android-37.2" "build-tools;37.0.0" "platform-tools"
  ```
  Если `sdkmanager` не в `PATH` — он лежит в
  `<sdk>/cmdline-tools/latest/bin/sdkmanager` (cmdline-tools скачиваются
  отдельно с https://developer.android.com/studio#command-tools, если ставите
  SDK не через Android Studio).
- **git**

### Клонирование и настройка SDK

```bash
git clone https://github.com/effectnebula15-indi/Effect-Browser.git
cd Effect-Browser
git checkout claude/android-browser-containers-tor-hjd7tf

echo "sdk.dir=/путь/к/android-sdk" > local.properties
```

(Вместо `local.properties` можно выставить `ANDROID_HOME` /
`ANDROID_SDK_ROOT` в окружении — подойдёт любой из двух способов.)

### Debug-сборка (быстрый путь, чтобы просто попробовать)

```bash
./gradlew :app:assembleDebug
```

APK появятся в `app/build/outputs/apk/debug/`:

- `app-arm64-v8a-debug.apk` — нужен почти всем современным телефонам
  (Android с 2017 года и позже)
- `app-armeabi-v7a-debug.apk` — старые 32-битные устройства
- `app-x86_64-debug.apk` — эмуляторы
- `app-universal-debug.apk` — все ABI сразу, самый тяжёлый файл

Debug-сборка подписана отладочным ключом Android — ставится сразу, без
дополнительных шагов.

### Release-сборка (со сжатием R8 и своей подписью)

Без ключа `assembleRelease` соберёт **неподписанный** APK, который не
установится на устройство. Ключ создаётся один раз:

```bash
keytool -genkeypair -v \
  -keystore effect-browser-release.jks \
  -alias effectbrowser \
  -keyalg RSA -keysize 2048 -validity 10000
```

`effect-browser-release.jks` — секрет, в репозиторий он не попадёт (уже в
`.gitignore`). Дальше собираем, передавая параметры подписи через `-P`:

```bash
./gradlew :app:assembleRelease \
  -PRELEASE_STORE_FILE=/абсолютный/путь/effect-browser-release.jks \
  -PRELEASE_STORE_PASSWORD=пароль_хранилища \
  -PRELEASE_KEY_ALIAS=effectbrowser \
  -PRELEASE_KEY_PASSWORD=пароль_ключа
```

Результат в `app/build/outputs/apk/release/`, файлы называются так же, как в
debug, но с `-release`. Эта сборка меньше debug (минифицирована R8), но всё
равно велика — GeckoView не сжимается сильно.

### Установка на телефон

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

(`-r` — переустановить поверх, если приложение уже стоит; без `adb`
достаточно скопировать APK на телефон и открыть — Android спросит
разрешение на установку из неизвестных источников).

Для большинства телефонов нужен именно `arm64-v8a`. `x86_64` — только для
эмуляторов/некоторых Chromebook.

### Тесты

```bash
./gradlew test
```

---

## Если сборка падает

- **`compileSdk`-версии не хватает** — см. шаг про `platforms;android-37.2`
  выше; частая причина — стоит только `android-37.0` или `android-36`.
- **Ошибки KSP/AGP про Kotlin-плагин** — в `gradle.properties` уже выставлены
  `android.builtInKotlin=false` и `android.newDsl=false`; это осознанный
  временный компромисс (см. README, раздел про изменившиеся API), трогать не
  нужно.
- **R8 падает на `java.lang.management.*`** — правило-исключение уже есть в
  `app/proguard-rules.pro`; если ошибка всё равно всплыла в новой версии
  зависимости — R8 сам подскажет файл `missing_rules.txt` с готовым текстом
  правила, его нужно дописать в `proguard-rules.pro`.
- Разбор архитектурных решений (почему процесс `:tor`, почему GeckoView,
  почему `resource-noexec-tor`) — в [docs/ARCHITECTURE.md](ARCHITECTURE.md).
