import os
import re

def fix_android_xml_apostrophes(content):
    """
    Ищет содержимое внутри тегов <string>...</string> и экранирует
    неэкранированные одинарные кавычки (апострофы).
    """
    
    def escape_apostrophe(match):
        full_tag = match.group(0)  # Полный тег: <string ...>Текст</string>
        tag_open = match.group(1) # <string name="xxx">
        text = match.group(2)     # Текст внутри
        tag_close = match.group(3)# </string>
        
        # Экранируем апостроф ('), только если перед ним НЕТ обратного слэша (\)
        # Используем negative lookbehind (?<!\\)
        fixed_text = re.sub(r"(?<!\\)'", r"\'", text)
        
        return f"{tag_open}{fixed_text}{tag_close}"

    # Регулярка для поиска тегов <string>. 
    # Группа 1: Открывающий тег, Группа 2: Контент, Группа 3: Закрывающий тег
    pattern = re.compile(r'(<string[^>]*>)(.*?)(</string>)', re.DOTALL)
    
    return pattern.sub(escape_apostrophe, content)

def main():
    target_dir = input("Введите путь к папке (например, device/google/PixelExtraParts): ").strip()
    
    if not os.path.isdir(target_dir):
        print("Ошибка: Указанный путь не является директорией.")
        return

    count = 0
    for root, _, files in os.walk(target_dir):
        for file in files:
            if file.endswith("json"):
                file_path = os.path.join(root, file)
                
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        original_content = f.read()
                    
                    fixed_content = fix_android_xml_apostrophes(original_content)
                    
                    if original_content != fixed_content:
                        with open(file_path, 'w', encoding='utf-8') as f:
                            f.write(fixed_content)
                        print(f"[ИСПРАВЛЕНО] {file_path}")
                        count += 1
                except Exception as e:
                    print(f"[ОШИБКА] Не удалось обработать {file_path}: {e}")

    print(f"\nГотово! Исправлено файлов: {count}")

if __name__ == "__main__":
    main()