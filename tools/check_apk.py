#!/usr/bin/env python3
"""Kiem tra file APK co cai duoc tren moi may Android khong (tim nguyen nhan loi
"Da xay ra su co khi phan tich cu phap goi" / "There was a problem parsing the package" va "Chua cai dat ung dung").

    python3 tools/check_apk.py <file.apk> [file2.apk ...]

Kiem tra:
  - file nguyen ven (khong bi cat khi tai / gui qua Zalo, Messenger...), dung la APK (khong phai AAB / APKS / XAPK / ZIP)
  - AndroidManifest.xml: minSdk, targetSdk, android:exported cho Activity co intent-filter (Android 12+),
    testOnly, debuggable, extractNativeLibs
  - classes.dex: phien ban dex hop voi minSdk (dex 038/039 khong chay tren Android cu)
  - resources.arsc: phai luu khong nen va can le 4 byte (bat buoc khi targetSdk >= 30, Android 11+)
  - zipalign: moi file luu khong nen can le 4 byte; thu vien .so can le trang 4 KB / 16 KB
  - chu ky: v1 (Android 5-6), v2 / v3 (Android 7+, bat buoc khi targetSdk >= 30)
  - thu vien native: du kieu chip (armeabi-v7a, arm64-v8a; x86 / x86_64 cho gia lap)
Thoat ma 1 neu co loi chan cai dat.
"""
import struct
import sys
import zipfile

ANDROID_NAME = {5: "5.0", 6: "5.0", 21: "5.0", 22: "5.1", 23: "6", 24: "7", 25: "7.1", 26: "8", 27: "8.1", 28: "9",
                29: "10", 30: "11", 31: "12", 32: "12L", 33: "13", 34: "14", 35: "15", 36: "16"}
ATTR_ID = {0x0101020c: "minSdkVersion", 0x01010270: "targetSdkVersion", 0x01010010: "exported",
           0x01010272: "testOnly", 0x0101000f: "debuggable", 0x010104ea: "extractNativeLibs", 0x01010003: "name"}


def andro(api):
    return "Android %s" % ANDROID_NAME.get(api, "?") if api else "?"


