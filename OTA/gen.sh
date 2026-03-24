#!/bin/bash
# Скрипт для автоматической генерации OTA JSON файла для прошивок

set -e

if [ -z "$1" ]; then
    echo "Использование: ./gen_ota_json.sh <путь_к_zip_архиву>"
    echo "Пример: ./gen_ota_json.sh out/target/product/shiba/EvolutionX-16.0-20251215-shiba-11.6.1-Official.zip"
    exit 1
fi

ZIP_FILE="$1"

if [ ! -f "$ZIP_FILE" ]; then
    echo "Ошибка: Файл '$ZIP_FILE' не найден!"
    exit 1
fi

# ==========================================
# Настройки по умолчанию (можно менять под себя)
# ==========================================
MAINTAINER="LeeGarChat"
OEM="Google"
BUILDTYPE="user"
FORUM="https://t.me/EvoX_LeeGarChat"
PAYPAL="https://boosty.to/leegar/"
GITHUB="leegarchat"

# Заглушка, которую нужно будет заменить в самом JSON
DOWNLOAD_URL="INSERT_DOWNLOAD_LINK_HERE"
# ==========================================

echo "Обработка файла: $ZIP_FILE"

# Получаем имя файла
FILENAME=$(basename "$ZIP_FILE")

# Автоопределение кодового имени и версии из имени файла
# Ожидаемый формат: EvolutionX-16.0-20251215-shiba-11.6.1-Official.zip
CODENAME=$(echo "$FILENAME" | cut -d'-' -f4)
VERSION=$(echo "$FILENAME" | cut -d'-' -f5)

# Конвертируем кодовое имя в коммерческое название устройства
case "$CODENAME" in
    "shiba") DEVICE="Pixel 8" ;;
    "husky") DEVICE="Pixel 8 Pro" ;;
    "akita") DEVICE="Pixel 8a" ;;
    "panther") DEVICE="Pixel 7" ;;
    "cheetah") DEVICE="Pixel 7 Pro" ;;
    "lynx") DEVICE="Pixel 7a" ;;
    *) DEVICE="$CODENAME" ;; # Если устройство неизвестно, оставляем кодовое имя
esac

echo "[*] Автоопределение из имени файла:"
echo "    -> Устройство: $DEVICE ($CODENAME)"
echo "    -> Версия EvoX: $VERSION"

# Получаем размер файла в байтах
echo "[1/4] Вычисляю размер файла..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # Для macOS
    FILE_SIZE=$(stat -f%z "$ZIP_FILE")
else
    # Для Linux/WSL
    FILE_SIZE=$(stat -c%s "$ZIP_FILE")
fi

# Вычисляем MD5
echo "[2/4] Вычисляю MD5 (это может занять минуту)..."
MD5=$(md5sum "$ZIP_FILE" | awk '{print $1}')

# Вычисляем SHA256
echo "[3/4] Вычисляю SHA-256 (это может занять минуту)..."
SHA256=$(sha256sum "$ZIP_FILE" | awk '{print $1}')

# Получаем текущий Unix timestamp
echo "[4/4] Получаю timestamp..."
TIMESTAMP=$(date +%s)

OUTPUT_FILE="ota_${FILENAME}.json"

echo "Генерирую JSON файл -> $OUTPUT_FILE"

# Записываем JSON в файл (форматированный вывод)
cat <<EOF > "$OUTPUT_FILE"
{
  "response": [
    {
      "maintainer": "$MAINTAINER",
      "currently_maintained": true,
      "oem": "$OEM",
      "device": "$DEVICE",
      "filename": "$FILENAME",
      "download": "$DOWNLOAD_URL",
      "timestamp": $TIMESTAMP,
      "md5": "$MD5",
      "sha256": "$SHA256",
      "size": $FILE_SIZE,
      "version": "$VERSION",
      "buildtype": "$BUILDTYPE",
      "forum": "$FORUM",
      "firmware": "",
      "paypal": "$PAYPAL",
      "github": "$GITHUB",
      "initial_installation_images": [
        "boot",
        "dtbo",
        "vendor_kernel_boot",
        "vendor_boot"
      ],
      "extra_images": [
        "init_boot"
      ]
    }
  ]
}
EOF

echo "=========================================="
echo "✅ Готово! Файл успешно сохранен как: $OUTPUT_FILE"
echo "⚠️  Не забудь открыть этот файл и заменить '$DOWNLOAD_URL' на реальную ссылку!"
echo "=========================================="