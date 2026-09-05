# Effect Browser

Мобильный браузер для Android с **контейнерами** — изолированными «личностями», каждая со своим
хранилищем cookie и своим выбором сети: обычное соединение или встроенный Tor.

Один и тот же сайт можно открыть одновременно в нескольких контейнерах, с разными аккаунтами,
и сессии не будут пересекаться.

---

## Статус реализации

| Шаг | Содержание | Статус |
|-----|-----------|--------|
| 1 | Контейнеры (создание / хранение / удаление) + рендеринг через GeckoView | Готово |
| 2 | Изоляция cookie между контейнерами | Готово, [как проверить](docs/VERIFYING_ISOLATION.md) |
| 3 | Закладки | Готово |
| 4 | Tor как сетевая опция контейнера | Готово |

Проект собирается (`./gradlew assembleDebug`) и проходит юнит-тесты (`./gradlew test`, 21 тест).
**Не проверялся на реальном устройстве** — см. [«Что осталось проверить»](#что-осталось-проверить).

---

## Главное архитектурное решение

Наивный план — «один GeckoRuntime, у Tor-контейнеров ставим прокси на сессию» — **не
работает**. У GeckoView нет API прокси на уровне сессии, а `network.proxy.*` — это
preferences уровня процесса.

Поэтому Tor-контейнеры живут **в отдельном процессе ОС** (`:tor`) со своим `GeckoRuntime`,
настроенным на SOCKS-порт встроенного Tor. Обычные контейнеры остаются в основном процессе.

Подробный разбор — почему именно так, что было отвергнуто и как это защищено от утечек —
в [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Это ключевой документ проекта.

---

## Сборка

```bash
# Требуется Android SDK с platform 37.2 (GeckoView 155 требует compileSdk >= 37.1)
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleDebug     # APK по ABI + universal
./gradlew test              # юнит-тесты
```

Открывается в Android Studio как обычный Gradle-проект.

### Размер APK

Debug-сборка в этом окружении даёт ~211 МБ (arm64) — но это **не показатель**: NDK не был
установлен, поэтому отладочные символы GeckoView (`libxul.so`) не вырезаны. В release-сборке
со стриппингом ожидается порядка 70–90 МБ на ABI.

Что уже сделано для размера:

- **ABI splits** — пользователь ставит один ABI, а не universal APK;
- **`resource-noexec-tor`** вместо `resource-exec-tor` — вариант с `exec` требует
  `android:extractNativeLibs=true`, что распаковало бы при установке и гигантский `libxul.so`
  GeckoView;
- **никакого DI-фреймворка** — граф зависимостей собран вручную (`di/ServiceLocator.kt`);
- **свои иконки** (`ui/components/Glyphs.kt`) вместо зависимости на `material-icons-core`;
- `isMinifyEnabled` + `isShrinkResources` в release.

---

## Структура

```
app/src/main/java/io/effect/browser/
├── core/          ProcessInfo — определение текущего процесса
├── domain/        модели, интерфейсы репозиториев, разбор адресной строки
│   ├── model/     Container, Bookmark, Tab, NetworkMode
│   ├── repository/
│   └── url/       AddressResolver, SearchEngine
├── data/          Room: сущности, DAO, реализации репозиториев
├── gecko/         GeckoRuntimeHolder, GeckoSessionPool, GeckoProxyPrefs
├── tor/           TorController, TorStatus, TorStartup
├── di/            ServiceLocator (ручной граф, свой на каждый процесс)
└── ui/            Compose: BrowserScreen, ViewModel, компоненты, тема
```

MVVM: `ui` → `domain` → `data`. `gecko` и `tor` — инфраструктурные слои, за которые `ui`
дёргает только через ViewModel.

---

## Зависимости

Все версии проверены на актуальность в момент разработки (сентябрь 2026).

| Что | Версия | Комментарий |
|-----|--------|-------------|
| GeckoView | `155.0.20260903215306` | stable-канал с `maven.mozilla.org` |
| kmp-tor | `2.6.0` | поддерживаемый преемник заброшенной `TorOnionProxyLibrary-Android` |
| kmp-tor-resource | `409.5.0` | бинарники tor, версионируются отдельно |
| AGP | `9.4.0` | |
| Kotlin | `2.4.10` | |
| Room | `2.8.4` | |
| Compose BOM | `2026.08.00` | |
| minSdk / targetSdk | 26 / 37 | |

### Что изменилось в API относительно ожиданий

Задача просила отдельно сообщить об устаревших или изменившихся API. Найдено следующее.

1. **У GeckoView нет и не было прокси на сессию.** `GeckoSessionSettings.Builder` не содержит
   ничего сетевого. Прокси настраивается только через preferences, и они глобальны для процесса.
   Замена — разделение по процессам, см. ARCHITECTURE.md.

2. **`contextId` — это не «контейнеры Firefox».** Он пишет origin-атрибут
   `geckoViewSessionContextId`, а не `userContextId`. Для изоляции хранилища этого достаточно
   (именно это нам и нужно), но популярный совет «сделать прокси через WebExtension
   `proxy.onRequest` и различать контейнеры по `cookieStoreId`» на GeckoView **не сработает**:
   `cookieStoreId` отражает `userContextId`, которого у наших контейнеров нет.

3. **Для preferences теперь есть публичный API — `GeckoPreferenceController`**
   (`setGeckoPref` / `getGeckoPref` / `clearGeckoUserPref`). Раньше это делали через
   недокументированные пути; используем актуальный.

4. **`TorOnionProxyLibrary-Android` заброшена**; её же автор ведёт **kmp-tor 2.x** —
   это и взято. Orbot не требуется, tor полностью встроен.

5. **kmp-tor: `resource-noexec-tor` вместо `resource-exec-tor`.** Вариант с `exec` запускает
   tor отдельным процессом и требует `extractNativeLibs=true` на весь APK. `noexec` грузит tor
   через JNI внутрь процесса — меньше размер установки и нет проблем с запретом на exec.

6. **AGP 9 несёт встроенную поддержку Kotlin, но KSP с ней несовместим.** Room требует KSP,
   поэтому в `gradle.properties` выставлены `android.builtInKotlin=false` и
   `android.newDsl=false` — это официальный миграционный путь Google. Стоит пересмотреть,
   когда KSP научится работать со встроенным Kotlin.

7. **GeckoView 155 требует `compileSdk >= 37.1`**, поэтому в проекте `compileSdk = 37` +
   `compileSdkMinor = 2`.

---

## Известные ограничения и решения, которые стоит обсудить

- **Tor работает, пока жив процесс `:tor`.** Демон не вынесен в foreground service, поэтому
  при выгрузке процесса системой Tor будет бутстрапиться заново. Для v1 приемлемо; апгрейд до
  `TorServiceConfig.Foreground` — очевидный следующий шаг.
- **Закладки общие для всех контейнеров** — как и предполагалось в задаче. В схеме БД у
  закладки уже есть поле `container_id` (сейчас всегда `null`), так что перевод на
  изоляцию по контейнерам не потребует миграции с потерей данных.
- **Google Play Services попадает в сборку транзитивно** через GeckoView
  (`play-services-fido`, WebAuthn/passkeys). Для приватного браузера это спорно. Убирается
  одной строкой, но тогда сайты с passkeys будут падать — поэтому по умолчанию оставлено;
  решение за вами:
  ```kotlin
  implementation(libs.geckoview) { exclude(group = "com.google.android.gms") }
  ```
- **Публикация в Google Play.** Встроенный Tor — это отдельный разговор с политиками Play; см.
  раздел в [ARCHITECTURE.md](docs/ARCHITECTURE.md#публикация-в-google-play).

## Что осталось проверить

Всё это требует реального устройства или эмулятора, которых в среде разработки не было:

1. Бутстрап Tor и что `TorStartup` действительно поднимает kmp-tor в процессе `:tor`
   (единственное место, где используется рефлексия — там же и самый большой риск).
2. Что трафик Tor-контейнера реально идёт через Tor — проверка через `check.torproject.org`.
3. Переключение между процессами по тапу на чипе контейнера.
4. Изоляция cookie на живом сайте — сценарий в [docs/VERIFYING_ISOLATION.md](docs/VERIFYING_ISOLATION.md).
