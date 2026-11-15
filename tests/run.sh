#!/usr/bin/env bash
# Chay thu toan bo: dong goi -> may chu gia lap GitHub -> client PC va J2ME.
set -euo pipefail
T="$(cd "$(dirname "$0")" && pwd)"; KIT="$(cd "$T/.." && pwd)"
W="$(mktemp -d)"; PORT=18765
export JAVA_TOOL_OPTIONS=""
cp -r "$KIT/games" "$W/games"; cp -r "$KIT/tools" "$W/tools"
python3 "$W/tools/build_packs.py" --games-dir "$W/games" --out "$W/dist" >/dev/null
python3 "$T/server.py" "$W/dist/demo" $PORT & SRV=$!
trap 'kill $SRV 2>/dev/null; rm -rf "$W"' EXIT
sleep 1
mkdir -p "$W/out-pc" "$W/out-me" "$W/me-data/root1"   # goc the nho luon co san tren may that
echo "== bien dich client Android/PC (cu phap Java 7/8)"
javac -nowarn --release 8 -Xlint:-options -d "$W/out-pc" "$KIT/client/android-pc/nekoyuki/assets/NekoAssets.java" "$T/TestPC.java"
echo "== bien dich client J2ME voi API gia lap"
javac -nowarn --release 8 -Xlint:-options -d "$W/out-me" $(find "$T/shim" -name '*.java') "$KIT"/client/j2me/nekoyuki/assets/*.java "$T/TestME.java" 2>&1 | grep -v "^Note:" || true
SRC="$W/games/demo/packs"
echo "== SHA-256 thuan Java"; java -cp "$W/out-me" TestME sha x x
echo "== PC, lan 1"; java -cp "$W/out-pc" TestPC 1 "http://127.0.0.1:$PORT" "$SRC" "$W/pc-data"
echo "== J2ME, lan 1"; java -Dshim.root="$W/me-data" -cp "$W/out-me" TestME 1 "http://127.0.0.1:$PORT" "$SRC"
echo "== phat hanh ban cap nhat cho chapter1"
printf 'BAN CAP NHAT\nNoi dung moi cua chuong 1.\n' > "$W/games/demo/packs/chapter1/script.txt"
python3 "$W/tools/build_packs.py" --games-dir "$W/games" --out "$W/dist" >/dev/null
echo "== PC, lan 2"; java -cp "$W/out-pc" TestPC 2 "http://127.0.0.1:$PORT" "$SRC" "$W/pc-data"
echo "== J2ME, lan 2"; java -Dshim.root="$W/me-data" -cp "$W/out-me" TestME 2 "http://127.0.0.1:$PORT" "$SRC"
echo "TAT CA DEU DAT"
