#!/usr/bin/env python3
"""
Generate properly translated strings.xml and strings_*.json for all locales.
Reads EN as source of truth, applies hand-crafted translations per locale.
"""

import json, os, re, sys
from collections import OrderedDict

BASE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(BASE, "common", "res")
LANG = os.path.join(BASE, "lang")

LOCALES = ["ru", "de", "fr", "in", "uk"]

# ─── Parse EN XML ──────────────────────────────────────────────────

def parse_strings_xml(path):
    """Return OrderedDict of name→value preserving order.  Also returns raw lines."""
    strings = OrderedDict()
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    for m in re.finditer(r'<string\s+name="([^"]+)">(.*?)</string>', content, re.DOTALL):
        name, val = m.group(1), m.group(2)
        strings[name] = val
    return strings, content

def xml_escape(s):
    """Escape for Android string resource value."""
    return s  # values are pre-escaped in our dicts

def write_strings_xml(path, en_keys, en_content, translations, lang):
    """Write a complete strings.xml from EN structure with translations overlay."""
    lines = []
    # Rebuild from EN content structure, replacing values
    in_content = en_content
    
    # We'll reconstruct the file by iterating EN lines and replacing values
    result_lines = []
    for line in in_content.split('\n'):
        m = re.match(r'^(\s*<string\s+name=")([^"]+)(">)(.*?)(</string>\s*)$', line)
        if m:
            prefix, name, mid, _val, suffix = m.groups()
            if name in translations:
                new_val = translations[name]
                result_lines.append(f'{prefix}{name}{mid}{new_val}{suffix}')
            else:
                # Keep EN value
                result_lines.append(line)
        else:
            result_lines.append(line)
    
    # Add XML declaration for non-EN
    output = '\n'.join(result_lines)
    if not output.startswith('<?xml'):
        output = "<?xml version='1.0' encoding='utf-8'?>\n" + output
    elif not output.startswith('<?xml'):
        pass
    
    with open(path, "w", encoding="utf-8", newline='\n') as f:
        f.write(output)
    print(f"  Written: {path} ({len(translations)} translations)")

def write_json(path, strings_dict):
    """Write strings as JSON."""
    with open(path, "w", encoding="utf-8", newline='\n') as f:
        json.dump(strings_dict, f, ensure_ascii=False, indent=2)
    print(f"  Written: {path}")

# ─── TRANSLATIONS ──────────────────────────────────────────────────
# Each dict maps string_name → translated_value
# Only strings that differ from EN need to be listed.
# ALL strings for each locale should be provided for completeness.

