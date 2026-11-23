/**
 * NekoDevice - nhận diện máy và tự tối ưu theo từng hệ điều hành. Dùng chung cho mọi game của Neko Yuki.
 *
 * Chỉ có phần tính toán, không vẽ gì: mỗi game tự vẽ màn "Cấu hình thiết bị" theo giao diện riêng, nhưng phải có đủ
 * các mục trong shared/device/README.md (Tự động tối ưu, chọn hệ điều hành, Tự tối ưu cho hệ điều hành đang chọn...).
 *
 *   int p = NekoDevice.detect("vn.nekoyuki.fnaf.MainActivity");   // tên Activity trong APK riêng của game
 *   NekoDevice.Profile pf = NekoDevice.optimize(p, w, h, Runtime.getRuntime().totalMemory(), hasPointerEvents(), true);
 *   // pf.gfx, pf.perf, pf.fps, pf.input, pf.layout, pf.font, pf.pack, pf.recW x pf.recH -> đổi sang cài đặt của game
 *
 * CLDC 1.0 / MIDP 2.0: không float, không generics, không StringBuilder. Cài vào game: python3 tools/install_device.py.
 */
final class NekoDevice {

    // ------------------------------------------------------------------ nền tảng / hệ điều hành
    static final int P_AUTO = 0, P_JAVA = 1, P_SYMBIAN = 2, P_J2ME_LOADER = 3, P_ANDROID = 4, P_PC = 5;
    static final int P_COUNT = 6;
    static final String[] P_NAME = {"Tự động", "Điện thoại Java", "Symbian", "J2ME Loader (Android)", "Android (APK)", "Giả lập PC"};
    static final String[] P_NAME_EN = {"Auto", "Java phone", "Symbian", "J2ME Loader (Android)", "Android (APK)", "PC emulator"};
    static final String[] P_SHORT = {"Tự động", "Java", "Symbian", "J2ME Loader", "Android", "PC"};
    static final String[] P_SHORT_EN = {"Auto", "Java", "Symbian", "J2ME Loader", "Android", "PC"};

    // ------------------------------------------------------------------ các mức của hồ sơ tối ưu
    static final int G_VLOW = 0, G_LOW = 1, G_MED = 2, G_HIGH = 3, G_VHIGH = 4, G_ULTRA = 5;
    static final String[] G_NAME = {"Cực thấp", "Thấp", "Trung bình", "Cao", "Cực cao", "Siêu nét"};
    static final String[] G_NAME_EN = {"Very low", "Low", "Medium", "High", "Very high", "Ultra"};
    static final int PERF_SAVE = 0, PERF_BAL = 1, PERF_HIGH = 2;
    static final String[] PERF_NAME = {"Tiết kiệm", "Cân bằng", "Cao"};
    static final String[] PERF_NAME_EN = {"Battery saver", "Balanced", "High"};
    static final int IN_TOUCH = 0, IN_KEY_S = 1, IN_KEY_M = 2, IN_KEY_L = 3;
    static final String[] IN_NAME = {"Cảm ứng", "Bàn phím - màn nhỏ", "Bàn phím - màn vừa", "Bàn phím - màn lớn"};
    static final String[] IN_NAME_EN = {"Touch", "Keypad - small screen", "Keypad - medium screen", "Keypad - large screen"};
    static final int LAY_LAND = 0, LAY_PORT = 1, LAY_SQUARE = 2;
    static final String[] LAY_NAME = {"Ngang", "Dọc", "Vuông"};
    static final String[] LAY_NAME_EN = {"Landscape", "Portrait", "Square"};
    static final int FONT_S = 0, FONT_M = 1, FONT_L = 2;
    static final String[] FONT_NAME = {"Nhỏ", "Vừa", "Lớn"};
    static final String[] FONT_NAME_EN = {"Small", "Medium", "Large"};
    static final int PACK_LITE = 0, PACK_FULL = 1, PACK_HD = 2;
    static final String[] PACK_NAME = {"Lite", "Full", "HD"};

    /** Kết quả tự tối ưu cho một hệ điều hành + một màn hình. */
    static final class Profile {
        int platform;           // P_*
        int gfx;                // G_*
        int perf;               // PERF_*
        int fps;                // khung hình / giây nên chạy
        int input;              // IN_*
        int layout;             // LAY_*
        int font;               // FONT_*
        int pack;               // PACK_*: gói tài nguyên nên tải (xem client/ - NekoAssets)
        int recW, recH;         // độ phân giải nên đặt (= màn hình thật nếu nền tảng không chỉnh được)
        boolean effects;        // bật hiệu ứng nặng (rung, mờ, chuyển cảnh)
        boolean sound;          // bật đủ âm thanh
    }

