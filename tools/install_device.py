#!/usr/bin/env python3
"""Cai module NekoDevice vao ma nguon mot game.

    python3 tools/install_device.py <thu_muc_src_cua_game> [ten_package]

Vi du:
    python3 tools/install_device.py ../FNaF/src              # lop khong package (FNaF)
    python3 tools/install_device.py ../DDLC/src/ddlc ddlc    # package ddlc (DDLC)

Chep shared/device/NekoDevice.java vao thu muc do, them dong "package ...;" neu can.
Chay lai bat ky luc nao de cap nhat len ban moi nhat trong kho.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "shared", "device", "NekoDevice.java")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    dest_dir = sys.argv[1]
    pkg = sys.argv[2] if len(sys.argv) > 2 else ""
    if not os.path.isdir(dest_dir):
        sys.exit("Khong thay thu muc: %s" % dest_dir)
    with open(SRC, encoding="utf-8") as f:
        code = f.read()
    if pkg:
        code = "package %s;\n\n%s" % (pkg, code)
    out = os.path.join(dest_dir, "NekoDevice.java")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(code)
    print("Da cai:", out)


if __name__ == "__main__":
    main()
