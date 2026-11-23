#!/usr/bin/env python3
"""Ky tai nguyen game cho NekoGuard (chay luc build, tren BAN SAO thu muc tai nguyen).

    python3 tools/sign_assets.py --res <thu_muc_tai_nguyen> --game <ten> --java <duong_dan/NekoGuardData.java>
                                 [--package ddlc] [--owned "nekoyuki*,credits*,lang_*.txt"] [--stamp]

Viec lam:
  1. --stamp: tai nguyen cua Neko Yuki (khop --owned) duoc nhung dau ban quyen:
       PNG: them doan tEXt "Copyright: Neko Yuki" (anh van mo binh thuong);
       file chu (.txt .json .csv .dat ...): bat buoc da co chu "Neko Yuki", khong thi bao loi.
     Tai nguyen cua game goc (khong khop --owned) KHONG ghi la ban quyen Neko Yuki; chung chi duoc ky la
     "ban build chinh thuc cua Neko Yuki" trong danh sach.
  2. Ghi <res>/neko.sig: moi dong "ten|kich_thuoc|16 ky tu dau SHA-256|o hoac b" (o = cua Neko Yuki).
  3. Ghi NekoGuardData.java: SIG = SHA-256 cua neko.sig (game so khi mo), GAME, BUILD.

Game: NekoGuard.loadManifest(getResourceAsStream("/neko.sig"), NekoGuardData.SIG), roi checkFile(ten, du_lieu)
khi doc tai nguyen.
"""
import argparse
import datetime
import fnmatch
import hashlib
import os
import struct
import sys
import zlib

MARK = b"Neko Yuki"
TEXT_EXT = (".txt", ".json", ".csv", ".dat", ".xml", ".properties", ".lang")


def png_has_mark(data):
    return MARK in data


def png_stamp(data):
    """Them 2 doan tEXt (Copyright, Author) truoc IEND. Tra ve du lieu moi."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("khong phai PNG")
    end = data.rfind(b"IEND")
    if end < 4:
        raise ValueError("PNG thieu IEND")
    pos = end - 4  # truoc truong do dai cua IEND
    chunks = b""
    for key, val in ((b"Copyright", b"Neko Yuki"), (b"Author", b"Neko Yuki")):
        body = key + b"\x00" + val
        chunks += struct.pack(">I", len(body)) + b"tEXt" + body + struct.pack(">I", zlib.crc32(b"tEXt" + body) & 0xFFFFFFFF)
    return data[:pos] + chunks + data[pos:]


def main():
    ap = argparse.ArgumentParser(description="Ky tai nguyen game cho NekoGuard")
    ap.add_argument("--res", required=True)
    ap.add_argument("--game", required=True)
    ap.add_argument("--java", required=True, help="duong dan file NekoGuardData.java se ghi")
    ap.add_argument("--package", default="")
    ap.add_argument("--owned", default="nekoyuki*", help="mau ten file (cach nhau dau phay) la tai nguyen cua Neko Yuki")
    ap.add_argument("--stamp", action="store_true", help="nhung dau ban quyen vao PNG cua Neko Yuki (sua file tai cho)")
    a = ap.parse_args()

    res = os.path.abspath(a.res)
    if not os.path.isdir(res):
        sys.exit("Khong thay thu muc: " + res)
    owned_pats = [p.strip() for p in a.owned.split(",") if p.strip()]
    lines = []
    n_owned = n_stamped = 0
    errors = []
    for root, dirs, files in os.walk(res):
        dirs.sort()
        for fn in sorted(files):
            full = os.path.join(root, fn)
            rel = "/" + os.path.relpath(full, res).replace(os.sep, "/")
            if rel == "/neko.sig" or "|" in rel:
                continue
            owned = any(fnmatch.fnmatch(fn, p) or fnmatch.fnmatch(rel[1:], p) for p in owned_pats)
            data = open(full, "rb").read()
            if owned:
                n_owned += 1
                low = fn.lower()
                if low.endswith(".png"):
                    if not png_has_mark(data):
                        if a.stamp:
                            data = png_stamp(data)
                            open(full, "wb").write(data)
                            n_stamped += 1
                        else:
                            errors.append("%s chua co dau ban quyen (chay voi --stamp)" % rel)
                elif low.endswith(TEXT_EXT):
                    if MARK not in data:
                        errors.append("%s la tai nguyen cua Neko Yuki nhung khong co chu 'Neko Yuki'" % rel)
            sha = hashlib.sha256(data).hexdigest()
            lines.append("%s|%d|%s|%s" % (rel, len(data), sha[:16], "o" if owned else "b"))
    if errors:
        sys.exit("LOI:\n  " + "\n  ".join(errors))

    build = "Neko Yuki official build %s" % datetime.date.today().isoformat()
    sig = ("NEKO-SIG|1|%s|%d|%s\n" % (a.game, len(lines), build) + "\n".join(lines) + "\n").encode("utf-8")
    open(os.path.join(res, "neko.sig"), "wb").write(sig)
    digest = hashlib.sha256(sig).hexdigest()

    pkg = "package %s;\n\n" % a.package if a.package else ""
    java = ("%s/** Sinh boi tools/sign_assets.py luc build - KHONG sua tay. */\n"
            "final class NekoGuardData {\n"
            "    static final String SIG = \"%s\";\n"
            "    static final String GAME = \"%s\";\n"
            "    static final String BUILD = \"%s\";\n"
            "}\n") % (pkg, digest, a.game, build)
    os.makedirs(os.path.dirname(os.path.abspath(a.java)), exist_ok=True)
    open(a.java, "w", encoding="utf-8", newline="\n").write(java)
    print("Da ky %d tai nguyen (%d cua Neko Yuki, %d vua nhung dau) | neko.sig %s" % (len(lines), n_owned, n_stamped, digest[:16]))


if __name__ == "__main__":
    main()
