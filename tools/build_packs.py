#!/usr/bin/env python3
"""Dong goi tai nguyen game thanh cac goi tai theo yeu cau.

Dau vao:
    games/<game>/packs/<pack>/...            (cac file tai nguyen goc)

Dau ra:
    dist/<game>/<game>-<pack>-<hash8>.zip    (Android / PC)
    dist/<game>/<game>-<pack>-<hash8>.npk    (J2ME)
    dist/<game>/manifest.txt                 (danh sach goi + SHA-256)

Ten file co chua 8 ky tu dau cua ma hash, nen moi lan noi dung thay doi
se ra mot file moi. Game luon doi chieu SHA-256 trong manifest truoc khi
dung, goi nao bi sua se bi tu choi.

Cach chay:
    python3 tools/build_packs.py --out dist
"""

import argparse
import hashlib
import os
import re
import struct
import sys
import zipfile

ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]*$")
SKIP_NAMES = {".gitkeep", ".ds_store", "thumbs.db", "desktop.ini"}
# Dinh dang da nen san: luu nguyen, khong nen lai (doc nhanh hon, khong ton CPU).
STORED_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".mp3", ".ogg", ".m4a",
              ".aac", ".amr", ".mp4", ".3gp", ".webm", ".zip", ".jar"}
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
WARN_SIZE = 100 * 1024 * 1024          # canh bao goi qua nang voi mang di dong
MAX_SIZE = 2 * 1024 * 1024 * 1024 - 1  # gioi han 1 file tren GitHub Releases


def fail(msg):
    print("LOI: " + msg, file=sys.stderr)
    sys.exit(1)


def list_files(pack_dir):
    """Tra ve [(ten_trong_goi, duong_dan)] da sap xep, bo qua file an."""
    out = []
    for root, dirs, files in os.walk(pack_dir):
        dirs[:] = sorted(d for d in dirs if not d.startswith("."))
        for name in sorted(files):
            if name.startswith(".") or name.lower() in SKIP_NAMES:
                continue
            full = os.path.join(root, name)
            rel = os.path.relpath(full, pack_dir).replace(os.sep, "/")
            if any(ord(c) > 0xFFFF or ord(c) < 0x20 for c in rel):
                fail("Ten file co ky tu khong ho tro: %r" % rel)
            if len(rel.encode("utf-8")) > 60000:
                fail("Ten file qua dai: %r" % rel)
            out.append((rel, full))
    out.sort(key=lambda item: item[0])
    return out


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_zip(files, out_path):
    """Zip co tinh lap lai: cung noi dung -> cung ma hash."""
    with zipfile.ZipFile(out_path, "w") as z:
        for rel, full in files:
            ext = os.path.splitext(rel)[1].lower()
            info = zipfile.ZipInfo(rel, date_time=FIXED_TIME)
            info.external_attr = 0o644 << 16
            info.create_system = 3
            if ext in STORED_EXT:
                info.compress_type = zipfile.ZIP_STORED
                level = None
            else:
                info.compress_type = zipfile.ZIP_DEFLATED
                level = 9
            with open(full, "rb") as f:
                z.writestr(info, f.read(), compresslevel=level)


def build_npk(files, out_path):
    """Dinh dang NPK cho J2ME (khong can giai nen).

    "NPK1" | so_file (int) | [ten (2 byte do dai + UTF-8) | kich_thuoc (int)]* | du lieu noi tiep
    Tat ca so la big-endian, doc duoc bang DataInputStream.
    """
    with open(out_path, "wb") as out:
        out.write(b"NPK1")
        out.write(struct.pack(">i", len(files)))
        for rel, full in files:
            name = rel.encode("utf-8")
            size = os.path.getsize(full)
            if size > 0x7FFFFFFF:
                fail("File qua lon cho NPK: %s" % rel)
            out.write(struct.pack(">H", len(name)))
            out.write(name)
            out.write(struct.pack(">i", size))
        for rel, full in files:
            with open(full, "rb") as f:
                for chunk in iter(lambda: f.read(1 << 20), b""):
                    out.write(chunk)


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return ("%d %s" % (n, unit)) if unit == "B" else ("%.1f %s" % (n, unit))
        n /= 1024.0


def build_game(game, game_dir, out_root):
    packs_dir = os.path.join(game_dir, "packs")
    if not os.path.isdir(packs_dir):
        return None
    packs = sorted(d for d in os.listdir(packs_dir)
                   if os.path.isdir(os.path.join(packs_dir, d)) and not d.startswith("."))
    lines = []
    out_dir = os.path.join(out_root, game)
    if os.path.isdir(out_dir):  # don sach ket qua lan truoc
        for old in os.listdir(out_dir):
            if old == "manifest.txt" or old.endswith((".zip", ".npk")):
                os.remove(os.path.join(out_dir, old))
    built = 0
    for pack in packs:
        if not ID_RE.match(pack):
            fail("Ten goi '%s' (game '%s') khong hop le. Chi dung chu thuong, so, '-' va '_'." % (pack, game))
        files = list_files(os.path.join(packs_dir, pack))
        if not files:
            print("  bo qua goi rong: %s/%s" % (game, pack))
            continue
        os.makedirs(out_dir, exist_ok=True)
        for kind, builder in (("zip", build_zip), ("npk", build_npk)):
            tmp = os.path.join(out_dir, "tmp." + kind)
            builder(files, tmp)
            digest = sha256_file(tmp)
            size = os.path.getsize(tmp)
            if size > MAX_SIZE:
                fail("Goi %s/%s nang %s, vuot gioi han 2 GB cua GitHub. Hay chia nho." % (game, pack, human(size)))
            if size > WARN_SIZE:
                print("  CANH BAO: goi %s/%s nang %s, nen chia nho hon." % (game, pack, human(size)))
            fname = "%s-%s-%s.%s" % (game, pack, digest[:8], kind)
            os.replace(tmp, os.path.join(out_dir, fname))
            lines.append("pack|%s|%s|%d|%s|%s" % (pack, kind, size, digest, fname))
            print("  %-40s %10s  %s" % (fname, human(size), digest[:16]))
        built += 1
    if not built:
        return None
    manifest = "NEKO-MANIFEST|1|%s|%d\n%s\n" % (game, built, "\n".join(lines))
    with open(os.path.join(out_dir, "manifest.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(manifest)
    return built


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description="Dong goi tai nguyen game.")
    ap.add_argument("--games-dir", default=os.path.join(here, "..", "games"))
    ap.add_argument("--out", default=os.path.join(here, "..", "dist"))
    ap.add_argument("--only", help="chi dong goi mot game")
    args = ap.parse_args()

    games_dir = os.path.abspath(args.games_dir)
    if not os.path.isdir(games_dir):
        fail("Khong thay thu muc games: %s" % games_dir)
    total = 0
    for game in sorted(os.listdir(games_dir)):
        game_dir = os.path.join(games_dir, game)
        if not os.path.isdir(game_dir) or game.startswith("."):
            continue
        if args.only and game != args.only:
            continue
        if not ID_RE.match(game):
            fail("Ten game '%s' khong hop le. Chi dung chu thuong, so, '-' va '_'." % game)
        print("[%s]" % game)
        built = build_game(game, game_dir, os.path.abspath(args.out))
        if built:
            total += built
        else:
            print("  (chua co goi nao)")
    print("Xong: %d goi." % total)


if __name__ == "__main__":
    main()
