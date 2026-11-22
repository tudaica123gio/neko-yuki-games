#!/bin/bash
# runs inside the emulator-runner: install, start, screenshots, logcat
set -x
ADB=adb
mkdir -p out
$ADB shell settings put global window_animation_scale 0 || true
$ADB install -r -g test.apk > out/install.txt 2>&1
$ADB logcat -c
$ADB shell am start -n com.nekoyuki.ddlc.dev/com.nekoyuki.ddlc.MainActivity
shot() { sleep $2; $ADB exec-out screencap -p > out/$1.png; }
shot 01_start 20
shot 02_later 15
$ADB shell wm size > out/size.txt
for i in 1 2 3; do $ADB shell input tap 1200 540; sleep 4; done
shot 03_tapped 3
$ADB shell input keyevent 4; shot 04_back 4
$ADB shell input tap 1200 540; shot 05_tap 5
for i in 1 2 3 4 5 6; do $ADB shell input tap 1200 540; sleep 3; done
shot 06_more 2
$ADB shell dumpsys activity activities | grep -i "mResumedActivity\|topResumed" > out/activity.txt || true
$ADB shell dumpsys meminfo com.nekoyuki.ddlc.dev | head -40 > out/meminfo.txt || true
$ADB logcat -d > out/logcat.txt
grep -iE "AndroidRuntime|FATAL|Exception|System.err|ddlc|OutOfMemory" out/logcat.txt | head -400 > out/errors.txt || true
exit 0