RU = {
    # General / Navigation
    "os_title_activity": "Физика прокрутки",
    "os_title_playground": "Песочница",
    "os_btn_back": "Назад",
    "btn_back": "Назад",
    "os_btn_reset": "Сбросить настройки",
    "os_msg_reset_done": "Настройки сброшены по умолчанию",
    "os_msg_import_success": "Настройки успешно импортированы",
    "os_msg_app_exists": "Это приложение уже есть в списке",
    # Master Switch
    "os_label_master_xposed": "Модуль Xposed",
    "os_desc_master_xposed": "Использует Xposed Framework для подмены системного класса EdgeEffect.\\n\\nПозволяет внедрить физику во все приложения без модификации системы. Если модуль не активен в LSPosed, эффекты будут работать только в этом приложении (демо-режим).",
    "os_label_master_native": "Системная интеграция",
    "os_desc_master_native": "Включает встроенную в прошивку реализацию физики.\\n\\nРаботает напрямую через системные классы Pixel Parts без использования хуков, обеспечивая максимальную производительность и совместимость.",
    # SystemUI Settings
    "sysui_settings_title": "Настройки SystemUI",
    "sysui_lockscreen_title": "Настройки экрана блокировки",
    "sysui_lockscreen_subtitle": "Настройка информации о зарядке и жестов на экране блокировки",
    "sysui_shade_title": "Настройки шторки",
    "sysui_shade_subtitle": "Компактный плеер и скрим",
    # Magnifier
    "magnifier_section_title": "Текстовая лупа",
    "magnifier_enable_title": "Кастомный зум",
    "magnifier_enable_summary": "Изменить уровень увеличения текстовой лупы",
    "magnifier_zoom_title": "Уровень увеличения",
    "magnifier_size_title": "Размер окна",
    "magnifier_shape_title": "Форма окна",
    "magnifier_shape_default": "По умолчанию",
    "magnifier_shape_square": "Квадрат",
    "magnifier_shape_circle": "Круг",
    "magnifier_offset_y_title": "Смещение по вертикали",
    "magnifier_xposed_warning_title": "Требуется Xposed-модуль",
    "magnifier_xposed_warning_desc": "Модификация текстовой лупы работает поприложениям. Включите Xposed-модуль для каждого приложения, где вы хотите видеть изменённую лупу.",
    "overscroll_xposed_warning_title": "Требуется Xposed-модуль",
    "overscroll_xposed_warning_desc": "Модификация эффекта прокрутки работает поприложениям. Включите Xposed-модуль для каждого приложения, где вы хотите видеть изменённый эффект прокрутки.",
    # Media
    "sysui_media_player_title": "Медиаплеер",
    "sysui_media_compact_mode_title": "Режим компактности",
    "sysui_media_compact_mode_off": "Выключено",
    "sysui_media_compact_mode_small": "Компактный",
    "sysui_media_compact_mode_header": "Только заголовок",
    "sysui_media_compact_mode_very_small": "Очень компактный",
    "sysui_media_bg_alpha_title": "Прозрачность фона плеера",
    "sysui_media_hide_title": "Скрыть медиаплеер",
    "sysui_media_hide_qs": "Скрыть в быстрых настройках",
    "sysui_media_hide_notifications": "Скрыть в уведомлениях",
    "sysui_media_hide_lockscreen": "Скрыть на экране блокировки",
    # Shade
    "sysui_shade_surface_title": "Фон шторки",
    "sysui_shade_surface_solid": "Однотонная шторка (без скрима)",
    "sysui_shade_blur_title": "Интенсивность размытия фона",
    "sysui_shade_zoom_title": "Интенсивность зума фона",
    "sysui_shade_disable_scale_threshold_title": "Отключить порог масштабирования",
    "sysui_shade_scrim_title": "Настройка скрима",
    "sysui_shade_notif_scrim_alpha_title": "Прозрачность скрима уведомлений",
    "sysui_shade_main_scrim_alpha_title": "Прозрачность основного скрима",
    "sysui_shade_notif_scrim_tint_title": "Тонирование скрима уведомлений",
    "sysui_shade_notif_scrim_tint_color": "Цвет тонировки",
    "sysui_shade_main_scrim_tint_title": "Тонирование основного скрима",
    "sysui_shade_main_scrim_tint_color": "Цвет тонировки",
    "sysui_shade_override_enable": "Относительный множитель прозрачности",
    # Charging Info
    "sysui_charging_info_title": "Информация о зарядке",
    "sysui_charging_info_enable": "Пользовательская информация о зарядке",
    "sysui_show_wattage": "Показать мощность (Вт)",
    "sysui_show_voltage": "Показать напряжение",
    "sysui_show_current": "Показать ток (мА)",
    "sysui_show_temp": "Показать температуру батареи",
    "sysui_show_percent": "Показать процент батареи",
    "sysui_show_standard": "Показать стандартный текст (быстро/медленно)",
    "sysui_show_custom_symbol": "Показать свой символ",
    "sysui_custom_symbol": "Свой символ (напр. ⚡)",
    "sysui_sensor_interval_title": "Интервал обновления датчика",
    "sysui_average_mode_title": "Показывать усреднённые значения зарядки",
    # Status
    "os_status_active": "Активно",
    "os_status_disabled": "Отключено",
    # Xposed Dialogs
    "os_dialog_xposed_title": "Xposed не активен",
    "os_dialog_xposed_msg": "Хуки не могут быть установлены.\\n\\nПриложение не обнаружило активный фреймворк LSPosed. Без него перехват системных вызовов невозможен, настройки будут работать только внутри этого приложения.",
    # Profiles
    "os_group_profiles": "Профили настроек",
    "os_prof_btn_export": "Экспорт",
    "os_prof_btn_import": "Импорт",
    "os_prof_btn_network": "Из сети",
    "os_prof_btn_create": "Сохранить новый профиль",
    "os_prof_header_list": "Сохраненные профили",
    "os_net_dialog_title": "Конфиги из сети",
    "os_net_loading": "Загрузка…",
    "os_net_empty": "Конфигов не найдено",
    "os_net_error": "Не удалось загрузить конфиги",
    "os_net_applied": "Конфиг применён: %s",
    "os_prof_item_active": "Активен",
    "os_prof_item_tap": "Нажмите, чтобы загрузить",
    "os_prof_dialog_title": "Новый профиль",
    "os_prof_dialog_hint": "Введите название профиля",
    "os_prof_btn_save": "Сохранить",
    "btn_save": "Сохранить",
    "os_prof_delete_desc": "Удалить профиль",
    # Physics
    "os_group_physics": "Физика пружины",
    "os_lbl_physics_pull": "Тяга",
    "os_desc_physics_pull": "Множитель расстояния при ручном вытягивании.\\n\\n• Значение &gt;= 1.0: Линейное следование за пальцем.\\n• Значение &lt; 1.0: Включает нелинейное сопротивление (резинку) — чем дальше тянете, тем сильнее сопротивление.",
    "os_lbl_physics_stiffness": "Жесткость",
    "os_desc_physics_stiffness": "Константа упругости пружины.\\n\\nОпределяет силу, возвращающую список в исходное положение. Влияет на частоту колебаний при отскоке.",
    "os_lbl_physics_damping": "Затухание",
    "os_desc_physics_damping": "Коэффициент демпфирования.\\n\\nГасит энергию пружины. Определяет, как быстро затухнут колебания после отпускания пальца или удара о границу.",
    "os_lbl_physics_fling": "Импульс",
    "os_desc_physics_fling": "Множитель входящей скорости при инерционном ударе.\\n\\nУвеличивает амплитуду отскока при резкой прокрутке.\\nПримечание: При высоких значениях (&gt;1.0) код динамически снижает жесткость пружины, чтобы удар ощущался \"мягче\".",
    "os_lbl_physics_res_exp": "Кривая сопротивления",
    "os_desc_physics_res_exp": "Степень экспоненты для формулы сопротивления.\\n\\nРаботает только если Тяга &lt; 1.0. Задает крутизну нарастания усилия при оттягивании края.",
    # Visual Scales
    "os_mode_off": "Нет (Выкл)",
    "os_mode_shrink": "Сжатие",
    "os_mode_grow": "Растяжение",
    "os_lbl_scale_mode": "Режим деформации",
    "os_lbl_scale_int": "Интенсивность",
    "os_lbl_scale_limit": "Предел",
    "os_lbl_anchor_x": "Точка опоры X",
    "os_lbl_anchor_y": "Точка опоры Y",
    "os_group_visual_vert": "Вертикальная деформация",
    "os_desc_visual_vert": "Изменяет масштаб (ScaleY) содержимого RenderNode по вертикали.\\n\\n• Сжатие: сплющивает контент при ударе.\\n• Растяжение: удлиняет контент (эффект резины).",
    "os_group_visual_zoom": "Зум",
    "os_desc_visual_zoom": "Изменяет общий масштаб (ScaleX и ScaleY) всего контейнера.\\n\\nСоздает эффект приближения/удаления списка при взаимодействии с границей.",
    "os_group_visual_horz": "Горизонтальная деформация",
    "os_desc_visual_horz": "Изменяет масштаб (ScaleX) по ширине.\\n\\nПозволяет сделать эффект \"бочки\" или сужения списка при натяжении.",
    "misc_group_title": "Разное",
    "os_desc_scale_intensity": "Коэффициент влияния смещения на масштаб. Чем выше значение, тем сильнее искажение при том же сдвиге.",
    "os_desc_scale_limit": "Жесткий лимит искажения (мин/макс масштаб), чтобы предотвратить визуальные артефакты при сильных рывках.",
    "os_desc_scale_anchor": "Координата точки, вокруг которой происходит масштабирование (0.0 - 1.0). 0.5 - центр.",
    "os_lbl_min_vel": "Отсечка скорости",
    "os_desc_min_vel": "Порог скорости затухания пружины.\\n\\nОпределяет момент, когда вычисления физики принудительно останавливаются. Если скорость пружины падает ниже этого значения, анимация завершается. Помогает убрать микро-дрожание в конце.",
    "os_lbl_min_val": "Отсечка смещения",
    "os_desc_min_val": "Порог амплитуды затухания.\\n\\nЕсли остаточное смещение (в пикселях) становится меньше этого значения, анимация считается завершенной. Увеличение значения делает возврат в исходное состояние визуально более резким и быстрым.",
    # Anchor
    "os_lbl_invert_anchor": "Инверсия якоря",
    "os_desc_invert_anchor": "Автоматически зеркалит координаты якоря при взаимодействии с правой или нижней границей.\\n\\n• ВКЛ: Координата 0.0 всегда означает \"край, за который тянут\".\\n• ВЫКЛ: Координаты абсолютные (0.0 — всегда лево/верх, 1.0 — всегда право/низ), независимо от направления свайпа.",
    "os_lbl_anchor_x_horiz": "Якорь X (Горизонт.)",
    "os_desc_anchor_x_horiz": "Точка масштабирования по главной оси (вдоль скролла) для горизонтальных списков.\\n\\nОпределяет, в какой части экрана по ширине будет происходить основное искажение при натяжении.",
    "os_lbl_anchor_y_horiz": "Якорь Y (Горизонт.)",
    "os_desc_anchor_y_horiz": "Точка масштабирования по поперечной оси (высоте) для горизонтальных списков.\\n\\nПозволяет сместить центр эффекта вверх или вниз относительно центра списка.",
    "os_lbl_scale_int_horiz": "Интенсивность (Гориз.)",
    "os_desc_scale_int_horiz": "Определяет силу эффекта исключительно для горизонтальных списков (LazyRow, ViewPager). Позволяет настроить поведение отдельно от вертикальной прокрутки.",
    # Advanced
    "os_group_advanced": "Системные параметры",
    "os_lbl_input_smooth": "Фильтр ввода",
    "os_desc_input_smooth": "Low-pass фильтр для координат пальца.\\n\\nСглаживает шум сенсора перед передачей данных физической модели. Высокие значения делают реакцию \"вязкой\".",
    "os_lbl_anim_speed": "Скорость анимации (%)",
    "os_desc_anim_speed": "Глобальный множитель скорости анимации.\\n\\nПрименяется поверх всех физических и визуальных настроек.\\n100%% = стандартная скорость, ниже 100%% замедляет, выше 100%% ускоряет.",
    "os_lbl_lerp_idle": "Сглаживание: ПАЛЕЦ",
    "os_desc_lerp_idle": "Скорость интерполяции (LERP) при прямом взаимодействии.\\n\\nОпределяет, как быстро визуал догоняет палец, пока пружина НЕ запущена (выключена). Меньше = плавнее, но с задержкой.",
    "os_lbl_lerp_run": "Сглаживание: ПРУЖИНА",
    "os_desc_lerp_run": "Скорость интерполяции (LERP) при активной анимации.\\n\\nОпределяет плавность движения, когда работает физическая модель (пружина запущена).",
    "os_lbl_compose_scale": "Делитель Compose",
    "os_desc_compose_scale": "Коэффициент нормализации для Jetpack Compose.\\n\\nCompose передает дельты прокрутки в других единицах или суммирует их иначе. Этот делитель уменьшает входящие значения, чтобы привести физику в соответствие с View-системой.",
    # Apps
    "os_group_apps": "Настройки приложений",
    "os_btn_add_app": "Добавить приложение",
    "os_btn_cancel": "Отмена",
    "btn_cancel": "Отмена",
    "btn_restart_launcher": "Применить и перезапустить лаунчер",
    "os_app_filter": "Сглаживание",
    "os_app_ignore": "Отключить",
    "os_app_scale": "Индив. множитель",
    "os_app_select_title": "Выбор приложения",
    "os_app_search_hint": "Поиск по названию или пакету...",
    "os_card_example_text": "Пример контента для теста физики.",
    # Thermal
    "thermal_title": "Термальные профили",
    "thermal_info_title": "Принцип работы",
    "thermal_info_summary": "В отличие от чипов Qualcomm, управление нагревом в Google Tensor осуществляется не через прямые параметры ядра, а через файлы конфигурации (thermal_info_config.json).\\n\\nЭтот модуль работает на системном уровне: он изменяет специальное свойство, которое заставляет службу Thermal HAL загружать альтернативный файл конфигурации с другими порогами троттлинга.\\n\\nЭто безопасный, \"нативный\" метод регулировки, предусмотренный архитектурой Pixel. Для применения изменений требуется перезагрузка.",
    "thermal_sec_battery": "Аккумулятор",
    "thermal_sec_soc": "Процессор",
    "thermal_mode_stock": "Заводской",
    "thermal_mode_soft": "Мягкий (+5°C)",
    "thermal_mode_medium": "Средний (+9°C)",
    "thermal_mode_hard": "Агрессивный (+15°C)",
    "thermal_mode_off": "Экстрим (Выкл)",
    "thermal_desc_bat_stock": "Стандартные заводские лимиты. Обеспечивают максимальную безопасность и срок службы аккумулятора.",
    "thermal_desc_bat_soft": "Небольшое повышение порога (+5°C). Помогает избежать замедления зарядки при умеренном нагреве.",
    "thermal_desc_bat_medium": "Сбалансированный профиль (+9°C). Компромисс между скоростью зарядки и нагревом корпуса.",
    "thermal_desc_bat_hard": "ВНИМАНИЕ: Высокие лимиты (+15°C). Возможен ощутимый нагрев при быстрой зарядке. Ускоряет износ химии батареи.",
    "thermal_desc_bat_off": "ОПАСНО: Практически полное отключение термозащиты (+90°C). Высокий риск вздутия батареи и повреждения устройства.",
    "thermal_desc_soc_stock": "Стандартный алгоритм. Агрессивное снижение частот (троттлинг) для удержания низкой температуры корпуса.",
    "thermal_desc_soc_soft": "Мягкий профиль (+5°C). Позволяет процессору дольше удерживать высокие частоты под нагрузкой.",
    "thermal_desc_soc_medium": "Оптимальный для игр (+9°C). Значительно снижает частоту просадок FPS, корпус будет теплым.",
    "thermal_desc_soc_hard": "Максимальная производительность (+15°C). Устройство может стать горячим на ощупь. Рекомендуется только для кратковременных задач.",
    "thermal_desc_soc_off": "ЭКСТРЕМАЛЬНО: Отключение троттлинга. Процессор будет греться до аварийного отключения питания. Только для бенчмарков.",
    "thermal_dialog_reboot_title": "Нужна перезагрузка",
    "thermal_dialog_safe_msg": "Новый термальный профиль выбран.\\n\\nСлужба Thermal HAL считает новые настройки только при следующем запуске системы. Перезагрузить устройство сейчас?",
    "thermal_dialog_risk_msg": "ВНИМАНИЕ: Вы выбрали агрессивный температурный режим!\\n\\nЭто может привести к сильному нагреву корпуса и ускоренной деградации компонентов.\\n\\nДля применения настроек требуется перезагрузка.",
    "btn_ok": "Понятно",
    "btn_reboot": "Перезагрузить",
    "btn_confirm": "Подтвердить",
    # Reboot
    "reboot_launcher": "Перезапустить лаунчер",
    "reboot_systemui": "Перезапустить SystemUI",
    "reboot_system": "Перезагрузить систему",
    "reboot_confirm_system_title": "Перезагрузить устройство?",
    "reboot_confirm_system_msg": "Устройство будет перезагружено немедленно. Сохраните свою работу перед продолжением.",
    "reboot_confirm_sysui_title": "Перезапустить SystemUI?",
    "reboot_confirm_sysui_msg": "SystemUI будет перезапущен. Панель навигации, строка состояния и уведомления ненадолго исчезнут.",
    # IMS
    "ims_category_title": "Конфигурация IMS",
    "warning_info": "Эти настройки принудительно активируют возможности оператора (Carrier Config). По умолчанию большинство этих функций скрыто или отключено для несертифицированных регионов.\\n\\nДля применения изменений необходимо включить и выключить \"Режим полета\".",
    "ims_footer_info": "Эти настройки принудительно активируют возможности оператора (Carrier Config). По умолчанию большинство этих функций скрыто или отключено для несертифицированных регионов.\\n\\nДля применения изменений необходимо включить и выключить \"Режим полета\".",
    "ims_sec_voice": "Голосовые сервисы",
    "ims_sec_network": "Сеть и 5G",
    "ims_sec_advanced": "Расширенные настройки",
    "ims_lbl_volte": "VoLTE",
    "ims_desc_volte": "Принудительно включает поддержку VoLTE в конфигурации оператора.\\n\\nПозволяет совершать голосовые вызовы через сеть 4G/LTE без переключения в 3G/2G. Обеспечивает быстрое соединение, HD-качество звука и одновременную работу интернета во время разговора. Исправляет отсутствие переключателя VoLTE в настройках.",
    "ims_lbl_vowifi": "Звонки через Wi-Fi",
    "ims_desc_vowifi": "Принудительно включает поддержку звонков через Wi-Fi.\\n\\nПозволяет телефону регистрироваться в IMS-сети оператора через любой доступный Wi-Fi. Незаменимо в местах с плохим приемом сотовой сети (подвалы, толстые стены), но наличием интернета. Активирует скрытое меню \"Звонки по Wi-Fi\".",
    "ims_lbl_vt": "Видеовызовы",
    "ims_desc_vt": "Включает нативную поддержку видеозвонков через оператора.\\n\\nЭто встроенная в \"звонилку\" функция видеосвязи (не через мессенджеры, а через стандартный протокол IR.94). Работает, если оператор поддерживает Video over LTE.",
    "ims_lbl_cross_sim": "Резервные вызовы",
    "ims_desc_cross_sim": "Технология резервного вызова через другую SIM-карту.\\n\\nПозволяет использовать интернет-трафик второй SIM-карты для регистрации IMS-сервисов первой SIM-карты. Полезно, когда основная SIM-карта потеряла сеть (например, в роуминге или \"слепой зоне\"), а вторая SIM-карта имеет доступ к интернету.",
    "ims_lbl_vonr": "VoNR (Звонки через 5G)",
    "ims_desc_vonr": "Voice over New Radio. Голосовые вызовы в чистых сетях 5G.\\n\\nСамый современный стандарт передачи голоса. Обеспечивает сверхнизкую задержку и лучшее качество, чем VoLTE. Работает только в сетях 5G SA и требует Android 14+.",
    "ims_lbl_5g": "Разблокировка 5G",
    "ims_desc_5g": "Снимает программную блокировку 5G со стороны конфигурации.\\n\\nМногие операторы блокируют доступ к 5G для устройств Pixel, которых нет в их \"белом списке\", даже если аппаратная поддержка есть. Этот свитч принудительно добавляет диапазоны NR NSA и SA в список разрешенных.",
    "ims_lbl_5g_thresh": "Агрессивный сигнал 5G",
    "ims_desc_5g_thresh": "Изменяет пороги уровня сигнала для переключения сетей.\\n\\nЗанижает требования к качеству сигнала для отображения значка 5G и удержания в сети 5G. Помогает, если телефон слишком охотно сваливается в 4G/LTE, даже когда 5G доступен.",
    "ims_lbl_ut": "Интерфейс UT",
    "ims_desc_ut": "Управление дополнительными услугами через IMS.\\n\\nПозволяет настраивать переадресацию, ожидание вызова и АОН через меню настроек телефона, используя IP-канал вместо устаревших USSD-кодов. Исправляет ошибки типа \"Ошибка сети или SIM-карты\" при попытке войти в настройки вызовов в сетях LTE-only.",
    # Launcher
    "launcher_settings_title": "Настройки Лаунчера",
    "launcher_search_section": "Поиск и навигация",
    "launcher_group_general": "Основные",
    "launcher_search_feed_title": "Поиск и Лента",
    "launcher_search_feed_subtitle": "Настройка нативного поиска и ленты Google",
    "launcher_group_appearance": "Внешний вид",
    "launcher_grid_title": "Настройки сетки",
    "launcher_grid_subtitle": "Настройка строк и столбцов рабочего стола",
    "launcher_group_recents_features": "Функции Recents",
    "launcher_clear_all_nav_title": "Кнопка «Очистить всё»",
    "launcher_clear_all_nav_subtitle": "Настройка кнопки очистки всех приложений",
    "launcher_group_gestures": "Жесты",
    "launcher_gestures_title": "Жесты",
    "launcher_gestures_subtitle": "Двойной тап для сна и другое",
    "launcher_group_recents_customization": "Кастомизация Recents",
    "launcher_recents_menu_title": "Меню Recents",
    "launcher_recents_menu_subtitle": "Настройка карусели, масштаба, размытия и другого",
    "launcher_search_title": "Нативный поиск",
    "launcher_search_desc": "Принудительно включает встроенный интерфейс поиска в Pixel Launcher.\\n\\nGoogle часто меняет поведение поиска на \"серверной стороне\", заменяя быстрый локальный поиск на медленный запуск приложения Google. Эта опция фиксирует флаг 'device_config', возвращая быстрый поиск приложений и контактов.",
    # Donate
    "donate_progress_title": "Прогресс сбора средств",
    "menu_refresh": "Обновить",
    "donate_open_target": "Открыть страницу сбора",
    "donate_error_loading": "Ошибка получения данных",
    "donate_retry": "Повторить",
    "donate_title": "Поддержка и ссылки",
    "donate_desc_short": "GitHub, Telegram, Boosty",
    "donate_header": "Поддержать проект",
    "donate_desc_long": "Если вам нравится этот модуль и вы хотите поддержать его дальнейшую разработку, вы можете сделать это на Boosty.",
    "donate_btn_boosty": "Перейти на Boosty",
    "donate_links_header": "Полезные ссылки",
    "error_network": "Ошибка сети",
    "refresh_strings": "Строки страницы обновлены",
    "donate_page_updated": "Страница обновлена",
    "donate_page_updated_error": "Ошибка загрузки данных",
    # Launcher Clear All
    "launcher_clear_all_title": "Кнопка «Очистить всё»",
    "launcher_clear_all_desc": "Добавляет кнопку закрытия всех приложений в меню многозадачности (Recents).\\n\\nПоддерживает несколько режимов отображения для интеграции в стандартный интерфейс.",
    "launcher_clear_all_enable_title": "Включить кнопку «Очистить всё»",
    "launcher_ca_hide_actions_row_title": "Скрыть стандартные кнопки действий",
    "launcher_ca_mode_float": "Снизу по центру",
    "launcher_ca_mode_screenshot": "Заменить «Скриншот»",
    "launcher_ca_mode_select": "Заменить «Выбрать»",
    "launcher_ca_margin": "Позиция (Отступ снизу)",
    "launcher_btn_restart": "Перезапустить лаунчер",
    "launcher_ca_margin_desc": "Регулирует вертикальное положение плавающей кнопки. Значение — множитель стандартного отступа.",
    "launcher_ca_hide_actions_row_desc": "Скрывает системные кнопки «Скриншот» и «Выбрать» в Recents, оставляя общую логику контейнера и вашу кастомную кнопку «Очистить всё».",
    # DT
    "dt_category_title": "Жесты двойного тапа",
    "dt_sec_wake": "Пробуждение",
    "dt_sec_sleep": "Сон",
    "dt2w_title": "Пробуждение касанием",
    "dt2w_desc_xposed": "Xposed-хук для SystemUI.\\n\\nПерехватывает события сенсора в классе DozeTriggers и эмулирует пробуждение, так как аппаратная поддержка на Pixel часто отключена или ограничена.",
    "dt2w_desc_native": "Нативная системная интеграция.\\n\\nИспользует встроенный в прошивку код для обработки сенсорных событий в режиме Doze, обеспечивая пробуждение без сторонних хуков.",
    "dt2w_info_title": "Как это работает",
    "dt2w_info_desc": "Это программная эмуляция, а не аппаратная функция ядра.\\n\\n<b>Принцип работы:</b>\\nСистема держит сенсор активным в режиме AOD/Doze и анализирует касания.\\n\\n<b>Важно:</b>\\n• Из-за программной обработки возможна небольшая задержка.\\n• Может незначительно влиять на расход батареи (Deep Sleep).",
    "dt2w_timeout_title": "Интервал тапа",
    "dt2w_timeout_desc": "Максимальное время между двумя касаниями (в мс), чтобы они засчитались как двойной тап.",
    "dt2s_title": "Сон на рабочем столе",
    "dt2s_desc_xposed": "Xposed-хук для Pixel Launcher.\\n\\nПерехватывает метод 'onTouchEvent' в классе Workspace лаунчера, позволяя блокировать экран касанием по пустому месту.",
    "dt2s_desc_native": "Нативная инъекция кода.\\n\\nИспользует модифицированный фреймворк для регистрации 'ActivityLifecycleCallbacks' в процессе Launcher3. При запуске активити автоматически находится корневое View рабочего стола (Workspace) и к нему привязывается кастомный 'OnTouchListener'. Это работает без Xposed и не требует прав суперпользователя для самого лаунчера.",
    "dt2s_info_title": "Особенности DT2S",
    "dt2s_info_desc": "Позволяет выключить экран двойным тапом по пустой области рабочего стола.\\n\\n<b>Ограничения:</b>\\n• Работает только в Pixel Launcher.\\n• Не срабатывает на виджетах или иконках.\\n• Может конфликтовать со свайпами, если \"Допуск смещения\" настроен слишком агрессивно.",
    "dt2s_timeout_title": "Интервал тапа",
    "dt2s_slop_title": "Допуск смещения",
    "dt2s_slop_desc": "Максимальное расстояние (в пикселях), на которое может сдвинуться палец между нажатиями. Уменьшите, если тапы часто распознаются как свайпы.",
    # Components
    "common_range_format": "Диапазон: %1$d - %2$d",
    "common_input_label": "Введите значение",
    "common_video_placeholder": "Демонстрация видео",
    # Global
    "app_name": "Pixel Extra Parts",
    "btn_apply": "Применить",
    "btn_default": "По умолчанию",
    "btn_close": "Закрыть",
    "btn_exit": "Выход",
    "btn_restart": "Перезапуск",
    "nav_back": "Назад",
    "xposed_required_title": "Требуется Xposed",
    "xposed_required_message": "Для работы этой функции необходим активный модуль Xposed.",
    "status_active": "Активно",
    "status_disabled": "Отключено",
    "status_error": "Ошибка",
    # Main
    "main_title": "Pixel Extra Parts",
    "main_desc": "Кастомные модули и твики",
    "main_header_gesture": "Жесты и ввод",
    "main_header_system": "Система",
    "main_header_network": "Сеть и связь",
    "test_things_title": "Тестовые штуки",
    "test_things_desc": "Экспериментальные возможности в разработке",
    "main_root_title": "Нет Root доступа",
    "main_root_desc": "Для работы приложения необходимы права суперпользователя (Root) и активный Xposed Framework.\\n\\nПожалуйста, установите Magisk/KernelSU и предоставьте права.",
    "dt_desc_activity": "Управление жестами пробуждения и сна",
    "launcher_title_activity": "Лаунчер",
    "os_desc_activity": "Настройка упругости и инерции скролла",
    "dt_title_activity": "Двойной тап",
    "launcher_desc_activity": "Настройки Pixel Launcher",
    "thermal_title_activity": "Термальные профили",
    "thermal_desc_activity": "Управление троттлингом",
    "os_desc_anchor": "Точка привязки трансформации.",
    # Delta Normalization
    "os_group_norm": "Нормализация дельт Compose",
    "os_desc_norm_group": "Интеллектуальная система нормализации для прокрутки Jetpack Compose.\\n\\nCompose передаёт увеличенные дельты по сравнению с классической прокруткой View. Модуль автоматически определяет экземпляры Compose и плавно уменьшает их дельты до уровня стандартного View.\\n\\nОткалиброван эмпирически: дельты Compose примерно в 5 раз больше дельт View при одинаковых жестах.",
    "os_lbl_norm_enabled": "Включить нормализацию",
    "os_desc_norm_enabled": "Главный переключатель нормализации дельт.\\n\\nПри включении система анализирует каждый экземпляр EdgeEffect и применяет коррекцию к увеличенным дельтам Compose.\\n\\nБезопасно оставлять включённым — не влияет на приложения с нормальными дельтами.",
    "os_lbl_norm_ref_delta": "Эталонная дельта",
    "os_desc_norm_ref_delta": "Базовое «нормальное» значение дельты (доля от effectiveSize).\\n\\nОпределено эмпирически из View-приложений. Если скользящее среднее превышает это × Множитель, экземпляр считается усиленным.\\n\\nСтандарт: 0.00001 (очень чувствительно). Увеличьте, если бывают ложные срабатывания.",
    "os_lbl_norm_detect_mul": "Множитель обнаружения",
    "os_desc_norm_detect_mul": "Пороговый множитель для детектирования усиления.\\n\\nАктивация: скользящее_среднее &gt; эталонная_дельта × это.\\n3.3 = надёжно ловит Compose (~5× от View), не срабатывает на View.\\n\\nУвеличьте при ложных срабатываниях, уменьшите если не ловит Compose.",
    "os_lbl_norm_factor": "Корректирующий коэффициент",
    "os_desc_norm_factor": "Во сколько раз множить усиленные дельты.\\n\\n0.2 = привести к уровню View (из анализа: ref/compose ≈ 0.195).\\n0.5 = уменьшить вдвое. 1.0 = без изменений.\\n\\nМеньше = слабее отскок, больше = сильнее.",
    "os_lbl_norm_window": "Размер окна",
    "os_desc_norm_window": "Количество последних сэмплов |дельта| для анализа.\\n\\nМеньше = быстрее обнаружение, но чувствительнее к шуму.\\nБольше = стабильнее, но медленнее.\\n\\nДиапазон: 2–64.",
    "os_lbl_norm_ramp": "Событий разгона",
    "os_desc_norm_ramp": "Сколько событий для перехода от 1.0 к коэффициенту.\\n\\n1–4 = почти мгновенно. 8–12 = плавный переход.\\n\\nПредотвращает визуальные скачки при активации нормализации.",
    "os_lbl_norm_detect_mode": "Режим обнаружения",
    "os_desc_norm_detect_mode": "Как определяются экземпляры Compose.\\n\\n0 = Только по поведению (скользящее окно дельт)\\n1 = Гибрид: стек-трейс + окно для подтверждения\\n2 = Только стек-трейс (без анализа окна)\\n\\nГибрид (1) рекомендуется для лучшей точности и скорости.",
    "os_norm_mode_behavior": "Только поведение",
    "os_norm_mode_hybrid": "Гибрид (рекомендуется)",
    "os_norm_mode_stacktrace": "Только стек-трейс",
    "ims_desc_activity": "VoLTE, VoWiFi, 5G",
    "ims_title_activity": "IMS Конфиг",
    "launcher_top_widget_title": "Отключить верхний виджет",
    "launcher_top_widget_desc": "Убирает неотключаемый виджет \"Самое главное\" с первого экрана и позволяет размещать там иконки.",
    # Grid
    "grid_group_homepage": "Настройка главного экрана",
    "grid_lbl_home_enable": "Настройка сетки главного экрана",
    "grid_desc_home_enable": "Включает настройку столбцов и строк сетки главного экрана.",
    "grid_lbl_columns": "Столбцы",
    "grid_desc_home_cols": "Количество столбцов на главном экране.",
    "grid_lbl_rows": "Строки",
    "grid_desc_home_rows": "Количество строк на главном экране.",
    "grid_lbl_hide_text": "Скрыть текст иконок",
    "grid_desc_home_hide_text": "Скрывает подписи иконок на главном экране.",
    "grid_lbl_icon_size": "Размер иконок",
    "grid_desc_home_icon_size": "Настройка размера иконок на главном экране (100%% — стандарт).",
    "grid_desc_drawer_icon_size": "Настройка размера иконок в меню приложений (100%% — стандарт).",
    "grid_desc_dock_icon_size": "Настройка размера иконок в доке (100%% — стандарт).",
    "grid_lbl_text_mode": "Режим отображения текста",
    "grid_desc_text_mode": "Как отображаются подписи при видимом тексте.",
    "grid_text_mode_default": "Обрезка",
    "grid_text_mode_two_line": "Две строки",
    "grid_text_mode_marquee": "Бегущая строка",
    "grid_group_drawer": "Настройка меню приложений",
    "grid_lbl_drawer_enable": "Настройка меню приложений",
    "grid_desc_drawer_enable": "Включает настройку сетки и компоновки меню приложений.",
    "grid_lbl_drawer_cols": "Столбцы меню",
    "grid_desc_drawer_cols": "Количество столбцов в меню приложений.",
    "grid_lbl_row_height": "Высота строки",
    "grid_desc_row_height": "Настройка вертикального расстояния между строками в меню (100% — стандарт).",
    "grid_lbl_drawer_hide_text": "Скрыть текст меню",
    "grid_desc_drawer_hide_text": "Скрывает подписи иконок в меню приложений.",
    "grid_group_dock": "Настройка дока",
    "grid_lbl_dock_icons": "Количество иконок в доке",
    "grid_desc_dock_icons": "Количество иконок на панели дока.",
    "grid_lbl_dock_hide_text": "Скрыть текст дока",
    "grid_desc_dock_hide_text": "Скрывает подписи иконок в доке.",
    "grid_lbl_search_cols": "Столбцы поиска",
    "grid_desc_search_cols": "Количество столбцов для результатов поиска и подсказок в меню.",
    # Search Widget
    "search_widget_group_title": "Виджет поиска и Док",
    "search_widget_enable_dock": "Настройка дока",
    "search_widget_desc_dock": "Главный переключатель всех модификаций дока и строки поиска ниже.",
    "search_widget_hide_search": "Скрыть строку поиска",
    "search_widget_desc_hide_search": "Скрывает виджет поиска внизу.",
    "search_widget_hide_dock": "Скрыть иконки дока",
    "search_widget_desc_hide_dock": "Скрывает область иконок нижнего дока (hotseat).",
    "pref_recents_size_summary": "pref_recents_size_summary",
    "info_recents_size": "info_recents_size",
    "search_widget_padding_home": "Верхний отступ рабочего стола",
    "search_widget_desc_padding_home": "Настройка верхнего отступа для иконок (растяжение сверху).",
    "search_widget_padding_home_bottom": "Нижний отступ рабочего стола",
    "search_widget_desc_padding_home_bottom": "Настройка нижнего отступа для иконок (растяжение снизу).",
    "search_widget_padding_dock": "Отступ иконок дока",
    "search_widget_desc_padding_dock": "Настройка вертикальной позиции иконок дока (+/-).",
    "search_widget_padding_search": "Отступ виджета поиска",
    "search_widget_desc_padding_search": "Настройка вертикальной позиции виджета поиска (+/-).",
    "search_widget_padding_dots": "Отступ точек",
    "search_widget_desc_padding_dots": "Настройка вертикальной позиции индикатора страниц.",
    "search_widget_padding_dots_x": "Горизонтальное смещение точек",
    "search_widget_desc_padding_dots_x": "Настройка горизонтальной позиции индикатора страниц.",
    # Recents Size
    "title_recents_size": "Размер карточек Recents",
    "desc_recents_size": "Настройка размера карточек задач в обзоре",
    "pref_recents_size_enable": "Свой размер",
    "pref_recents_size_scale": "Масштаб (%)",
    "pref_recents_carousel_enable": "Эффект карусели (стиль OnePlus)",
    "pref_recents_icons_enable": "Строка иконок приложений",
    # Recents Menu
    "recents_menu_screen_title": "Меню Recents",
    "recents_group_general": "Общее управление",
    "recents_enable_modding": "Включить моддинг Recents",
    "recents_disable_live_tile": "Отключить живые плитки",
    "recents_disable_live_tile_desc": "Показывать снимок вместо живого превью",
    "recents_group_scaling": "Базовое масштабирование",
    "recents_enable_static_scale": "Статическое масштабирование",
    "recents_scale_percent": "Масштаб (%)",
    "recents_group_carousel_geometry": "Карусель: Геометрия",
    "recents_carousel_min_scale": "Мин. масштаб (край)",
    "recents_carousel_spacing": "Расстояние / Перекрытие",
    "recents_carousel_min_alpha": "Мин. прозрачность (край)",
    "recents_group_carousel_blur": "Карусель: Размытие",
    "recents_carousel_blur_radius": "Макс. радиус размытия",
    "recents_blur_overflow": "Размытие всей карточки",
    "recents_blur_overflow_desc": "Применить ко всей карточке, а не только к миниатюре",
    "recents_group_carousel_tint": "Карусель: Тонирование",
    "recents_tint_intensity": "Интенсивность тонировки",
    "recents_select_tint_color": "Выбрать цвет тонировки",
    "recents_group_icon_offset": "Карусель: Смещение иконок",
    "recents_icon_offset_x": "Смещение иконок X",
    "recents_icon_offset_y": "Смещение иконок Y",
    # Screen titles
    "gestures_screen_title": "Жесты",
    "clear_all_screen_title": "Кнопка «Очистить всё»",
    "grid_screen_title": "Настройки сетки",
    "grid_homepage_title": "Главный экран",
    "grid_homepage_dock_title": "Панель дока",
    "grid_menupage_title": "Меню приложений",
    "grid_menupage_enable": "Настройка меню приложений",
    "grid_menupage_search_title": "Результаты поиска",
    "grid_menupage_suggestions_title": "Список предложений",
    "grid_menupage_apps_title": "Список приложений",
    "grid_menupage_suggestions_disable": "Отключить список предложений",
    "grid_text_mode_hide": "Скрыть текст",
    "search_feed_screen_title": "Поиск и Лента",
    # Quickstep
    "quickstep_warning_title": "Экспериментальная функция",
    "quickstep_warning_text": "Эта функция полностью заменяет стандартный вид Quickstep на пользовательскую реализацию. Могут быть визуальные артефакты или сбои. Используйте на свой страх и риск. Отключите эту опцию при возникновении проблем.",
    "quickstep_engine_title": "Кастомный движок Quickstep",
    "quickstep_engine_summary": "Заменить стандартный Recents на пользовательскую реализацию",
    "quickstep_engine_info": "Включает полностью кастомный вид Recents с различными стилями и анимациями. Полностью заменяет стандартный Recents Pixel Launcher.",
    "quickstep_style_title": "Стиль Recents",
    "quickstep_style_stock": "Стандартный",
    "quickstep_style_stock_desc": "Классические горизонтальные карточки",
    "quickstep_style_ios": "Стиль iOS",
    "quickstep_style_ios_desc": "Вертикальные карточки стопкой с 3D-эффектом",
    "quickstep_style_oneplus": "Стиль OnePlus",
    "quickstep_style_oneplus_desc": "Карусель с фокусом на центральной карточке",
    "quickstep_style_minimal": "Минималистичный",
    "quickstep_style_minimal_desc": "Маленькие карточки только с иконками",
    "content_desc_selected": "Выбрано",
    "launcher_feed_title": "Отключить ленту Google",
    "launcher_feed_desc": "Отключает экран -1 (Google Discover/Лента) на главном экране.",
    # Animation Theme
    "anim_theme_create_title": "Создать тему анимации",
    "anim_theme_style_name": "Имя стиля",
    "anim_theme_open_enter": "Открытие — вход",
    "anim_theme_open_exit": "Открытие — выход",
    "anim_theme_close_enter": "Закрытие — вход",
    "anim_theme_close_exit": "Закрытие — выход",
    "anim_theme_tap_to_select": "Нажмите для выбора XML файла",
    "anim_theme_compile_btn": "Собрать и установить",
    "anim_theme_log_title": "Лог сборки",
    "anim_theme_installed_title": "Установленные темы",
    "anim_theme_none_installed": "Нет установленных тем анимации",
    "anim_theme_paste_xml": "Вставить XML",
    "anim_theme_xml_hint": "Вставьте XML анимации здесь…",
    "anim_theme_input_mode_file": "Файл",
    "anim_theme_input_mode_xml": "XML текст",
    # Built-in Animations
    "anim_builtin_title": "Встроенные анимации",
    "anim_builtin_disabled": "Отключено",
    "anim_builtin_custom": "Пользовательская тема",
    "anim_preview_open": "Открытие",
    "anim_preview_close": "Закрытие",
    "anim_apply_both": "Применить оба",
    "anim_apply_open": "Применить открытие",
    "anim_apply_close": "Применить закрытие",
    "anim_current_open": "Текущее открытие: %s",
    "anim_current_close": "Текущее закрытие: %s",
    # Anim groups
    "anim_group_slide": "Сдвиг",
    "anim_group_card_stack": "Стопка карт",
    "anim_group_train": "Поезд",
    "anim_group_ios": "iOS Параллакс",
    "anim_group_fade": "Затухание",
    "anim_group_zoom": "Зум",
    "anim_group_modal": "Модальное",
    "anim_group_depth": "Глубина",
    "anim_group_pivot": "Поворот",
    # Directions
    "anim_dir_right": "Вправо",
    "anim_dir_left": "Влево",
    "anim_dir_top": "Вверх",
    "anim_dir_bottom": "Вниз",
    # Transitions
    "anim_transition_title": "Анимации переходов",
    "anim_transition_subtitle": "Пользовательские темы и встроенные переходы",
    "anim_predictive_back_title": "Отключить предиктивный жест «назад»",
    "anim_predictive_back_desc": "Убирает системную анимацию свайпа назад",
    "anim_no_animation_title": "Без анимации",
    "anim_no_animation_desc": "Полностью отключает анимацию переходов",
    "anim_constructor_title": "Конструктор анимаций",
    "anim_constructor_preview": "Предпросмотр",
    "anim_constructor_style_name": "Имя стиля",
    "anim_constructor_build_theme": "Собрать тему",
    "anim_constructor_hide": "Скрыть",
    "anim_constructor_show_xml": "Показать XML",
    "anim_constructor_hide_xml": "Скрыть XML",
    "anim_dialog_cancel": "Отмена",
    "anim_active_open_close": "Открытие + Закрытие",
    "anim_installing": "Установка…",
    "anim_install_success": "✓ Тема успешно установлена!",
    "anim_install_failed": "Ошибка установки",
    "anim_install_failed_log": "✗ Ошибка установки",
    "anim_compile_failed": "Ошибка компиляции",
    "anim_cd_delete": "Удалить",
    "anim_cd_confirm_delete": "Подтвердить удаление",
    "anim_cd_reset": "Сбросить",
    "anim_cd_copy": "Копировать",
    "anim_param_translate": "Сдвиг",
    "anim_param_scale": "Масштаб",
    "anim_param_alpha": "Прозрачность",
    "anim_param_rotate": "Вращение",
    "anim_param_pivot": "Точка опоры",
    "anim_param_timing": "Тайминг",
    "anim_param_interpolator": "Интерполятор",
    # Addon Manager
    "addon_title": "Аддоны Pine",
    "addon_desc": "Управление внешними модулями хуков",
    "addon_count": "Модулей установлено: %d",
    "addon_active_count": "Активно: %1$d из %2$d",
    "addon_safe_mode_title": "Безопасный режим",
    "addon_safe_mode_desc": "Аддоны были отключены из-за повторяющихся сбоев. Если проблема вызвана неисправным аддоном, вы можете отключить его перед выходом из безопасного режима.",
    "addon_safe_mode_exit": "Выйти из безопасного режима",
    "addon_section_system": "Системные модули",
    "addon_section_user": "Установленные модули",
    "addon_system_empty_title": "Системные модули отсутствуют",
    "addon_system_empty_desc": "Системные модули поставляются с прошивкой",
    "addon_user_empty_title": "Пользовательские модули не установлены",
    "addon_user_empty_desc": "Импортируйте .jar аддон для начала",
    "addon_import": "Импортировать",
    "addon_import_desc": "Выберите .jar файл аддона",
    "addon_import_success": "Аддон импортирован",
    "addon_import_failed": "Ошибка импорта",
    "addon_empty_title": "Аддоны не установлены",
    "addon_empty_desc": "Импортируйте .jar аддон для начала",
    "addon_delete_title": "Удалить аддон",
    "addon_delete_confirm": "Удалить \"%s\"?\\n\\nJAR-файл будет удалён и все настройки сброшены.",
    "addon_scope_default": "Целевые по умолчанию",
    "addon_scope_custom": "Только пользовательские",
    "addon_scope_merge": "Объединить оба",
    "addon_scope_label_default": "Область по умолчанию",
    "addon_scope_label_custom": "Пользовательская область",
    "addon_scope_label_merge": "Объединённая область",
    "addon_target_scope": "Область действия",
    "addon_targets_default": "Цели по умолчанию",
    "addon_targets_custom": "Пользовательские цели",
    "addon_builtin_badge": "встроенный",
    "addon_builtin_plus_addon": "Встроенный + Аддон",
    "addon_enabled": "Включён",
    "addon_btn_save": "Сохранить",
    "addon_btn_cancel": "Отмена",
    "addon_btn_remove": "Удалить",
    "addon_settings_title": "Настройки модуля",
    "addon_range_format": "Диапазон: %1$s — %2$s",
    "addon_file_not_set": "Не указано",
    "addon_file_saved": "Файл сохранён",
    "addon_file_copy_failed": "Ошибка копирования файла",
    "addon_apps_selected": "Выбрано приложений: %d",
    "addon_select_apps": "Выбрать приложения",
    "addon_selected_count": "Выбрано: %d",
    "addon_confirm_count": "Подтвердить (%d)",
    "addon_search_apps": "Поиск приложений\\u2026",
    "addon_show_system": "Показать системные",
    "addon_hide_system": "Скрыть системные",
    "addon_no_apps_match": "Нет приложений по запросу \"%s\"",
    "addon_no_apps_available": "Нет доступных приложений",
    "addon_default_target_badge": "Цель по умолчанию",
    "addon_author_unknown": "Неизвестный",
    "addon_active_apps_title": "Активные приложения",
    "addon_active_apps_desc": "%d приложение(й) с активными хуками",
    "addon_active_apps_empty": "Нет активных хуков",
    "addon_active_apps_modules": "%d модуль(ей)",
}

