set -x
mkdir -p out
adb shell getprop ro.build.version.release > out/android.txt
adb install -r test.apk > out/install.txt 2>&1; cat out/install.txt
adb shell pm list packages | grep nekoyuki > out/pkg.txt || true
adb logcat -c || true
adb shell am start -n com.nekoyuki.ddlc/com.nekoyuki.ddlc.MainActivity > out/start.txt 2>&1
sleep 45
adb exec-out screencap -p > out/01_start.png || true
adb shell input keyevent 66; sleep 6; adb shell input keyevent 66; sleep 6; adb shell input keyevent 66; sleep 10
adb exec-out screencap -p > out/02_later.png || true
adb shell dumpsys activity activities | grep -iE "mResumedActivity|topResumed|mFocusedActivity" > out/activity.txt || true
adb logcat -d > out/logcat.txt || true
grep -iE "AndroidRuntime|FATAL|VerifyError|NoSuchMethod|NoClassDef|ddlc|nekoyuki" out/logcat.txt | head -300 > out/errors.txt || true
echo "=== RESULT android=$(cat out/android.txt) install=$(tail -1 out/install.txt) pkg=$(cat out/pkg.txt)"
cat out/activity.txt
echo "=== ERRORS"; grep -iE "FATAL|AndroidRuntime|VerifyError|NoSuchMethod|NoClassDef|Exception" out/errors.txt | head -40
ls -la out
exit 0
