#!/usr/bin/env bash
# Kiem thu NekoGuard: ky mot bo tai nguyen mau bang tools/sign_assets.py roi chay TestGuard (co chup phong khoa).
# Chay: bash tests/run_guard.sh [thu_muc_luu_anh_chup]
set -euo pipefail
T="$(cd "$(dirname "$0")" && pwd)"; KIT="$(cd "$T/.." && pwd)"
SHOTS="${1:-$(mktemp -d)}"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
export JAVA_TOOL_OPTIONS=""
mkdir -p "$W/res/img" "$W/stubs" "$W/cls"
# bo tai nguyen mau: anh cua Neko Yuki (chua dau -> tool nhung dau), tai nguyen game goc, credits
python3 - "$KIT/shared/guard/art/nekoyuki_halfbody_240.png" "$W/res/img/nekoyuki_halfbody_240.png" <<'EOF'
import sys
from PIL import Image
Image.open(sys.argv[1]).save(sys.argv[2])            # luu lai khong kem dau, de tool tu nhung
EOF
head -c 5000 /dev/urandom > "$W/res/game.dat"
printf 'Chuyen the: Neko Yuki\nGame goc: Team Salvato\n' > "$W/res/credits.txt"
python3 "$KIT/tools/sign_assets.py" --res "$W/res" --game demo --java "$W/gen/NekoGuardData.java" --owned "nekoyuki*,credits*" --stamp
javac -nowarn -encoding UTF-8 --release 8 -Xlint:-options -d "$W/stubs" $(find "$T/stubs" -name '*.java') 2>&1 | grep -v "Picked up\|^Note:" || true
javac -nowarn -encoding UTF-8 --release 8 -Xlint:-options -cp "$W/stubs" -d "$W/cls" "$KIT/shared/guard/NekoGuard.java" "$W/gen/NekoGuardData.java" "$T/TestGuard.java" 2>&1 | grep -v "Picked up\|^Note:" || true
java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$W/stubs:$W/cls" TestGuard "$W/res" "$SHOTS" "$KIT/shared/guard/art" 2>&1 | grep -v "Picked up"
echo "Anh chup phong khoa: $SHOTS"
