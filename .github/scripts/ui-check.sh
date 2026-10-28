#!/usr/bin/env bash
# エミュレーターでアプリを起動し、各タブのスクリーンショットとクラッシュの有無を記録する
set -u
OUT=ui-check
PKG=jp.fairguide.jimtof2026
ACT=$PKG/.MainActivity
APK=app/build/outputs/apk/release/app-release.apk
mkdir -p "$OUT"

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

read -r W H < <(adb shell wm size | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
echo "screen ${W}x${H}" > "$OUT/screen.txt"

shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/$1.png"; }
swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; }

adb shell am start -W -n $ACT > "$OUT/start.txt" 2>&1
shot 01-overview 6
swipe_up
shot 02-overview-scrolled 2
swipe_up
shot 03-overview-bottom 2

adb shell am start -W -n $ACT --es open_tab halls
shot 04-halls 4
swipe_up
shot 04b-halls-scrolled 2
adb shell am start -W -n $ACT --es search_query mazak
shot 04c-search-mazak 4
adb shell am start -W -n $ACT --es search_query E2019
shot 04d-search-booth 3
adb shell am start -W -n $ACT --es search_query okuma
shot 04e-search-okuma 3
adb shell am start -W -n $ACT --es novelty_only 1
shot 04f-novelty-only 4
swipe_up
shot 04g-novelty-only-scrolled 2
adb shell am start -W -n $ACT --es report_preset COMPETITOR
shot 04h-preset-competitor 4
adb shell am start -W -n $ACT --es report_preset AUTOMATION
shot 04i-preset-automation 4
adb shell am start -W -n $ACT --es open_tab report
shot 10-report 5
swipe_up
shot 10b-report-scrolled 2
swipe_up
shot 10f-report-company 2
swipe_up
shot 10g-report-company2 2
swipe_up
shot 10h-report-company3 2
adb shell am start -W -n $ACT --es open_tab report --es report_item 2
shot 10c-report-item2 4
adb shell am start -W -n $ACT --es open_tab report --es report_item route
shot 10r-report-route 5
swipe_up
shot 10s-report-route-2 2
swipe_up
shot 10t-report-route-3 2
swipe_up
shot 10u-report-route-4 2
adb shell am start -W -n $ACT --es open_tab report --es report_item 5
shot 10d-report-item5 4
swipe_up
shot 10e-report-item5-scrolled 2
adb shell am start -W -n $ACT --es open_tab links
shot 11-links 3
adb shell am start -W -n $ACT --es open_tab schedule --es event_date 2026-10-27
shot 05-schedule-1027 3
swipe_up
shot 05b-schedule-1027-scrolled 2
adb shell am start -W -n $ACT --es open_tab mylist
shot 06-mylist 3
adb shell am start -W -n $ACT --es open_tab mylist --es report_add 1
shot 06b-mylist-checklist 4
swipe_up
shot 06c-mylist-checklist2 2
adb shell am start -W -n $ACT --es open_tab links
shot 07-links 3

adb shell cmd uimode night yes
shot 08-links-dark 4
adb shell am start -W -n $ACT --es open_tab overview
shot 09-overview-dark 3
adb shell cmd uimode night no

adb logcat -d > "$OUT/logcat.txt"
grep -E "FATAL EXCEPTION|AndroidRuntime" "$OUT/logcat.txt" > "$OUT/crashes.txt" || echo "no crashes" > "$OUT/crashes.txt"
adb shell pidof $PKG > "$OUT/pid.txt" || echo "not running" > "$OUT/pid.txt"
exit 0
