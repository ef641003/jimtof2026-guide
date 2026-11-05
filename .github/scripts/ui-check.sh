#!/usr/bin/env bash
# エミュレーターでアプリを起動し、各タブのスクリーンショットとクラッシュの有無を記録する
# 折りたたみ（ふつうの幅）と、広げた画面（横幅 600dp 以上）の両方を撮る
set -u
OUT=ui-check
PKG=jp.fairguide.jimtof2026
ACT=$PKG/.MainActivity
APK=app/build/outputs/apk/release/app-release.apk
mkdir -p "$OUT"
rm -f "$OUT"/*.png

adb install -r "$APK" > "$OUT/install.txt" 2>&1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

read -r W H < <(adb shell wm size | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
echo "screen ${W}x${H}" > "$OUT/screen.txt"

shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/$1.png"; }
swipe_up() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; }
start() { adb shell am start -W -n $ACT "$@" > /dev/null 2>&1; }

# 初回：担当を選ぶ画面
start
shot 00-profile-chooser 6

# ---- 立旋盤メーカー視点 ----
start --es profile maker --es open_tab report
shot 10-report-maker 5
swipe_up
shot 10b-report-maker-scrolled 2
swipe_up
shot 10c-report-maker-company 2
start --es map_booth E1001
shot 20-map-maker-E1001 5
swipe_up
shot 20b-map-maker-list 2
start --es open_tab map
shot 21-map-maker 3

# ---- 西展示棟担当 ----
start --es profile west --es open_tab report
shot 30-report-west 5
start --es map_booth W1065
shot 31-map-west-W1065 5
start --es map_booth E7085
shot 32-map-west-E7085 4
start --es report_preset CUTTING
shot 33-preset-cutting 4

# 検索・予定・ブース・会期
start --es search_query mazak
shot 40-search-mazak 4
start --es search_query makimo
shot 41-search-fuzzy 3
start --es open_tab schedule --es event_date 2026-10-27
shot 50-schedule 3
start --es open_tab mylist --es report_add w1
shot 60-mylist-checklist 4
swipe_up
shot 60b-mylist-checklist2 2
start --es open_tab overview
shot 70-overview 3
swipe_up
shot 70b-overview-scrolled 2
start --es open_tab report --es report_item route
shot 80-route 5

# ---- 広げた画面（フォルダブルの内側の画面に近い大きさ） ----
adb shell wm size 1920x2160
adb shell wm density 400
sleep 3
start --es open_tab map
shot 90-wide-map 5
start --es map_booth W2055
shot 91-wide-map-W2055 5
start --es open_tab report --es report_item w1
shot 92-wide-report 4
start --es open_tab mylist
shot 93-wide-mylist 3
adb shell wm size reset
adb shell wm density reset

adb shell cmd uimode night yes
start --es open_tab map
shot 95-map-dark 4
adb shell cmd uimode night no

adb logcat -d > "$OUT/logcat.txt"
grep -E "FATAL EXCEPTION|AndroidRuntime" "$OUT/logcat.txt" > "$OUT/crashes.txt" || echo "no crashes" > "$OUT/crashes.txt"
adb shell pidof $PKG > "$OUT/pid.txt" || echo "not running" > "$OUT/pid.txt"
exit 0
