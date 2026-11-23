#!/bin/bash
# v2.6 Player APK: setup -> title -> New game -> name typed with the phone keyboard -> first lines
set -x
ADB=adb
PKG=com.nekoyuki.ddlc
mkdir -p out
$ADB install -r -g test.apk > out/install.txt 2>&1
$ADB logcat -c
$ADB shell am start -n $PKG/com.nekoyuki.ddlc.MainActivity
shot() { sleep $2; $ADB exec-out screencap -p > out/$1.png; }
W=$($ADB shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); PW=${W%x*}; PH=${W#*x}; if [ $PW -lt $PH ]; then T=$PW; PW=$PH; PH=$T; fi
L() { echo $(( $1 * PH / 720 )); }                   # logical (720 high) -> pixels
shot 01_setup 20
$ADB shell input tap $((PW/2)) $(L 426); sleep 10    # Bắt đầu
for i in 1 2 3 4 5 6; do $ADB shell input tap $((PW*3/4)) $((PH/2)); sleep 3; done
shot 02_title 10
LW=$(( PW * 720 / PH )); SX=$(( (LW - 1280) / 2 ))
$ADB shell input tap $(( (SX + 260) * PH / 720 )) $(L 490); shot 03_name 4
$ADB shell input text "Linh"; shot 04_typed 3
$ADB shell input keyevent 66; shot 05_after 8
for i in 1 2 3; do $ADB shell input tap $((PW/2)) $((PH/2)); sleep 3; done
shot 06_game 3
$ADB shell dumpsys activity activities | grep -i "mResumedActivity\|topResumed" > out/activity.txt || true
$ADB shell dumpsys meminfo $PKG | head -40 > out/meminfo.txt || true
$ADB logcat -d > out/logcat.txt
grep -iE "AndroidRuntime|FATAL|Exception|System.err|OutOfMemory|nekoyuki" out/logcat.txt | head -400 > out/errors.txt || true
exit 0
