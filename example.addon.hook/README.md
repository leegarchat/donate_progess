# Pine Addon Development Guide

Инструментарий для создания Xposed-совместимых аддонов (хуков) в виде автономных DEX JAR-файлов.
Эти аддоны динамически загружаются менеджером **CustomParts / PixelParts** без модификации системного образа.

Менеджер использует фреймворк [Pine](https://github.com/nickilicious/nickiliciousPine) — нативную
реализацию ART-хукинга, обёрнутую Xposed-совместимым API (`XposedHelpers`, `XC_MethodHook` и т.д.).

---

## Содержание

1. [Быстрый старт](#-быстрый-старт)
2. [Структура проекта](#-структура-проекта)
3. [Формат addon.json](#-формат-addonjson-манифест)
4. [Как написать хук](#-как-написать-хук)
5. [Настройки аддона (Settings UI)](#-настройки-аддона-auto-generated-ui)
6. [Компиляция и сборка](#-компиляция-и-сборка)
7. [Архитектура менеджера Pine](#-архитектура-менеджера-pine)
8. [Жизненный цикл аддона](#-жизненный-цикл-аддона-от-jar-до-хука)
9. [Settings.Global — флаги и конфигурация](#-settingsglobal--флаги-и-конфигурация)
10. [Управление целевыми пакетами (Scope)](#-управление-целевыми-пакетами-scope)
11. [Установка и удаление через UI](#-установка-и-удаление-через-ui)
12. [Устранение неполадок](#-устранение-неполадок)
13. [Справочник API](#-справочник-api)

---

## Быстрый старт

Нужна только **Java 11+**. Android Studio и полный SDK **не требуются**.

```bash
# 1. Создайте папку проекта
mkdir my_addon && cd my_addon
mkdir -p src/com/example/addon META-INF

# 2. Напишите хук (src/com/example/addon/MyHook.java)
# 3. Создайте манифест (META-INF/addon.json)
# 4. Соберите:
cd ..
./build_addon.sh my_addon

# 5. Результат: my_addon/out/my_addon.jar
```

Скрипт сам скомпилирует Java → class → DEX и упакует всё в JAR.

---

## Структура проекта

### Рабочее пространство (workspace)

```text
example.addon.hook/              ← Вы здесь
├── build_addon.sh               # Скрипт сборки
├── prebuild/                    # Предсобранные зависимости
│   ├── android.jar              #   Android API stubs
│   ├── IAddonHook.java          #   Интерфейс аддона (компилируется при сборке)
│   ├── pine/
│   │   ├── pine-core.jar        #   Pine ART hooking core
│   │   └── pine-xposed.jar      #   Xposed compatibility layer
│   ├── xposed/
│   │   ├── api-82.jar            #   Xposed API (XposedHelpers, XC_MethodHook...)
│   │   └── api-82-sources.jar    #   Исходники (для IDE)
│   └── sdk/
│       └── d8.jar               #   DEX compiler (из Android build-tools)
│
├── test_project/                # Пример аддона
│   ├── src/
│   │   └── com/example/addon/test/
│   │       └── SimpleTestHook.java
│   ├── META-INF/
│   │   └── addon.json
│   └── out/                     # Результат сборки
│       └── test_project.jar
│
└── my_addon/                    # ← ВАША ПАПКА АДДОНА
    ├── src/                     #   Исходники Java
    ├── META-INF/
    │   └── addon.json           #   Манифест
    └── out/                     #   Сюда попадёт итоговый .jar
```

### Менеджер Pine (родительский каталог `../`)

```text
../xposed-pine/src/org/pixel/customparts/
├── core/
│   ├── IAddonHook.java          # Интерфейс, который реализует ваш аддон
│   ├── IHookEnvironment.java    # Абстракция чтения настроек + логирования
│   └── BaseHook.java            # Базовый класс встроенных хуков
├── hooks/                       # Встроенные хуки (Launcher, SystemUI, глобальные)
│   ├── EdgeEffectHook.java
│   ├── MagnifierHook.java
│   ├── recents/
│   ├── systemui/
│   └── ...
└── manager/
    ├── pine/
    │   ├── ModEntry.java        # Точка входа: загрузка libpine.so → HookEntry.init()
    │   ├── HookEntry.java       # Роутер: встроенные хуки + AddonLoader
    │   ├── AddonLoader.java     # Сканер/загрузчик addon JAR-файлов
    │   └── PineEnvironment.java # Реализация IHookEnvironment для Pine
    └── xposed/
        ├── XposedInit.kt        # Альтернативный путь через LSPosed/Xposed
        └── XposedEnvironment.kt

../common/src/org/pixel/customparts/ui/addons/
└── AddonManagerScreen.kt        # Compose UI менеджера аддонов
```

> **Ключевая мысль**: `prebuild/` содержит минимальный набор инструментов. Скрипт сборки приоритетно
> использует их, так что собирать можно без установки Android SDK.

---

## Формат addon.json (манифест)

Файл `META-INF/addon.json` — **обязателен**. Менеджер читает его **до** загрузки вашего кода.

### Минимальный пример

```json
{
    "id": "my_cool_hook",
    "entryClass": "com.example.addon.MyCoolHook",
    "name": "My Cool Hook",
    "version": "1.0",
    "targetPackages": ["com.android.settings"],
    "enabled": true
}
```

### Полный пример со всеми полями

```json
{
    "id": "test_visual_hook",
    "entryClass": "com.example.addon.test.SimpleTestHook",
    "name": "Visual Test Hook",
    "author": "LeeGarChat",
    "description": "Показывает дополнительную кнопку в настройках",
    "version": "1.1",
    "targetPackages": [
        "com.android.settings"
    ],
    "enabled": true,
    "icon": "META-INF/icon.webp",
    "background": "META-INF/bg.webp",
    "backgroundMode": "gradient",
    "backgroundAlpha": 60,
    "backgroundGradientSteps": [0, 60, 100],
    "backgroundBlur": true,
    "backgroundBlurRadius": 20,
    "cardColor": "#1A237E",
    "backgroundScope": "full",

    "settings": [
        {
            "key": "test_hook_intensity",
            "title": "Hook Intensity",
            "description": "Уровень интенсивности хука (0–100)",
            "type": "int",
            "provider": "global",
            "default": 50,
            "min": 0,
            "max": 100,
            "step": 5,
            "unit": "%"
        }
    ]
}
```

### Описание полей

| Поле           | Тип       | Обязательно | Описание                                                                                                                                                                       |
| ------------------ | ------------ | :--------------------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`             | `string`   |     **Да**     | Уникальный идентификатор аддона. Используется как ключ в `Settings.Global`. Не должен содержать пробелов.  |
| `entryClass`     | `string`   |     **Да**     | Полное имя класса, реализующего `IAddonHook`. Например: `com.example.addon.MyHook`.                                                             |
| `name`           | `string`   |         Нет         | Отображаемое имя в UI менеджера. По умолчанию =`id`.                                                                                             |
| `author`         | `string`   |         Нет         | Автор аддона. По умолчанию:`"Unknown"`.                                                                                                                        |
| `description`    | `string`   |         Нет         | Описание функциональности.                                                                                                                                     |
| `version`        | `string`   |         Нет         | Версия аддона. По умолчанию:`"1.0"`.                                                                                                                          |
| `targetPackages` | `string[]` |         Нет         | Список пакетов-целей. Пустой или отсутствующий → применяется ко**всем** инжектируемым процессам. |
| `enabled`        | `boolean`  |         Нет         | Включён по умолчанию. Пользователь может переопределить через UI или `Settings.Global`.                                     |
| `icon`           | `string`   |         Нет         | Путь к иконке внутри JAR (например `META-INF/icon.webp`). Поддерживаемые форматы: **PNG, JPEG, WebP, AVIF, BMP, GIF** (любой формат, поддерживаемый `BitmapFactory`). Отображается в карточке и диалоге вместо стандартной иконки. |
| `background`     | `string`   |         Нет         | Путь к фоновому изображению внутри JAR (например `META-INF/bg.webp`). Поддерживаемые форматы: **PNG, JPEG, WebP, AVIF, BMP, GIF**. Отображается на фоне карточки аддона. |
| `backgroundMode` | `string`   |         Нет         | Режим отображения фона: `"gradient"` (с градиентным оверлеем, **по умолчанию**), `"cover"` (полное покрытие без градиента). |
| `backgroundAlpha` | `int`     |         Нет         | Прозрачность фонового изображения: `0` = полностью прозрачный, `100` = полностью непрозрачный. По умолчанию: `50`. Когда аддон выключен, значение автоматически снижается на 60%. |
| `backgroundGradientSteps` | `int[]` |     Нет         | Массив шагов градиента — каждый элемент задаёт непрозрачность (**0–100**) цвета поверхности на соответствующем шаге градиента. Например: `[0, 60, 100]` — прозрачный сверху, 60% по центру, полностью непрозрачный снизу. Минимум 2 шага. По умолчанию: `[0, 100]`. Используется только при `backgroundMode: "gradient"`. |
| `backgroundBlur` | `boolean` |         Нет         | Включить блюр-эффект на фоновом изображении. По умолчанию: `false`. |
| `backgroundBlurRadius` | `int` |         Нет         | Радиус размытия в dp (от `0` до `100`). Больше = сильнее размытие. По умолчанию: `25`. Используется только при `backgroundBlur: true`. |
| `cardColor`      | `string`   |         Нет         | Пользовательский цвет фона карточки в формате HEX (например `"#FF5722"`, `"#1A237E"`). Если не указан, используется системный цвет `surface`. Влияет также на цвет градиентного оверлея. |
| `backgroundScope` | `string`  |         Нет         | Область действия фона: `"full"` (фон растягивается на всю карточку включая раскрывающиеся настройки, **по умолчанию**), `"header"` (фон только в шапке карточки, настройки отображаются на чистом фоне). |
| `settings`       | `array`    |         Нет         | Массив определений настроек для авто-генерации UI (см. раздел ниже).                                                              |

> **Визуальные эффекты карточки** — 4 независимых и комбинируемых параметра:
>
> 1. **Прозрачность фона** (`backgroundAlpha`) — контролирует насколько виден фон
> 2. **Градиент** (`backgroundMode` + `backgroundGradientSteps`) — массив шагов непрозрачности для гибкого управления градиентом
> 3. **Блюр** (`backgroundBlur` + `backgroundBlurRadius`) — размытие фонового изображения
> 4. **Цвет карточки** (`cardColor`) — произвольный цвет фона карточки
> 5. **Область фона** (`backgroundScope`) — фон на всю карточку или только в шапке
>
> Все эффекты можно комбинировать: например, `backgroundAlpha: 80` + `backgroundBlur: true` + `backgroundGradientSteps: [0, 40, 100]` даёт яркий размытый фон с 3-шаговым градиентом.

### Описание полей блока settings

| Поле        | Тип     | Обязательно | Описание                                                                                   |
| --------------- | ---------- | :--------------------: | -------------------------------------------------------------------------------------------------- |
| `key`         | `string` |     **Да**     | Ключ в `Settings.Global` (или `System`/`Secure`).                                    |
| `title`       | `string` |     **Да**     | Заголовок настройки в UI.                                                       |
| `description` | `string` |         Нет         | Подзаголовок/описание.                                                         |
| `type`        | `string` |     **Да**     | Тип:`int`, `float`, `string`, `select`, `file`, `toggle`, `switch`, `checkbox`. |
| `provider`    | `string` |         Нет         | Хранилище:`global` (по умолчанию), `system`, `secure`.                   |
| `default`     | `any`    |         Нет         | Значение по умолчанию.                                                          |
| `min`         | `number` |         Нет         | Минимум (для `int`/`float`).                                                         |
| `max`         | `number` |         Нет         | Максимум (для `int`/`float`).                                                       |
| `step`        | `number` |         Нет         | Шаг (для `int`/`float`).                                                                 |
| `unit`        | `string` |         Нет         | Суффикс единиц измерения (`%`, `x`, `dp`, `ms`...).                  |
| `options`     | `array`  |         Нет         | Варианты для `select`: `[{"value": "v1", "label": "Label 1"}, ...]`.                |
| `mimeType`    | `string` |         Нет         | MIME-тип для `file` (по умолчанию: `*/*`).                                    |

---

## Как написать хук

### Интерфейс IAddonHook

Ваш главный класс **обязан** реализовать интерфейс `org.pixel.customparts.core.IAddonHook`.

```java
package com.example.addon;

import android.content.Context;
import android.util.Log;
import org.pixel.customparts.core.IAddonHook;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class MyHook implements IAddonHook {

    private static final String TAG = "MyHook";

    // --- Метаданные ---

    @Override
    public String getId() {
        return "my_cool_hook";          // Уникальный ID — должен совпадать с addon.json
    }

    @Override
    public String getName() {
        return "My Cool Hook";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    @Override
    public String getDescription() {
        return "Описание того, что делает хук";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    // --- Целевые пакеты ---

    @Override
    public Set<String> getTargetPackages() {
        Set<String> targets = new HashSet<>();
        targets.add("com.android.settings");
        // targets.add("com.android.systemui");
        return targets;
        // Верните null или пустой Set, чтобы хукать ВСЕ процессы
    }

    // --- Приоритет (необязательно) ---

    @Override
    public int getPriority() {
        return 0;   // Больше = выполняется раньше
    }

    // --- Проверка включён ли хук (необязательно) ---

    @Override
    public boolean isEnabled(Context context) {
        return true;  // Можно читать Settings.Global и т.д.
    }

    // --- Главный метод — здесь происходит хукинг ---

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        Log.d(TAG, "Хук загружен в процесс: " + packageName);

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.settings.dashboard.DashboardFragment",
                classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Log.d(TAG, "DashboardFragment.onResume() перехвачен!");
                        // Ваша логика здесь
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "Ошибка при установке хука", t);
        }
    }
}
```

### Сигнатура handleLoadPackage

```java
void handleLoadPackage(Context context, ClassLoader classLoader, String packageName);
```

| Параметр | Описание                                                                                                           |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `context`      | `Application` Context целевого процесса (не менеджера!).                                      |
| `classLoader`  | `ClassLoader` целевого приложения — используйте его для поиска классов. |
| `packageName`  | Имя пакета запущенного процесса.                                                               |

> **Важно**: ваш код выполняется **внутри целевого процесса** (например, внутри SystemUI),
> а не внутри менеджера CustomParts.

### Доступный API для хукинга

Pine предоставляет полноценный Xposed-совместимый слой. Вы можете использовать:

```java
// Хук метода (before/after)
XposedHelpers.findAndHookMethod(className, classLoader, methodName, paramTypes..., callback);

// Замена метода целиком
XposedHelpers.findAndHookMethod(className, classLoader, methodName,
    new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            return null; // ваша реализация
        }
    }
);

// Хук конструктора
XposedHelpers.findAndHookConstructor(className, classLoader, paramTypes..., callback);

// Поиск класса
Class<?> clazz = XposedHelpers.findClass("com.example.TargetClass", classLoader);

// Чтение/запись полей
Object value = XposedHelpers.getObjectField(instance, "fieldName");
XposedHelpers.setObjectField(instance, "fieldName", newValue);
XposedHelpers.getStaticObjectField(clazz, "staticField");

// Вызов методов
Object result = XposedHelpers.callMethod(instance, "methodName", args...);
XposedHelpers.callStaticMethod(clazz, "staticMethod", args...);

// Создание экземпляров
Object instance = XposedHelpers.newInstance(clazz, constructorArgs...);
```

### Чтение настроек внутри хука

Значения настроек, определённых в `addon.json`, хранятся в `Settings.Global`:

```java
import android.provider.Settings;

// Целые числа
int intensity = Settings.Global.getInt(
    context.getContentResolver(), "test_hook_intensity", 50);

// Дробные числа
float scale = Settings.Global.getFloat(
    context.getContentResolver(), "test_hook_scale", 1.0f);

// Строки
String msg = Settings.Global.getString(
    context.getContentResolver(), "test_hook_message");

// Boolean (switch/toggle/checkbox хранятся как int 0/1)
boolean enabled = Settings.Global.getInt(
    context.getContentResolver(), "demo_dark_override", 0) != 0;
```

> Провайдер определяется полем `provider` в настройке (`global`, `system`, `secure`).

---

## Настройки аддона (Auto-generated UI)

Вам **не нужно** писать Android UI-код. Определите настройки в `addon.json`, и менеджер
автоматически сгенерирует нативный экран настроек (Jetpack Compose / Material 3).

### Поддерживаемые типы

| Тип       | UI-элемент                            | Значение                    |
| ------------ | -------------------------------------------- | ----------------------------------- |
| `switch`   | Switch (переключатель)          | `int` 0/1                         |
| `toggle`   | Switch (переключатель)          | `int` 0/1                         |
| `checkbox` | Checkbox (флажок)                      | `int` 0/1                         |
| `int`      | Slider + ручной ввод               | `int`                             |
| `float`    | Slider + ручной ввод               | `float`                           |
| `string`   | TextField (текстовое поле)      | `string`                          |
| `select`   | Dropdown (выпадающий список) | `string`                          |
| `file`     | File picker (выбор файла)          | `string` (путь к файлу) |

### Примеры определений

```json
"settings": [
    {
        "key": "icon_size",
        "title": "Icon Size",
        "description": "Размер иконок",
        "type": "int",
        "provider": "global",
        "default": 40,
        "min": 10,
        "max": 100,
        "step": 2,
        "unit": "dp"
    },
    {
        "key": "animation_speed",
        "title": "Animation Speed",
        "type": "float",
        "default": 1.0,
        "min": 0.0,
        "max": 3.0,
        "step": 0.1,
        "unit": "x"
    },
    {
        "key": "enable_logs",
        "title": "Enable Logging",
        "type": "switch",
        "default": false
    },
    {
        "key": "operating_mode",
        "title": "Operating Mode",
        "type": "select",
        "default": "normal",
        "options": [
            { "value": "silent",  "label": "Silent — тихий режим" },
            { "value": "normal",  "label": "Normal — стандартный" },
            { "value": "verbose", "label": "Verbose — подробный лог" }
        ]
    },
    {
        "key": "custom_message",
        "title": "Custom Message",
        "description": "Текст для отображения",
        "type": "string",
        "default": "Hello!"
    },
    {
        "key": "config_file",
        "title": "Config File",
        "description": "Конфигурационный файл",
        "type": "file",
        "mimeType": "application/json"
    }
]
```

---

## Компиляция и сборка

### Требования

- **Java 11+** (`javac`, `jar`)
- Всё остальное есть в `prebuild/`

Проверьте:

```bash
java -version    # должна быть 11+
javac -version
```

Установка (Ubuntu/WSL):

```bash
sudo apt install openjdk-17-jdk
```

### Команда сборки

```bash
./build_addon.sh <ИМЯ_АДДОНА> [ПУТЬ_К_ПРОЕКТУ]
```

| Параметр               | Описание                                                      |
| ------------------------------ | --------------------------------------------------------------------- |
| `ИМЯ_АДДОНА`        | Имя выходного JAR-файла (без пробелов).   |
| `ПУТЬ_К_ПРОЕКТУ` | Папка с `src/` и `META-INF/` (необязательно). |

Если `ПУТЬ_К_ПРОЕКТУ` не указан, скрипт ищет папку `<ИМЯ_АДДОНА>` рядом с собой.

### Примеры

```bash
# Собрать test_project (папка test_project/ рядом со скриптом)
./build_addon.sh test_project

# Собрать с явным путём
./build_addon.sh my_hook ./path/to/my_hook

# Переопределить пути к инструментам
ANDROID_JAR=/path/to/android.jar D8_JAR=/path/to/d8.jar ./build_addon.sh my_hook
```

### Что делает скрипт (4 шага)

```
[1/4] Компиляция IAddonHook.java     → build/stubs/  (stub интерфейса)
[2/4] Компиляция исходников аддона    → build/classes/ (ваш код + stub)
[3/4] Конвертация в DEX (d8)          → build/dex/classes.dex
[4/4] Упаковка JAR                    → out/<ИМЯ>.jar (classes.dex + META-INF/addon.json)
```

**Classpath при компиляции**:

- `android.jar` — Android API stubs
- `build/stubs/` — скомпилированный `IAddonHook.class`
- `pine-xposed.jar` — Xposed compatibility (XposedHelpers, XC_MethodHook...)
- `pine-core.jar` — Pine core
- `api-82.jar` — Xposed API

### Структура выходного JAR

```
my_addon.jar
├── classes.dex           # DEX с вашими классами
└── META-INF/
    ├── addon.json        # Манифест аддона
    ├── icon.png          # (необязательно) Иконка аддона
    └── bg.png            # (необязательно) Фоновое изображение карточки
```

### Переменные окружения

| Переменная                    | Описание                                                                                |
| --------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `ANDROID_JAR`                         | Путь к `android.jar` (приоритет над `prebuild/`).                          |
| `D8_JAR`                              | Путь к `d8.jar` (приоритет над `prebuild/sdk/`).                           |
| `ANDROID_SDK_ROOT` / `ANDROID_HOME` | SDK root — скрипт найдёт `android.jar` и `d8.jar` автоматически. |

---

## Архитектура менеджера Pine

### Общая схема

```
┌─────────────────────────────────────────────────────────────────┐
│                    Android System (ART)                         │
│                                                                 │
│  ┌───────────────┐    libpine.so     ┌────────────────────────┐ │
│  │  Целевой      │  ◄──────────────  │  ModEntry.init()       │ │
│  │  процесс      │                   │  (внедряется в каждый  │ │
│  │  (Settings,   │                   │   процесс из whitelist)│ │
│  │  SystemUI...) │                   └───────────┬────────────┘ │
│  └───────────────┘                               │              │
│         ▲                                        ▼              │
│         │                           ┌────────────────────────┐  │
│         │   XposedHelpers.*         │  HookEntry.init()      │  │
│         │   XC_MethodHook           │  ┌──────────────────┐  │  │
│         │                           │  │ Встроенные хуки  │  │  │
│         │                           │  │ (Launcher, SysUI)│  │  │
│         │                           │  └──────────────────┘  │  │
│         │                           │  ┌──────────────────┐  │  │
│         └───────────────────────────│──│ AddonLoader      │  │  │
│                                     │  │ .loadAndRunAddons│  │  │
│                                     │  └──────────────────┘  │  │
│                                     └────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  /data/pixelparts/addons/                                   ││
│  │  ├── my_hook.jar          ← DexClassLoader загружает        ││
│  │  ├── pixel_studio.jar     ← META-INF/addon.json читается    ││
│  │  └── ...                                                    ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  CustomParts App (UI)                                       ││
│  │  └── AddonManagerScreen.kt  — Compose UI для управления     ││
│  │      - Сканирует JAR-файлы, читает addon.json               ││
│  │      - Включает/выключает аддоны (Settings.Global)          ││
│  │      - Управляет scope (целевые пакеты)                     ││
│  │      - Рендерит авто-настройки из settings[]                ││
│  │      - Импортирует/удаляет JAR файлы                        ││
│  │      - Показывает иконку/фон из JAR (icon, background)      ││
│  │      - Панель «Активные приложения» — какие аддоны           ││
│  │        действуют на какие приложения, с переходом к карточке ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### Ключевые компоненты

#### ModEntry.java — Точка входа

Внедряется в каждый целевой процесс через системный патч ActivityThread.
Загружает `libpine.so`, получает `Application` context текущего процесса и вызывает `HookEntry.init()`.

```
ModEntry.init()
  → System.load("libpine.so")
  → app = ActivityThread.currentApplication()
  → HookEntry.init(app, classLoader, packageName)
```

#### HookEntry.java — Маршрутизатор хуков

Определяет, какие хуки применять к текущему процессу:

1. **Whitelist-пакеты** (Launcher, SystemUI) → встроенные хуки **первыми**, потом аддоны.
2. **Не в whitelist, но есть аддон** → **только** аддон-хуки (встроенные не применяются).
3. **Не в whitelist, нет аддонов** → пропуск (процесс не должен был попасть сюда).

```java
if (inWhitelist) {
    // Сначала встроенные хуки (Launcher/SystemUI)
    if (isLauncher)  initLauncherHooks(context, classLoader);
    if (isSystemUI)  initSystemUIHooks(context, classLoader);
}
// Затем (или вместо) — аддон-хуки
if (hasAddons) {
    AddonLoader.loadAndRunAddons(context, classLoader, packageName);
}
```

#### AddonLoader.java — Загрузчик аддонов

| Метод                         | Назначение                                                                                                                       |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `loadAndRunAddons(ctx, cl, pkg)` | Загрузить все JAR (если ещё не загружены), отфильтровать по пакету, выполнить. |
| `hasAddonsForPackage(ctx, pkg)`  | Есть ли хотя бы один аддон для данного пакета?                                                        |
| `getAllAddonTargetPackages(ctx)` | Все пакеты, нужные всем включённым аддонам.                                                            |
| `getAllAddons(ctx)`              | Метаданные всех аддонов (для UI).                                                                                  |
| `rescan(ctx)`                    | Принудительное пересканирование (после установки нового аддона).                   |
| `deleteAddon(ctx, id)`           | Удалить JAR + очистить настройки + обновить whitelist.                                                     |
| `syncWhitelist(ctx)`             | Синхронизировать whitelist инжекции со всеми целевыми пакетами.                             |

**Директории сканирования** (в порядке приоритета):

1. `/system_ext/etc/pixelparts/addons/` — системные аддоны (read-only)
2. `/data/pixelparts/addons/` — пользовательские аддоны

**Загрузка одного аддона**:

```
1. Прочитать дескриптор: сперва .jar.json (внешний), затем META-INF/addon.json (из JAR)
2. Распарсить метаданные: id, entryClass, targetPackages...
3. Проверить enabled (json + Settings.Global override)
4. DexClassLoader загружает JAR
5. Инстанцировать entryClass → проверить instanceof IAddonHook
6. Сохранить в loadedAddons map
```

#### PineEnvironment.java — Чтение настроек

Автоматически добавляет суффикс `_pine` ко всем ключам `Settings.Global`.
Это позволяет встроенным хукам иметь отдельные настройки для Pine- и Xposed-среды.

```java
// Реальный ключ = baseKey + "_pine"
// Например: "edge_effect_enabled" → "edge_effect_enabled_pine"
```

> **Для аддонов** это не применяется — аддоны читают `Settings.Global` напрямую
> по ключам из `addon.json`.

---

## Жизненный цикл аддона (от JAR до хука)

```
                Пользователь
                    │
                    ▼
         ┌───────────────────────┐
    ①    │ Сборка: build_addon.sh│
         │ Java → .class → DEX   │
         │ + META-INF/addon.json │
         │ → out/addon.jar       │
         └──────────┬────────────┘
                    │
                    ▼
         ┌───────────────────────┐
    ②    │ Установка:            │
         │ UI: AddonManagerScreen│  ← Импорт через file picker
         │ или вручную:          │  ← cp addon.jar /data/pixelparts/addons/
         └──────────┬────────────┘
                    │
                    ▼
         ┌───────────────────────┐
    ③    │ Конфигурация (UI):    │
         │ - Вкл/выкл аддон      │
         │ - Настройка scope     │
         │ - Изменение settings  │
         └──────────┬────────────┘
                    │
                    ▼ (перезагрузка / перезапуск целевого приложения)
         ┌───────────────────────┐
    ④    │ ActivityThread запуск │
         │ → ModEntry.init()     │
         │ → HookEntry.init()    │
         └──────────┬────────────┘
                    │
                    ▼
         ┌───────────────────────┐
    ⑤    │ AddonLoader           │
         │ - Сканирует JAR-файлы │
         │ - Читает addon.json   │
         │ - DexClassLoader      │
         │ - new entryClass()    │
         └──────────┬────────────┘
                    │
                    ▼
         ┌───────────────────────┐
    ⑥    │ IAddonHook            │
         │ .handleLoadPackage()  │
         │ → XposedHelpers.*     │
         │ → Ваши хуки работают  │
         └───────────────────────┘
```

---

## ⚙ Settings.Global — флаги и конфигурация

Менеджер и аддоны общаются через `Settings.Global`. Вот все используемые ключи:

### Системные флаги (менеджер)

| Ключ                                   | Тип        | Описание                                                                          |
| ------------------------------------------ | ------------- | ----------------------------------------------------------------------------------------- |
| `pixel_addon_{id}_enabled`               | `int` 0/1   | Включён ли аддон (пользовательский override).               |
| `pixel_addon_{id}_packages`              | `string`    | Пользовательские целевые пакеты (через запятую). |
| `pixel_addon_{id}_scope_mode`            | `int` 0/1/2 | Режим scope: 0=default, 1=custom, 2=merge.                                           |
| `pixel_extra_parts_inject_package_{pkg}` | `int` 0/1   | Whitelist инжекции ActivityThread.                                                |

### Пользовательские настройки (аддон)

Каждая настройка из `addon.json → settings[]` хранится по ключу `key` в указанном `provider`:

```
Например:
  Settings.Global → "test_hook_intensity" = 75
  Settings.Global → "demo_dark_override" = 1
  Settings.Global → "test_hook_message" = "Custom text"
```

---

## 🎯 Управление целевыми пакетами (Scope)

### Режимы scope_mode

| Значение | Название | Поведение                                                                                             |
| ---------------- | ---------------- | -------------------------------------------------------------------------------------------------------------- |
| `0`            | Default          | Используются только `targetPackages` из `addon.json`.                                  |
| `1`            | Custom           | Используются только пакеты, заданные пользователем через UI. |
| `2`            | Merge            | Объединение дефолтных + пользовательских.                                  |

### Whitelist инжекции

Для каждого целевого пакета аддона менеджер устанавливает флаг:

```
pixel_extra_parts_inject_package_{package_name} = 1
```

Это сигнал для системного патча `ActivityThread` о том, что в данный процесс нужно инжектировать Pine.

> Пакеты из встроенного whitelist (Launcher, SystemUI) уже инжектируются всегда.
> Флаги нужны только для **дополнительных** пакетов, таргетируемых аддонами.

---

## 📲 Установка и удаление через UI

### Установка

1. Откройте **CustomParts → Addon Manager**.
2. Нажмите кнопку импорта (FAB).
3. Выберите `.jar` файл из файлового менеджера.
4. Менеджер скопирует JAR в `/data/pixelparts/addons/` (с root-фолбэком при необходимости).
5. Аддон появится в списке.

**Ручная установка** (через adb/root):

```bash
adb push my_addon.jar /data/pixelparts/addons/
# или
adb shell su -c "cp /sdcard/my_addon.jar /data/pixelparts/addons/"
adb shell su -c "chmod 644 /data/pixelparts/addons/my_addon.jar"
```

### Удаление

Через UI или вручную:

```bash
adb shell su -c "rm /data/pixelparts/addons/my_addon.jar"
```

При удалении через UI автоматически очищаются:

- JAR файл и внешний дескриптор (`.jar.json`)
- Флаги whitelist (если пакет больше не нужен другим аддонам)
- Настройки в `Settings.Global`

### Внешний дескриптор

Менеджер поддерживает **внешний** JSON-дескриптор рядом с JAR:

```
/data/pixelparts/addons/
├── my_addon.jar
└── my_addon.jar.json    ← Приоритет над META-INF/addon.json
```

Это полезно для быстрого изменения метаданных без перекомпиляции JAR.

---

## Устранение неполадок

| Проблема                                                | Решение                                                                                                                                                                                                   |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Unsupported class file major version`                        | Используйте Java 11 `--release 11` (скрипт делает это автоматически). Убедитесь, что `d8.jar` и `android.jar` из `prebuild/` совместимы. |
| Ошибка компиляции: класс не найден | Проверьте, что `prebuild/pine/` и `prebuild/xposed/` содержат нужные JAR.                                                                                                         |
| Аддон не отображается в UI                  | Проверьте:`.jar` в `/data/pixelparts/addons/`, `META-INF/addon.json` внутри JAR корректен, `entryClass` указывает на существующий класс.            |
| Аддон отображается, но не работает | Проверьте: аддон включён, целевой пакет в scope, флаг whitelist установлен. Посмотрите `logcat \| grep AddonLoader`.                                  |
| `ClassNotFoundException` в runtime                           | `entryClass` в `addon.json` не совпадает с реальным полным именем класса.                                                                                             |
| `does not implement IAddonHook`                               | Класс не реализует интерфейс `IAddonHook` или был скомпилирован против устаревшей версии интерфейса.                               |
| Хуки не срабатывают                            | Целевой класс/метод не найден в данной версии приложения. Оберните в `try/catch` и проверьте `logcat`.                                    |
| `d8.jar` не найден                                    | Положите в `prebuild/sdk/d8.jar` или задайте `D8_JAR=/path/to/d8.jar`.                                                                                                                    |
| `android.jar` не найден                               | Положите в `prebuild/android.jar` или задайте `ANDROID_JAR=/path/to/android.jar`.                                                                                                         |

### Полезные команды для отладки

```bash
# Логи загрузки аддонов
adb logcat -s AddonLoader PineInject HookEntry

# Проверить установленные аддоны
adb shell ls -la /data/pixelparts/addons/

# Проверить флаги Settings.Global
adb shell settings get global pixel_addon_my_hook_enabled
adb shell settings get global pixel_extra_parts_inject_package_com.android.settings

# Вручную включить/выключить аддон
adb shell settings put global pixel_addon_my_hook_enabled 1
adb shell settings put global pixel_addon_my_hook_enabled 0

# Вручную установить настройку аддона
adb shell settings put global test_hook_intensity 75
```

---

## Справочник API

### IAddonHook (интерфейс)

| Метод                          | Возврат  | Обязательный | Описание                                                                      |
| ----------------------------------- | --------------- | :----------------------: | ------------------------------------------------------------------------------------- |
| `getId()`                         | `String`      |      **Да**      | Уникальный идентификатор.                                      |
| `getName()`                       | `String`      |          Нет          | Отображаемое имя (default:`getId()`).                                |
| `getAuthor()`                     | `String`      |          Нет          | Автор (default:`"Unknown"`).                                                   |
| `getDescription()`                | `String`      |          Нет          | Описание (default:`""`).                                                    |
| `getVersion()`                    | `String`      |          Нет          | Версия (default:`"1.0"`).                                                     |
| `getTargetPackages()`             | `Set<String>` |      **Да**      | Целевые пакеты (`null`/пустой = все).                         |
| `handleLoadPackage(ctx, cl, pkg)` | `void`        |      **Да**      | Основной метод хукинга.                                           |
| `getPriority()`                   | `int`         |          Нет          | Приоритет выполнения (default:`0`, больше = раньше). |
| `isEnabled(ctx)`                  | `boolean`     |          Нет          | Проверка включён ли хук (default:`true`).                       |

### Xposed Compatibility API (ключевые классы)

| Класс                                      | Описание                                                                                |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `de.robv.android.xposed.XposedHelpers`        | Утилиты: хук методов, поиск классов, работа с полями. |
| `de.robv.android.xposed.XC_MethodHook`        | Callback для `beforeHookedMethod` / `afterHookedMethod`.                                 |
| `de.robv.android.xposed.XC_MethodReplacement` | Полная замена метода.                                                         |
| `de.robv.android.xposed.XposedBridge`         | Логирование (`XposedBridge.log()`).                                                |

---

## Лицензия

Этот инструментарий является частью проекта **CustomParts / PixelParts** (EvolutionX).

Happy hooking! 🎣
