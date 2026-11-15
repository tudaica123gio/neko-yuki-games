#!/usr/bin/env bash
# Dua cac goi trong dist/ len GitHub Releases. Moi game co mot ban phat hanh
# rieng voi the "assets-<game>". Chay tu dong boi GitHub Actions.
#
# Thu tu an toan: tai goi moi len truoc, manifest sau cung, roi moi xoa goi cu.
# Nhu vay game khong bao gio thay manifest tro toi goi chua ton tai.
set -euo pipefail

DIST="${1:-dist}"
[ -d "$DIST" ] || { echo "Khong co thu muc $DIST"; exit 0; }

for dir in "$DIST"/*/; do
  [ -f "$dir/manifest.txt" ] || continue
  game="$(basename "$dir")"
  tag="assets-$game"
  echo "== $game -> $tag"

  if ! gh release view "$tag" >/dev/null 2>&1; then
    gh release create "$tag" --latest=false \
      --title "Tai nguyen: $game" \
      --notes "Goi tai nguyen tai theo yeu cau cho game '$game'. Duoc tao tu dong, khong sua tay."
  fi

  existing="$(gh release view "$tag" --json assets --jq '.assets[].name')"

  # 1) Goi moi (ten file co ma hash nen file trung ten la trung noi dung).
  for f in "$dir"*; do
    name="$(basename "$f")"
    [ "$name" = "manifest.txt" ] && continue
    if grep -qxF "$name" <<<"$existing"; then
      echo "   da co: $name"
    else
      echo "   tai len: $name"
      gh release upload "$tag" "$f"
    fi
  done

  # 2) Manifest sau cung, va chi khi co thay doi.
  current="$(gh release download "$tag" -p manifest.txt -O - 2>/dev/null || true)"
  if [ "$current" = "$(cat "$dir/manifest.txt")" ]; then
    echo "   manifest khong doi"
  else
    gh release upload "$tag" "$dir/manifest.txt" --clobber
  fi

  # 3) Xoa goi cu khong con trong manifest.
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    [ "$name" = "manifest.txt" ] && continue
    if [ ! -f "$dir/$name" ]; then
      echo "   xoa goi cu: $name"
      gh release delete-asset "$tag" "$name" --yes
    fi
  done <<<"$existing"
done
