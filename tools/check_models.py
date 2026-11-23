#!/usr/bin/env python3
"""Kiem tra bang model may shared/device/models/*.csv: du cot, so hop le, bo phim co that, khong trung model.
Chay: python3 tools/check_models.py"""
import csv
import os
import sys

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "shared", "device", "models")
COLS = ["vendor", "model", "platform", "edition", "year", "width", "height", "orientation", "touch", "qwerty", "midp", "cldc",
        "jsr", "heap_kb", "jar_kb", "key_profile", "gfx_tier", "confidence", "note"]
err = []
keys = {r["id"] for r in csv.DictReader(open(os.path.join(D, "key_profiles.csv"), encoding="utf-8"))}
tiers = {r["tier"] for r in csv.DictReader(open(os.path.join(D, "gfx_tiers.csv"), encoding="utf-8"))}
rd = csv.DictReader(open(os.path.join(D, "models.csv"), encoding="utf-8"))
if rd.fieldnames != COLS:
    err.append("sai cot: %s" % rd.fieldnames)
seen = set()
n = 0
java = 0
for i, r in enumerate(rd, 2):
    n += 1
    k = (r["vendor"], r["model"])
    if k in seen:
        err.append("dong %d: trung model %s" % (i, k))
    seen.add(k)
    for c in ("year", "width", "height", "touch", "qwerty"):
        if r[c] and not r[c].isdigit():
            err.append("dong %d: %s khong phai so: %r" % (i, c, r[c]))
    for c in ("heap_kb", "jar_kb"):
        if r[c] and r[c] != "inf" and not r[c].isdigit():
            err.append("dong %d: %s phai la so KB hoac inf: %r" % (i, c, r[c]))
    if r["orientation"] not in ("D", "N", "V"):
        err.append("dong %d: huong man phai la D/N/V" % i)
    if r["confidence"] not in ("A", "B", "C"):
        err.append("dong %d: muc tin cay phai la A/B/C" % i)
    if r["midp"]:  # may chay duoc J2ME
        java += 1
        if not all(p in keys for p in r["key_profile"].split("+")):
            err.append("dong %d: bo phim la %r" % (i, r["key_profile"]))
        if r["gfx_tier"] not in tiers:
            err.append("dong %d: muc do hoa la %r" % (i, r["gfx_tier"]))
        if not (r["width"] and r["height"]) and "chua ro" not in r["note"]:
            err.append("dong %d: thieu do phan giai" % i)
if err:
    print("\n".join(err))
    sys.exit(1)
print("Bang model: %d dong (%d may chay J2ME), %d bo phim, %d muc do hoa - hop le" % (n, java, len(keys), len(tiers)))