    // ------------------------------------------------------------------ nhận diện
    static String prop(String k) {
        try {
            return System.getProperty(k);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean has(String s, String sub) {
        return s != null && s.toLowerCase().indexOf(sub) >= 0;
    }

    static boolean classExists(String name) {
        if (name == null || name.length() == 0) return false;
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Đoán nền tảng đang chạy.
     *  - Android (APK): APK riêng của game. Nhận ra nhờ thuộc tính hệ thống "nekoyuki.platform" (APK đặt) hoặc lớp
     *    Activity của game có mặt (lớp này chỉ có trong APK, không có trong file jar chạy bằng J2ME Loader).
     *  - J2ME Loader: chạy trên máy ảo Android nhưng không phải APK của game.
     *  - Giả lập PC: máy ảo Java của máy tính (KEmulator, FreeJ2ME, MicroEmulator...).
     *  - Symbian: máy ảo J9 của Nokia S60 hoặc tên máy thuộc dòng S60.
     *  - Còn lại: điện thoại Java thường (S40, Sony Ericsson, Samsung, Motorola...).
     *
     * @param apkActivity tên đầy đủ lớp Activity trong APK của game, hoặc null
     */
    static int detect(String apkActivity) {
        if (has(prop("nekoyuki.platform"), "apk")) return P_ANDROID;
        String vm = prop("java.vm.name"), vendor = prop("java.vendor"), osn = prop("os.name"), plat = prop("microedition.platform");
        if (has(vendor, "android") || has(vm, "dalvik") || has(osn, "android") || has(plat, "j2me-loader") || has(plat, "j2meloader"))
            return classExists(apkActivity) ? P_ANDROID : P_J2ME_LOADER;
        // máy ảo máy tính; "CLDC HotSpot" của Sun là máy ảo trên điện thoại thật, không tính
        boolean desktopVm = (has(vm, "hotspot") || has(vm, "openjdk") || has(vm, "server vm") || has(vm, "client vm")) && !has(vm, "cldc");
        if (desktopVm || has(osn, "windows") || has(osn, "mac") || has(plat, "kemulator") || has(plat, "freej2me") || has(plat, "microemu"))
            return P_PC;
        if (prop("com.ibm.oti.configuration") != null || prop("com.ibm.oti.jcl.build") != null || has(plat, "symbian")
                || has(plat, "sw_platform=s60") || has(plat, "s60")) return P_SYMBIAN;
        if (plat != null && plat.startsWith("Nokia") && s60Model(plat.substring(5))) return P_SYMBIAN;
        return P_JAVA;
    }

    /** Tên máy Nokia (phần sau chữ "Nokia") thuộc dòng S60 / Symbian. */
    static boolean s60Model(String m) {
        String u = m.toUpperCase();
        if (u.startsWith("E") || u.startsWith("N") && !u.startsWith("NGAGE")) return true;
        String[] s60 = {"5230", "5228", "5233", "5235", "5250", "5320", "5500", "5530", "5630", "5700", "5730", "5800",
            "6110", "6120", "6121", "6122", "6124", "6210", "6220", "6290", "6650", "6700S", "6710", "6720", "6730", "6760",
            "6788", "6790", "C5", "C6", "C7", "X5", "X6", "X7", "T7", "500", "600", "603", "700", "701", "808", "3250", "3230",
            "6600", "6630", "6680", "6681", "7610"};
        for (int i = 0; i < s60.length; i++) {
            int n = s60[i].length();
            // khớp đúng số máy: "500" không được khớp Nokia 5000 (S40)
            if (u.startsWith(s60[i]) && (u.length() == n || u.charAt(n) < '0' || u.charAt(n) > '9')) return true;
        }
        return false;
    }

    /** Nền tảng thật sẽ dùng: lựa chọn của người chơi, hoặc kết quả nhận diện khi để Tự động. */
    static int resolve(int chosen, String apkActivity) {
        return chosen > P_AUTO && chosen < P_COUNT ? chosen : detect(apkActivity);
    }

    // ------------------------------------------------------------------ tự tối ưu
    /** Bố cục theo tỉ lệ màn hình: cạnh dài hơn cạnh kia từ 1,2 lần là ngang / dọc; gần vuông (máy gập) là vuông. */
    static int layoutFor(int w, int h) {
        if (w * 100 >= h * 120) return LAY_LAND;
        if (h * 100 >= w * 120) return LAY_PORT;
        return LAY_SQUARE;
    }

    static int inputFor(int w, int h, boolean touch) {
        if (touch) return IN_TOUCH;
        int small = Math.min(w, h);
        return small < 160 ? IN_KEY_S : (small < 240 ? IN_KEY_M : IN_KEY_L);
    }

    /**
     * Tự tối ưu cho một hệ điều hành: độ phân giải, đồ hoạ, hiệu năng, FPS, điều khiển, bố cục, cỡ chữ, gói tài nguyên.
     *
     * @param platform P_JAVA..P_PC (P_AUTO: tự nhận diện, không biết Activity APK)
     * @param w        chiều rộng màn hình game đang có
     * @param h        chiều cao
     * @param mem      bộ nhớ Java (byte), 0 nếu không biết
     * @param touch    máy có cảm ứng
     * @param hd       game có bản / gói ảnh HD
     */
    static Profile optimize(int platform, int w, int h, long mem, boolean touch, boolean hd) {
        if (w <= 0 || h <= 0) {
            w = 240;
            h = 320;
        }
        Profile p = new Profile();
        p.platform = platform > P_AUTO && platform < P_COUNT ? platform : detect(null);
        int small = Math.min(w, h);
        long px = (long) w * h;
        long mb = mem >> 20;
        boolean lowMem = mem > 0 && mem < 3L * 1024 * 1024;
        p.layout = layoutFor(w, h);
        p.input = inputFor(w, h, touch);
        p.recW = w;
        p.recH = h;
        p.effects = true;
        p.sound = true;
        switch (p.platform) {
            case P_ANDROID:
                // APK riêng: vẽ thẳng theo màn hình máy, nhiều bộ nhớ -> đẩy mọi thứ lên tối đa
                p.gfx = small >= 432 ? G_ULTRA : (small > 260 ? G_VHIGH : G_HIGH);
                p.perf = PERF_HIGH;
                p.fps = 30;
                p.pack = hd ? PACK_HD : PACK_FULL;
                p.font = FONT_L;
                break;
            case P_J2ME_LOADER:
                // game chỉ có số điểm ảnh J2ME Loader cấp: màn quá to thì bớt hiệu ứng cho mượt
                p.gfx = small >= 432 ? G_ULTRA : (small > 260 ? G_VHIGH : G_HIGH);
                p.perf = px > 2600000L ? PERF_BAL : PERF_HIGH;
                p.fps = 30;
                p.pack = hd && small >= 360 ? PACK_HD : PACK_FULL;
                p.font = FONT_L;
                recommend(p, w, h, hd);
                break;
            case P_PC:
                p.gfx = small >= 432 ? G_ULTRA : G_VHIGH;
                p.perf = PERF_HIGH;
                p.fps = 30;
                p.pack = hd ? PACK_HD : PACK_FULL;
                p.font = FONT_M;
                recommend(p, w, h, hd);
                break;
            case P_SYMBIAN:
                // máy ảo J9 tự nới bộ nhớ: đồ hoạ vừa phải
                p.gfx = small >= 360 ? G_HIGH : G_MED;
                p.perf = PERF_BAL;
                p.fps = 25;
                p.pack = hd && small >= 360 && mb >= 8 ? PACK_HD : PACK_FULL;
                p.font = FONT_M;
                break;
            default:
                // điện thoại Java thường: nhẹ cho mượt, máy ít bộ nhớ thì bớt hẳn
                p.gfx = lowMem || small < 176 ? G_LOW : G_MED;
                p.perf = lowMem ? PERF_SAVE : PERF_BAL;
                p.fps = 20;
                p.pack = lowMem || small < 176 ? PACK_LITE : PACK_FULL;
                p.font = FONT_M;
                p.effects = !lowMem;
                break;
        }
        if (small < 176) p.font = FONT_S;
        return p;
    }

    /** Độ phân giải nên đặt cho nền tảng chỉnh được kích thước màn hình (J2ME Loader, giả lập PC). */
    static void recommend(Profile p, int w, int h, boolean hd) {
        boolean tall = h > w;
        int a = hd ? 1280 : 640, b = hd ? 720 : 360;
        p.recW = tall ? b : a;
        p.recH = tall ? a : b;
    }

    /**
     * Màn hình game thấp hẳn so với mức nên đặt (ảnh và chữ bị phóng to nên vỡ, mờ): cạnh ngắn chưa tới một nửa mức
     * nên đặt (bản HD: dưới 360, bản thường: dưới 180). Chỉ có nghĩa với J2ME Loader và giả lập PC.
     */
    static boolean resolutionLow(Profile p, int w, int h) {
        return Math.min(w, h) * 2 < Math.min(p.recW, p.recH);
    }

    // ------------------------------------------------------------------ chữ hiển thị
    /** Dòng thông tin máy cho đầu màn Cấu hình thiết bị. */
    static String scan(int platform, int w, int h, long mem, boolean touch, boolean en) {
        String plat = prop("microedition.platform");
        if (plat == null || plat.length() == 0) plat = "?";
        if (plat.length() > 24) plat = plat.substring(0, 24);
        long kb = mem / 1024;
        String m = kb >= 1024 ? (kb / 1024) + " MB" : kb + " KB";
        String pn = platform > P_AUTO && platform < P_COUNT ? (en ? P_SHORT_EN : P_SHORT)[platform] : "?";
        if (en) return "Device: " + plat + " (" + pn + "), " + w + "x" + h + ", Java memory " + m + ", touch: " + (touch ? "yes" : "no") + ".";
        return "Máy: " + plat + " (" + pn + "), " + w + "x" + h + ", bộ nhớ Java " + m + ", cảm ứng: " + (touch ? "có" : "không") + ".";
    }

    /** Tóm tắt một hồ sơ, dùng cho thông báo sau khi bấm Tự tối ưu. */
    static String summary(Profile p, boolean en) {
        return (en ? "Graphics " + G_NAME_EN[p.gfx] : "Đồ hoạ " + G_NAME[p.gfx]) + " - " + p.fps + " FPS - "
                + (en ? IN_NAME_EN : IN_NAME)[p.input] + (en ? " - pack " : " - gói ") + PACK_NAME[p.pack];
    }

    /** Lời khuyên về độ phân giải cho từng nền tảng. */
    static String advice(Profile p, int w, int h, boolean en) {
        String cur = w + "x" + h, rec = p.recW + "x" + p.recH;
        boolean low = resolutionLow(p, w, h);
        switch (p.platform) {
            case P_J2ME_LOADER:
                if (en) return "Game screen " + cur + (low ? " (LOW). The game draws exactly the pixels J2ME Loader gives it; a small"
                        + " screen stretched to the phone looks blurry. J2ME Loader > hold the game icon > Settings > Screen size > "
                        + rec + "." : " (good). The game draws exactly the pixels J2ME Loader gives it.");
                return "Màn hình game " + cur + (low ? " (THẤP). Game vẽ đúng số điểm ảnh J2ME Loader cấp; để nhỏ rồi phóng to ra"
                        + " màn hình điện thoại thì ảnh và chữ sẽ vỡ, mờ. J2ME Loader > nhấn giữ biểu tượng game > Cài đặt > Kích thước"
                        + " màn hình > nhập " + rec + "." : " (tốt). Game vẽ đúng số điểm ảnh J2ME Loader cấp.");
            case P_ANDROID:
                return en ? "The Android app draws straight at your screen size (" + cur + "). Nothing to adjust."
                        : "Bản Android vẽ thẳng theo màn hình máy (" + cur + "), không cần chỉnh gì thêm.";
            case P_PC:
                return en ? "PC emulator: set the screen size to " + rec + " in the emulator for the sharpest picture."
                        : "Giả lập PC: đặt kích thước màn hình " + rec + " trong giả lập để nét nhất.";
            case P_SYMBIAN:
                return en ? "Symbian uses the phone's real resolution (" + cur + "); the game fits itself."
                        : "Symbian dùng đúng độ phân giải thật của máy (" + cur + "), game tự canh.";
            default:
                return en ? "Java phones use their real resolution (" + cur + "). Weak phone or out-of-memory errors: use the Lite build."
                        : "Điện thoại Java dùng đúng độ phân giải thật (" + cur + "). Máy yếu hoặc báo thiếu bộ nhớ: dùng bản Lite.";
        }
    }
}
