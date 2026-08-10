#!/usr/bin/env bash
#
# Проверка приложения на подключённом устройстве.
#
# Собирает всё, что можно снять автоматически, и по шагам подсказывает
# действия, которые может сделать только человек (сказать фразу в микрофон,
# нажать кнопку в уведомлении). Итог — один файл, который можно переслать.
#
# Запуск из корня проекта:
#   ./scripts/device-check.sh            # собрать, поставить, проверить
#   ./scripts/device-check.sh --no-build # только проверить уже установленное
#
set -u

PKG=com.zhukoffsky.magpie
REPORT=magpie-device-report.txt
BUILD=1
[ "${1:-}" = "--no-build" ] && BUILD=0

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }
log() { printf '\n===== %s =====\n' "$*" >>"$REPORT"; }
run() { printf '$ %s\n' "$*" >>"$REPORT"; "$@" >>"$REPORT" 2>&1; }
pause() { printf '\n>>> %s\n    Сделай это на телефоне и нажми Enter... ' "$*"; read -r _; }

# adb ставится вместе с Android Studio и в PATH сам не попадает: студия
# зовёт его по полному пути. Ищем в стандартных местах, прежде чем сдаваться.
if ! command -v adb >/dev/null; then
    for candidate in \
        "${ANDROID_HOME:-}/platform-tools" \
        "${ANDROID_SDK_ROOT:-}/platform-tools" \
        "$HOME/Library/Android/sdk/platform-tools" \
        "$HOME/Android/Sdk/platform-tools"
    do
        if [ -x "$candidate/adb" ]; then
            PATH="$candidate:$PATH"
            break
        fi
    done
fi

command -v adb >/dev/null || {
    echo "adb не найден ни в PATH, ни в стандартных каталогах Android SDK."
    echo "Укажи путь вручную: export PATH=\"\$HOME/Library/Android/sdk/platform-tools:\$PATH\""
    exit 1
}

: >"$REPORT"
log "device"
run adb devices -l
run adb shell getprop ro.product.model
run adb shell getprop ro.build.version.release
run adb shell getprop ro.build.version.sdk

if ! adb shell true >/dev/null 2>&1; then
    echo "Устройство не отвечает. Проверь отладку по USB и подтверждение на телефоне."
    exit 1
fi

if [ "$BUILD" = 1 ]; then
    say "Собираю и ставлю APK"
    ./gradlew assembleDebug --console=plain || exit 1
    adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
fi

say "Чищу лог и запускаю приложение"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3

# --- Разрешения и системное состояние -------------------------------------
log "permissions"
run adb shell cmd appops get "$PKG" SCHEDULE_EXACT_ALARM
run adb shell cmd appops get "$PKG" POST_NOTIFICATION
run adb shell dumpsys deviceidle whitelist
log "notification listeners"
adb shell dumpsys notification_manager 2>/dev/null | grep -i -A 4 "$PKG" >>"$REPORT" 2>&1

pause "Разреши уведомления, если система спросит. Открой вкладку «Настройки» и посмотри, что горит красным"
log "settings tab reported by user"

# --- Голосовой ввод покупок -----------------------------------------------
say "Проверка 1 из 4: список покупок голосом"
adb shell am start -a com.zhukoffsky.magpie.action.CAPTURE_SHOPPING \
    -n "$PKG/.core.voice.VoiceCaptureActivity" >/dev/null 2>&1
pause "Скажи: «молоко, хлеб и яйца». ЗАПОМНИ, сколько полей появилось в карточке, и сохрани"
log "voice shopping"
run adb logcat -d -s Magpie:V AndroidRuntime:E

# --- Голосовой ввод напоминания -------------------------------------------
say "Проверка 2 из 4: напоминание голосом"
adb logcat -c
adb shell am start -a com.zhukoffsky.magpie.action.CAPTURE_REMINDER \
    -n "$PKG/.core.voice.VoiceCaptureActivity" >/dev/null 2>&1
pause "Скажи: «через две минуты позвонить маме» и сохрани"
log "voice reminder"
run adb logcat -d -s Magpie:V AndroidRuntime:E
log "alarms after reminder"
adb shell dumpsys alarm 2>/dev/null | grep -i -B 2 -A 8 "$PKG" >>"$REPORT" 2>&1

# --- Доставка в Doze -------------------------------------------------------
say "Проверка 3 из 4: срабатывание в режиме сна"
adb logcat -c
adb shell dumpsys battery unplug >/dev/null 2>&1
adb shell dumpsys deviceidle force-idle >/dev/null 2>&1
echo "Телефон переведён в Doze. Жду срабатывания напоминания (до 3 минут)..."
sleep 150
log "doze delivery"
run adb logcat -d -s Magpie:V AndroidRuntime:E
adb shell dumpsys deviceidle unforce >/dev/null 2>&1
adb shell dumpsys battery reset >/dev/null 2>&1
say "Doze выключен, батарея возвращена в обычный режим"

# --- Перезагрузка ----------------------------------------------------------
say "Проверка 4 из 4: восстановление будильников после перезагрузки"
printf '>>> Перезагрузить телефон сейчас? [y/N] '
read -r answer
if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
    adb reboot
    echo "Жду возвращения устройства..."
    adb wait-for-device
    sleep 45
    log "after reboot"
    run adb logcat -d -s Magpie:V AndroidRuntime:E
    log "alarms after reboot"
    adb shell dumpsys alarm 2>/dev/null | grep -i -B 2 -A 8 "$PKG" >>"$REPORT" 2>&1
fi

say "Готово. Отчёт: $REPORT"
echo "Пришли этот файл и допиши своими словами:"
echo "  1. Сколько полей было в карточке покупок (три или одно)?"
echo "  2. Что горело красным на вкладке «Настройки»?"
echo "  3. Пришло ли уведомление в режиме сна?"