# ---------------------------------------------------------------- AndroidManifest.xml dang nhi phan (AXML)
def parse_axml(data):
    """Tra ve danh sach (ten the, {ten thuoc tinh: gia tri}, do sau) theo thu tu trong file."""
    if len(data) < 8 or struct.unpack_from("<H", data, 0)[0] != 0x0003:
        raise ValueError("AndroidManifest.xml khong phai dang nhi phan cua Android")
    strings, resmap, out = [], [], []
    depth = 0
    pos = struct.unpack_from("<H", data, 2)[0]
    while pos + 8 <= len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if csize < 8:
            break
        if ctype == 0x0001:  # string pool
            count, _styles, flags, sstart, _ = struct.unpack_from("<IIIII", data, pos + 8)
            utf8 = flags & 0x100
            offs = struct.unpack_from("<%dI" % count, data, pos + hsize)
            base = pos + sstart
            for o in offs:
                p = base + o
                if utf8:
                    n = data[p]
                    p += 2 if n & 0x80 else 1
                    n = data[p]
                    if n & 0x80:
                        n = ((n & 0x7F) << 8) | data[p + 1]
                        p += 2
                    else:
                        p += 1
                    strings.append(data[p:p + n].decode("utf-8", "replace"))
                else:
                    n = struct.unpack_from("<H", data, p)[0]
                    p += 2
                    if n & 0x8000:
                        n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                        p += 2
                    strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
        elif ctype == 0x0180:  # resource map
            resmap = list(struct.unpack_from("<%dI" % ((csize - hsize) // 4), data, pos + hsize))
        elif ctype == 0x0102:  # start element
            _ns, name, astart, asize, acount = struct.unpack_from("<IIHHH", data, pos + 16)
            attrs = {}
            for i in range(acount):
                a = pos + 16 + astart + i * asize
                _ans, aname, raw, _sz, _r0, dtype, val = struct.unpack_from("<IIIHBBI", data, a)
                key = strings[aname] if aname < len(strings) and strings[aname] else ""
                if aname < len(resmap) and resmap[aname] in ATTR_ID:
                    key = ATTR_ID[resmap[aname]]
                if dtype == 0x03 and raw != 0xFFFFFFFF:
                    v = strings[raw] if raw < len(strings) else ""
                elif dtype == 0x12:
                    v = val != 0
                elif dtype in (0x10, 0x11):
                    v = val
                elif raw != 0xFFFFFFFF and raw < len(strings):
                    v = strings[raw]
                else:
                    v = val
                attrs[key] = v
            out.append((strings[name] if name < len(strings) else "?", attrs, depth))
            depth += 1
        elif ctype == 0x0103:  # end element
            depth -= 1
        pos += csize
    return out


# ---------------------------------------------------------------- khoi chu ky APK (v2 / v3)
def signing_schemes(raw):
    """Doc 'APK Signing Block' truoc thu muc trung tam: tra ve tap {'v2','v3','v3.1'}."""
    eocd = raw.rfind(b"PK\x05\x06", max(0, len(raw) - 65557))
    if eocd < 0:
        return None
    cd_off = struct.unpack_from("<I", raw, eocd + 16)[0]
    if cd_off < 32 or raw[cd_off - 16:cd_off] != b"APK Sig Block 42":
        return set()
    size = struct.unpack_from("<Q", raw, cd_off - 24)[0]
    start = cd_off - size - 8
    p, end, found = start + 8, cd_off - 24, set()
    while p + 12 <= end:
        ln, pid = struct.unpack_from("<QI", raw, p)
        found.add({0x7109871a: "v2", 0xf05368c0: "v3", 0x1b93ad61: "v3.1"}.get(pid, ""))
        p += 8 + ln
    found.discard("")
    return found


def data_offset(raw, info):
    """Vi tri du lieu that cua mot muc trong file (doc tu local header)."""
    h = info.header_offset
    nlen, elen = struct.unpack_from("<HH", raw, h + 26)
    return h + 30 + nlen + elen


# ---------------------------------------------------------------- kiem tra
def check(path):
    errs, warns, info = [], [], []
    raw = open(path, "rb").read()
    print("== %s (%.1f MB)" % (path, len(raw) / 1e6))
    if raw[:4] != b"PK\x03\x04":
        errs.append("File khong phai APK/ZIP (bi hong, hoac la file khac doi duoi .apk).")
        return report(errs, warns, info)
    try:
        z = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        errs.append("File bi cat ngan hoac hong (thieu phan cuoi ZIP): tai / gui lai file APK goc, khong qua app nen anh / tin nhan.")
        return report(errs, warns, info)
    names = set(z.namelist())
    if "BundleConfig.pb" in names or any(n.startswith("base/manifest/") for n in names):
        errs.append("Day la file AAB (goi cho Google Play), khong cai truc tiep duoc. Can xuat APK.")
        return report(errs, warns, info)
    inner = [n for n in names if n.lower().endswith(".apk")]
    if "AndroidManifest.xml" not in names:
        if len(inner) == 1:
            errs.append("Day la file ZIP chua APK '%s': giai nen file APK ra roi moi cai (bam vao APK ben trong ZIP se bao loi phan tich goi)." % inner[0])
        elif inner:
            errs.append("Day la goi nhieu APK (APKS / XAPK, %d APK): cai bang app SAI hoac tach APK chinh ra." % len(inner))
        else:
            errs.append("Khong co AndroidManifest.xml: khong phai APK.")
        return report(errs, warns, info)
    bad = z.testzip()
    if bad:
        errs.append("Du lieu hong (sai CRC) o '%s': file bi loi khi tai / chep. Tai lai." % bad)

    # --- manifest
    try:
        tags = parse_axml(z.read("AndroidManifest.xml"))
    except Exception as e:
        errs.append("Khong doc duoc AndroidManifest.xml: %s" % e)
        return report(errs, warns, info)
    pkg = next((a.get("package", "?") for t, a, d in tags if t == "manifest"), "?")
    sdk = next((a for t, a, d in tags if t == "uses-sdk"), {})
    mn, tg = sdk.get("minSdkVersion", 1), sdk.get("targetSdkVersion", sdk.get("minSdkVersion", 1))
    mn = int(mn) if str(mn).isdigit() else 1
    tg = int(tg) if str(tg).isdigit() else mn
    app = next((a for t, a, d in tags if t == "application"), {})
    info.append("Goi: %s | minSdk %d (%s) | targetSdk %d (%s)" % (pkg, mn, andro(mn), tg, andro(tg)))
    if mn > 21:
        warns.append("minSdk %d: may duoi %s khong cai duoc. Muon cai moi may: minSdk 21 (Android 5.0) hoac thap hon." % (mn, andro(mn)))
    if tg < 24:
        warns.append("targetSdk %d qua cu: Android 14+ chan cai app targetSdk < 23, Play Protect canh bao." % tg)
    if app.get("testOnly") is True:
        errs.append("android:testOnly=true: chi cai bang adb, cai tay se bao loi.")
    if app.get("debuggable") is True:
        warns.append("Ban debug (android:debuggable=true): Play Protect de canh bao.")
    # Activity / Service / Receiver co intent-filter phai ghi exported khi targetSdk >= 31
    if tg >= 31:
        for i, (t, a, d) in enumerate(tags):
            if t in ("activity", "activity-alias", "service", "receiver"):
                # chi xet cac the con truc tiep
                k, has_filter = i + 1, False
                while k < len(tags) and tags[k][2] > d:
                    if tags[k][0] == "intent-filter" and tags[k][2] == d + 1:
                        has_filter = True
                    k += 1
                if has_filter and "exported" not in a:
                    errs.append("%s '%s' co intent-filter nhung thieu android:exported (bat buoc tu Android 12): loi phan tich goi."
                                % (t, a.get("name", "?")))

    # --- dex
    dexes = sorted(n for n in names if n.startswith("classes") and n.endswith(".dex"))
    if not dexes:
        errs.append("Khong co classes.dex.")
    for d in dexes:
        h = z.read(d)[:8]
        if h[:4] != b"dex\n":
            errs.append("%s hong." % d)
            continue
        ver = int(h[4:7])
        need = {35: 1, 37: 24, 38: 26, 39: 28, 40: 34, 41: 35}.get(ver, 1)
        info.append("%s: dex %03d (chay tu %s)" % (d, ver, andro(need) if need > 1 else "moi ban"))
        if need > mn:
            errs.append("%s la dex %03d, chi chay tu %s nhung minSdk la %d: may cu bao loi phan tich goi. Dich voi --min-api %d."
                        % (d, ver, andro(need), mn, mn))

    # --- resources.arsc + zipalign + .so
    entries = {i.filename: i for i in z.infolist()}
    ra = entries.get("resources.arsc")
    if ra is not None:
        stored = ra.compress_type == zipfile.ZIP_STORED
        aligned = data_offset(raw, ra) % 4 == 0
        if tg >= 30 and not (stored and aligned):
            errs.append("resources.arsc %s: Android 11+ tu choi cai (loi phan tich goi). Luu khong nen + zipalign -p 4."
                        % ("dang bi nen" if not stored else "chua can le 4 byte"))
    unaligned = [n for n, i in entries.items() if i.compress_type == zipfile.ZIP_STORED and i.file_size > 0
                 and not n.endswith(".so") and data_offset(raw, i) % 4 != 0]
    if unaligned:
        warns.append("%d file luu khong nen chua can le 4 byte (vd %s): chay zipalign -p 4 truoc khi ky." % (len(unaligned), unaligned[0]))
    sos = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    abis = sorted(set(n.split("/")[1] for n in sos))
    if sos:
        info.append("Thu vien native: %s" % ", ".join(abis))
        for need_abi in ("armeabi-v7a", "arm64-v8a"):
            if need_abi not in abis:
                warns.append("Thieu thu vien cho %s: may dung chip nay khong cai / mo duoc." % need_abi)
        if app.get("extractNativeLibs") is False:
            for n in sos:
                i = entries[n]
                if i.compress_type != zipfile.ZIP_STORED:
                    errs.append("extractNativeLibs=false nhung %s bi nen: loi cai dat." % n)
                    break
                if data_offset(raw, i) % 4096:
                    errs.append("%s chua can le trang 4 KB (zipalign -p): loi cai dat." % n)
                    break
                if data_offset(raw, i) % 16384:
                    warns.append("%s chua can le 16 KB: may Android 15+ dung trang 16 KB co the loi." % n)
                    break
    else:
        info.append("Khong co thu vien native: chay duoc moi loai chip (ARM, ARM64, x86).")

    # --- chu ky
    v1 = any(n.startswith("META-INF/") and n.endswith(".SF") for n in names) and \
        any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in names)
    sch = signing_schemes(raw)
    have = (["v1"] if v1 else []) + sorted(sch or [])
    info.append("Chu ky: %s" % (", ".join(have) if have else "KHONG CO"))
    if not have:
        errs.append("APK chua ky: khong may nao cai duoc.")
    else:
        if tg >= 30 and not (sch & {"v2", "v3", "v3.1"}):
            errs.append("Thieu chu ky v2/v3 ma targetSdk >= 30: Android 11+ bao loi phan tich goi. Ky bang apksigner (v1+v2+v3).")
        if mn < 24 and not v1:
            errs.append("Thieu chu ky v1 ma minSdk < 24: may Android 5-6 khong cai duoc. Bat --v1-signing-enabled true.")
    return report(errs, warns, info)


def report(errs, warns, info):
    for s in info:
        print("   " + s)
    for s in warns:
        print("   CANH BAO: " + s)
    for s in errs:
        print("   LOI: " + s)
    print("   => " + ("CAI DUOC tren moi may tu minSdk tro len" if not errs else "KHONG CAI DUOC (%d loi)" % len(errs)))
    return not errs


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    ok = all([check(p) for p in sys.argv[1:]])
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
