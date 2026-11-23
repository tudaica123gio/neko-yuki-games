/**
 * Kiem thu NekoDevice: gia lap thuoc tinh he thong cua nhieu loai may (Nokia S40, Symbian, Sony Ericsson,
 * J2ME Loader, APK Android, KEmulator, FreeJ2ME...) roi kiem tra nhan dien va ho so tu toi uu.
 * Chay: bash tests/run_device.sh
 */
public class TestDevice {
    static int checks = 0;
    static final String[] KEYS = {"java.vm.name", "java.vendor", "os.name", "microedition.platform",
        "com.ibm.oti.configuration", "com.ibm.oti.jcl.build", "nekoyuki.platform"};
    static String[] saved = new String[KEYS.length];

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError("FAIL: " + what);
        System.out.println("  ok  " + what);
    }

    /** Dat bo thuoc tinh may gia lap: cap (khoa, gia tri), khoa khong co thi xoa. */
    static void device(String... kv) {
        for (String k : KEYS) System.clearProperty(k);
        for (int i = 0; i < kv.length; i += 2) System.setProperty(kv[i], kv[i + 1]);
    }

    static final String APK = "TestDevice";          // gia lap: lop Activity co mat trong APK
    static final String NO_APK = "vn.khongco.MainActivity";

    public static void main(String[] a) {
        for (int i = 0; i < KEYS.length; i++) saved[i] = System.getProperty(KEYS[i]);
        try {
            run();
        } finally {
            for (int i = 0; i < KEYS.length; i++) {
                if (saved[i] == null) System.clearProperty(KEYS[i]);
                else System.setProperty(KEYS[i], saved[i]);
            }
        }
        System.out.println("NekoDevice: " + checks + " kiem tra deu dat");
    }

    static void run() {
        System.out.println("== nhan dien nen tang");
        device("microedition.platform", "Nokia5310XpressMusic/10.10");
        check(NekoDevice.detect(NO_APK) == NekoDevice.P_JAVA, "Nokia 5310 (S40) -> Dien thoai Java");
        device("microedition.platform", "Nokia5000/04.81");
        check(NekoDevice.detect(null) == NekoDevice.P_JAVA, "Nokia 5000 (S40) khong bi nham thanh Symbian 500");
        device("microedition.platform", "SonyEricssonK800i/R1KG001");
        check(NekoDevice.detect(null) == NekoDevice.P_JAVA, "Sony Ericsson K800i -> Dien thoai Java");
        device("microedition.platform", "SonyEricssonW995", "java.vm.name", "CLDC HotSpot Implementation");
        check(NekoDevice.detect(null) == NekoDevice.P_JAVA, "may that dung CLDC HotSpot khong bi nham thanh PC");
        device("microedition.platform", "NokiaN95/30.0.015", "com.ibm.oti.configuration", "cldc");
        check(NekoDevice.detect(null) == NekoDevice.P_SYMBIAN, "Nokia N95 (may ao J9) -> Symbian");
        device("microedition.platform", "NokiaE71-1/300.21.012");
        check(NekoDevice.detect(null) == NekoDevice.P_SYMBIAN, "Nokia E71 (ten may S60) -> Symbian");
        device("microedition.platform", "Nokia5800d-1/52.0.007");
        check(NekoDevice.detect(null) == NekoDevice.P_SYMBIAN, "Nokia 5800 -> Symbian");
        device("java.vm.name", "Dalvik", "java.vendor", "The Android Project", "os.name", "Linux", "microedition.platform", "Nokia6233");
        check(NekoDevice.detect(NO_APK) == NekoDevice.P_J2ME_LOADER, "J2ME Loader (Dalvik, khong co Activity cua game) -> J2ME Loader");
        check(NekoDevice.detect(APK) == NekoDevice.P_ANDROID, "APK cua game (Dalvik + co Activity) -> Android");
        check(NekoDevice.detect(null) == NekoDevice.P_J2ME_LOADER, "Dalvik, khong truyen ten Activity -> J2ME Loader");
        device("java.vm.name", "Dalvik", "nekoyuki.platform", "android-apk");
        check(NekoDevice.detect(NO_APK) == NekoDevice.P_ANDROID, "APK danh dau bang thuoc tinh nekoyuki.platform -> Android");
        device("java.vm.name", "Java HotSpot(TM) Client VM", "os.name", "Windows 10", "microedition.platform", "KEmulator");
        check(NekoDevice.detect(null) == NekoDevice.P_PC, "KEmulator tren Windows -> Gia lap PC");
        device("java.vm.name", "OpenJDK 64-Bit Server VM", "os.name", "Linux");
        check(NekoDevice.detect(null) == NekoDevice.P_PC, "FreeJ2ME tren Linux (OpenJDK) -> Gia lap PC");
        device("os.name", "Linux", "microedition.platform", "MOTOROKR_E8");
        check(NekoDevice.detect(null) == NekoDevice.P_JAVA, "dien thoai Linux (Motorola) khong bi nham thanh PC");
        device();
        check(NekoDevice.detect(null) == NekoDevice.P_JAVA, "khong co thuoc tinh nao -> Dien thoai Java");
        check(NekoDevice.resolve(NekoDevice.P_ANDROID, null) == NekoDevice.P_ANDROID, "nguoi choi chon Android thi giu Android");
        check(NekoDevice.resolve(NekoDevice.P_AUTO, null) == NekoDevice.P_JAVA, "Tu dong -> dung ket qua nhan dien");
        check(NekoDevice.resolve(99, null) == NekoDevice.P_JAVA, "gia tri hong trong file luu -> tu nhan dien");

        System.out.println("== tu toi uu theo tung he dieu hanh");
        long mb = 1024L * 1024;
        NekoDevice.Profile p;

        p = NekoDevice.optimize(NekoDevice.P_JAVA, 240, 320, 2 * mb, false, true);
        check(p.gfx == NekoDevice.G_LOW && p.perf == NekoDevice.PERF_SAVE && p.fps == 20, "S40 240x320, 2 MB: do hoa Thap, Tiet kiem, 20 FPS");
        check(p.pack == NekoDevice.PACK_LITE && !p.effects, "S40 it bo nho: goi Lite, tat hieu ung nang");
        check(p.input == NekoDevice.IN_KEY_L && p.layout == NekoDevice.LAY_PORT, "S40 240x320: ban phim man lon, bo cuc doc");
        check(p.recW == 240 && p.recH == 320, "dien thoai Java: giu do phan giai that");

        p = NekoDevice.optimize(NekoDevice.P_JAVA, 128, 160, 1 * mb, false, true);
        check(p.font == NekoDevice.FONT_S && p.input == NekoDevice.IN_KEY_S, "man 128x160: chu nho, ban phim man nho");

        p = NekoDevice.optimize(NekoDevice.P_JAVA, 240, 320, 6 * mb, false, true);
        check(p.gfx == NekoDevice.G_MED && p.perf == NekoDevice.PERF_BAL && p.pack == NekoDevice.PACK_FULL, "may Java 6 MB: Trung binh, Can bang, goi Full");

        p = NekoDevice.optimize(NekoDevice.P_SYMBIAN, 240, 320, 8 * mb, false, true);
        check(p.gfx == NekoDevice.G_MED && p.fps == 25 && p.pack == NekoDevice.PACK_FULL, "Symbian 240x320: Trung binh, 25 FPS, goi Full");

        p = NekoDevice.optimize(NekoDevice.P_SYMBIAN, 360, 640, 16 * mb, true, true);
        check(p.gfx == NekoDevice.G_HIGH && p.pack == NekoDevice.PACK_HD && p.input == NekoDevice.IN_TOUCH, "Symbian cam ung 360x640: Cao, goi HD, cam ung");

        p = NekoDevice.optimize(NekoDevice.P_J2ME_LOADER, 1080, 470, 34 * mb, true, true);
        check(p.gfx == NekoDevice.G_ULTRA && p.perf == NekoDevice.PERF_HIGH && p.fps == 30, "J2ME Loader 1080x470: Sieu net, Cao, 30 FPS");
        check(p.layout == NekoDevice.LAY_LAND && p.font == NekoDevice.FONT_L && p.pack == NekoDevice.PACK_HD, "J2ME Loader ngang: chu lon, goi HD");
        check(p.recW == 1280 && p.recH == 720 && !NekoDevice.resolutionLow(p, 1080, 470), "J2ME Loader: goi y 1280x720, 1080x470 la tot");

        p = NekoDevice.optimize(NekoDevice.P_J2ME_LOADER, 240, 320, 34 * mb, true, true);
        check(NekoDevice.resolutionLow(p, 240, 320) && p.recW == 720 && p.recH == 1280, "J2ME Loader 240x320 doc: bao THAP, goi y 720x1280");
        check(NekoDevice.advice(p, 240, 320, false).indexOf("720x1280") > 0, "loi khuyen J2ME Loader chi cach dat 720x1280");

        p = NekoDevice.optimize(NekoDevice.P_J2ME_LOADER, 2560, 1440, 64 * mb, true, true);
        check(p.perf == NekoDevice.PERF_BAL, "J2ME Loader man qua lon (2560x1440): ha Can bang cho muot");

        p = NekoDevice.optimize(NekoDevice.P_ANDROID, 1872, 864, 34 * mb, true, true);
        check(p.gfx == NekoDevice.G_ULTRA && p.perf == NekoDevice.PERF_HIGH && p.fps == 30 && p.pack == NekoDevice.PACK_HD, "APK 1872x864: toi da moi thu");
        check(p.recW == 1872 && p.recH == 864, "APK: ve thang theo man hinh may");
        check(NekoDevice.advice(p, 1872, 864, false).indexOf("không cần chỉnh") > 0, "loi khuyen APK: khong can chinh");

        p = NekoDevice.optimize(NekoDevice.P_ANDROID, 1280, 720, 64 * mb, true, false);
        check(p.pack == NekoDevice.PACK_FULL, "game khong co ban HD: khong goi y goi HD");

        p = NekoDevice.optimize(NekoDevice.P_PC, 240, 320, 256 * mb, false, false);
        check(p.gfx == NekoDevice.G_VHIGH && p.recW == 360 && p.recH == 640, "gia lap PC 240x320: Cuc cao, goi y 360x640");

        p = NekoDevice.optimize(NekoDevice.P_ANDROID, 1080, 1100, 64 * mb, true, true);
        check(p.layout == NekoDevice.LAY_SQUARE, "man gan vuong (may gap): bo cuc Vuong");

        p = NekoDevice.optimize(NekoDevice.P_ANDROID, 0, 0, 0, true, true);
        check(p.recW == 240 && p.recH == 320, "man hinh chua co kich thuoc: dung 240x320 tam");

        System.out.println("== chu hien thi");
        device("microedition.platform", "Nokia5310XpressMusic/10.10");
        String s = NekoDevice.scan(NekoDevice.P_JAVA, 240, 320, 2 * mb, false, false);
        check(s.startsWith("Máy: Nokia5310XpressMusic/10. (Java), 240x320, bộ nhớ Java 2 MB, cảm ứng: không"), "dong quet may: " + s);
        check(NekoDevice.scan(NekoDevice.P_ANDROID, 1872, 864, 34 * mb, true, true).indexOf("touch: yes") > 0, "dong quet may tieng Anh");
        p = NekoDevice.optimize(NekoDevice.P_ANDROID, 1872, 864, 34 * mb, true, true);
        check(NekoDevice.summary(p, false).equals("Đồ hoạ Siêu nét - 30 FPS - Cảm ứng - gói HD"), "tom tat: " + NekoDevice.summary(p, false));
        for (int pl = 1; pl < NekoDevice.P_COUNT; pl++) {
            NekoDevice.Profile q = NekoDevice.optimize(pl, 240, 320, 4 * mb, false, true);
            check(NekoDevice.advice(q, 240, 320, false).length() > 20 && NekoDevice.advice(q, 240, 320, true).length() > 20,
                    "loi khuyen Viet + Anh cho " + NekoDevice.P_NAME[pl]);
        }
    }
}
