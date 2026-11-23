#!/usr/bin/env python3
"""Cai cac module dung chung (NekoDevice, NekoGuard) vao ma nguon mot game.

    python3 tools/install_modules.py <thu_muc_src> [ten_package]

Vi du: python3 tools/install_modules.py ../FNaF/src
       python3 tools/install_modules.py ../DDLC/src/ddlc ddlc
Chay lai bat ky luc nao de cap nhat ban moi nhat trong kho.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MODULES = [os.path.join(HERE, "..", "shared", "device", "NekoDevice.java"),
           os.path.join(HERE, "..", "shared", "guard", "NekoGuard.java")]


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    dest, pkg = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
    if not os.path.isdir(dest):
        sys.exit("Khong thay thu muc: %s" % dest)
    for src in MODULES:
        code = open(src, encoding="utf-8").read()
        if pkg:
            code = "package %s;\n\n%s" % (pkg, code)
        out = os.path.join(dest, os.path.basename(src))
        open(out, "w", encoding="utf-8", newline="\n").write(code)
        print("Da cai:", out)


if __name__ == "__main__":
    main()