# ─── Now load the second part with DE/FR/IN/UK translations ────────
# This is in a separate file to keep this one manageable
exec(open(os.path.join(BASE, "gen_translations_locales.py"), encoding="utf-8").read())

# ─── MAIN ──────────────────────────────────────────────────────────

def main():
    en_path = os.path.join(RES, "values", "strings.xml")
    en_strings, en_content = parse_strings_xml(en_path)
    print(f"EN: {len(en_strings)} strings loaded")

    all_trans = {"ru": RU, "de": DE, "fr": FR, "in": IN, "uk": UK}

    # Write XML for each locale
    for lang in LOCALES:
        trans = all_trans[lang]
        # Build complete translations: for each EN key, use locale translation if available, else EN
        complete = OrderedDict()
        for key in en_strings:
            if key in trans:
                complete[key] = trans[key]
            else:
                complete[key] = en_strings[key]
        
        out_path = os.path.join(RES, f"values-{lang}", "strings.xml")
        write_strings_xml(out_path, list(en_strings.keys()), en_content, complete, lang)

    # Write JSON for all languages including EN
    os.makedirs(LANG, exist_ok=True)
    # EN JSON
    write_json(os.path.join(LANG, "strings_en.json"), dict(en_strings))
    for lang in LOCALES:
        trans = all_trans[lang]
        complete = OrderedDict()
        for key in en_strings:
            complete[key] = trans.get(key, en_strings[key])
        write_json(os.path.join(LANG, f"strings_{lang}.json"), dict(complete))

    print("\nDone! All locale files generated.")

if __name__ == "__main__":
    main()
