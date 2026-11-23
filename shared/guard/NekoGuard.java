import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * NekoGuard - khoá chống sửa dùng chung cho mọi game của Neko Yuki.
 *
 *  - Dấu bản quyền từng file: lúc build, tools/sign_assets.py ghi danh sách mọi tài nguyên + mã SHA-256 vào /neko.sig,
 *    nhúng dòng "Copyright Neko Yuki" vào tài nguyên của Neko Yuki, và ghi mã của chính /neko.sig vào NekoGuardData.SIG.
 *    Game gọi loadManifest() lúc mở và checkFile() khi đọc tài nguyên: file bị thay, thêm, xoá hoặc mất dấu -> khoá.
 *  - Credits: checkCredits() bắt buộc phải còn "Neko Yuki" (và tên tác giả game gốc nếu có).
 *  - Phòng khoá "Just Neko Yuki": khi đã khoá, game chỉ vẽ paint() và chỉ nhận phím để thoát (không cho chơi tiếp,
 *    không xoá file lưu của người chơi). Game DDLC truyền thêm hình Monika để phòng có cả hai.
 *
 * Game đã có cách kiểm tra riêng (FNaF: FnafPack, DDLC: SIG_*) chỉ cần gọi trip(...) khi phát hiện sửa đổi.
 * CLDC 1.0 / MIDP 2.0: không float, không generics, không StringBuilder.
 */
final class NekoGuard {

    // ------------------------------------------------------------------ cấu hình (game đặt lúc mở)
    /** Tên tác giả game gốc cho dòng bản quyền, vd "Team Salvato", "Scott Cawthon"; null nếu game hoàn toàn của Neko Yuki. */
    static String original;
    /** Nơi tải bản chính thức. */
    static String[] links = {"Facebook: facebook.com/tunekogaming", "YouTube: @tuanimekiller", "Discord: @tudaica123gio"};
    /** Dòng chữ chập chờn trong phòng; DDLC: "Just Monika. Just Neko Yuki." */
    static String just = "Just Neko Yuki.";
    static boolean en;

    // ------------------------------------------------------------------ trạng thái
    static boolean locked;
    static String reason = "";

    /** Phát hiện sửa đổi: khoá game (chỉ ghi lý do đầu tiên). */
    static void trip(String why) {
        if (!locked) {
            locked = true;
            reason = why == null ? "" : why;
        }
    }

    // ------------------------------------------------------------------ dòng bản quyền
    static String copyright() {
        if (original == null) return "© Neko Yuki";
        return en ? "© Neko Yuki (port & code) - original game © " + original
                  : "© Neko Yuki (chuyển thể & mã nguồn) - game gốc © " + original;
    }

    // ------------------------------------------------------------------ danh sách ký (neko.sig)
    /** tên file -> String[]{kích thước, 16 ký tự đầu SHA-256, loại: "o" tài nguyên Neko Yuki, "b" tài nguyên bản build} */
    static Hashtable files;
    static String build = "";

    /**
     * Đọc /neko.sig và so mã SHA-256 của nó với mã ghi lúc build (NekoGuardData.SIG).
     * Sai mã, thiếu file hoặc lỗi định dạng -> khoá. Trả về true nếu hợp lệ.
     */
    static boolean loadManifest(InputStream in, String expectSha) {
        try {
            if (in == null) {
                trip("thiếu neko.sig");
                return false;
            }
            byte[] d = readAll(in);
            if (expectSha == null || !expectSha.equals(sha256hex(d, 0, d.length))) {
                trip("neko.sig bị sửa");
                return false;
            }
            Hashtable t = new Hashtable();
            Vector lines = split(new String(d, "UTF-8"), '\n');
            if (lines.size() == 0 || !((String) lines.elementAt(0)).startsWith("NEKO-SIG|1|")) {
                trip("neko.sig sai định dạng");
                return false;
            }
            Vector head = split((String) lines.elementAt(0), '|');
            build = head.size() > 4 ? (String) head.elementAt(4) : "";
            for (int i = 1; i < lines.size(); i++) {
                Vector p = split((String) lines.elementAt(i), '|');
                if (p.size() == 4) t.put(p.elementAt(0), new String[]{(String) p.elementAt(1), (String) p.elementAt(2), (String) p.elementAt(3)});
            }
            files = t;
            return true;
        } catch (Throwable e) {
            trip("neko.sig lỗi");
            return false;
        }
    }

    /** Có trong danh sách ký không (để game biết file nào cần kiểm). */
    static boolean known(String name) {
        return files != null && files.get(name) != null;
    }

    /**
     * Kiểm tra một tài nguyên vừa đọc (tên dạng "/img/a.png"). File lạ, sai kích thước, sai mã, hoặc tài nguyên của
     * Neko Yuki bị mất dấu bản quyền -> khoá. Trả về true nếu hợp lệ.
     */
    static boolean checkFile(String name, byte[] data) {
        if (files == null) {
            trip("chưa đọc neko.sig");
            return false;
        }
        String[] e = (String[]) files.get(name);
        if (e == null) {
            trip("file lạ: " + name);
            return false;
        }
        if (data == null || !e[0].equals(String.valueOf(data.length)) || !sha256hex(data, 0, data.length).startsWith(e[1])) {
            trip("file bị sửa: " + name);
            return false;
        }
        if ("o".equals(e[2]) && indexOf(data, MARK) < 0) {
            trip("mất dấu bản quyền: " + name);
            return false;
        }
        return true;
    }

    static final byte[] MARK = {'N', 'e', 'k', 'o', ' ', 'Y', 'u', 'k', 'i'};

    /** Credits phải còn "Neko Yuki" và mọi tên trong must (vd tác giả game gốc). */
    static boolean checkCredits(String text, String[] must) {
        if (text == null || text.indexOf("Neko Yuki") < 0) {
            trip("credits bị xoá Neko Yuki");
            return false;
        }
        if (must != null) {
            for (int i = 0; i < must.length; i++) {
                if (text.indexOf(must[i]) < 0) {
                    trip("credits bị sửa");
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ phòng khoá
    /** Vẽ chữ bằng font của game (font bitmap có dấu tiếng Việt); không có thì dùng SysText. */
    interface Text {
        int height();

        int width(String s);

        void draw(Graphics g, String s, int x, int y, int rgb);
    }

    /** Font hệ thống của máy (máy cũ có thể không hiện đủ dấu tiếng Việt: game nên truyền font riêng). */
    static final class SysText implements Text {
        final Font f;

        SysText(boolean bold, int size) {
            f = Font.getFont(Font.FACE_SYSTEM, bold ? Font.STYLE_BOLD : Font.STYLE_PLAIN, size);
        }

        public int height() {
            return f.getHeight();
        }

        public int width(String s) {
            return f.stringWidth(s);
        }

        public void draw(Graphics g, String s, int x, int y, int rgb) {
            g.setFont(f);
            g.setColor(rgb);
            g.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
        }
    }

    static String[] texts() {
        if (en) return new String[]{"GAME HAS BEEN MODIFIED", "This is not an official Neko Yuki copy.",
            "Data, resources or credits were changed, so the game is locked.", "Download the original game and files from Neko Yuki:",
            "Press any key to exit"};
        return new String[]{"GAME ĐÃ BỊ SỬA ĐỔI", "Đây không phải bản chính chủ của Neko Yuki.",
            "Dữ liệu, tài nguyên hoặc credits đã bị thay đổi nên game bị khoá.", "Tải game gốc kèm file gốc tại Neko Yuki:",
            "Nhấn phím bất kỳ để thoát"};
    }

    /**
     * Vẽ phòng khoá: khoảng không đầy sao, sàn và bàn, nhân vật đứng sau bàn (chars: Neko Yuki, DDLC thêm Monika),
     * chữ cảnh báo, nơi tải bản gốc, dòng bản quyền; thỉnh thoảng nhiễu hình kèm dòng "Just Neko Yuki."
     *
     * @param chars ảnh nhân vật đã thu nhỏ hợp màn hình (null / rỗng: chỉ vẽ phòng và chữ)
     * @param ms    thời gian (System.currentTimeMillis) cho hiệu ứng
     */
    static void paint(Graphics g, int w, int h, Image[] chars, Text title, Text body, long ms) {
        if (title == null) title = new SysText(true, Font.SIZE_LARGE);
        if (body == null) body = new SysText(false, Math.min(w, h) >= 360 ? Font.SIZE_MEDIUM : Font.SIZE_SMALL);
        g.setClip(0, 0, w, h);
        // nền: khoảng không tím đen, đậm dần xuống dưới
        for (int y = 0; y < h; y += 8) {
            int k = y * 16 / Math.max(1, h);
            g.setColor((8 + k / 2) << 16 | (4 + k / 4) << 8 | (16 + k));
            g.fillRect(0, y, w, 8);
        }
        // sao: vị trí cố định, nhấp nháy theo thời gian
        int seed = 0x4E4B;
        int n = Math.max(24, w * h / 1800);
        for (int i = 0; i < n; i++) {
            seed = seed * 1103515245 + 12345;
            int sx = ((seed >>> 8) & 0x7FFF) % w;
            seed = seed * 1103515245 + 12345;
            int sy = ((seed >>> 8) & 0x7FFF) % h;
            boolean bright = ((int) (ms / 140) + i * 7) % 19 == 0;
            g.setColor(bright ? 0xFFFFFF : (i % 3 == 0 ? 0x8C82B4 : 0x5A5078));
            g.fillRect(sx, sy, bright ? 2 : 1, bright ? 2 : 1);
        }
        boolean land = w > h;
        boolean glitch = ms % 2600 < 180;
        String[] tx = texts();
        int pad = Math.max(4, Math.min(w, h) / 30);
        int textX = pad, textW = land ? w * 52 / 100 - pad * 2 : w - pad * 2;
        int fw = land ? textW : w - pad * 2;
        // gọn dần cho tới khi chữ vừa màn: 0 đủ; 1 bỏ dòng giải thích; 2 tiêu đề chữ nhỏ, bỏ dòng thoát, bỏ nhân vật;
        // 3 bỏ dòng "không phải bản chính chủ"; 4 bản quyền rút gọn
        Vector top = null, bot = null;
        int level = 0, topH = 0;
        for (; level <= 4; level++) {
            top = new Vector();          // mỗi phần tử: Object[]{Text, String, Integer màu, Integer lề thêm}
            add(top, level >= 2 ? body : title, tx[0], textW, 0xFF4A6A, 0);
            if (level < 3) add(top, body, tx[1], textW, 0xFFFFFF, 0);
            if (level == 0) add(top, body, tx[2], textW, 0xBBB4D0, 0);
            add(top, body, tx[3], textW, 0xFFD27A, 0);
            for (int i = 0; i < links.length; i++) add(top, body, links[i], textW - pad, 0xFFFFFF, pad);
            bot = new Vector();
            add(bot, body, level >= 4 ? "© Neko Yuki" : copyright(), fw, 0x9890B0, 0);
            if (level < 2) add(bot, body, tx[4], fw, 0x6E6888, 0);
            topH = 0;
            for (int i = 0; i < top.size(); i++) topH += ((Text) ((Object[]) top.elementAt(i))[0]).height();
            if (pad * 3 + topH + bot.size() * body.height() <= h) break;
        }
        if (level > 4) level = 4;
        if (level >= 2 && !land) chars = null;       // màn quá nhỏ: chỉ để chữ
        int textBottom = pad + topH;
        int botTop = h - pad - bot.size() * body.height();
        // sàn + bàn (sàn trải hết bề ngang; ngang thì nhân vật ở nửa phải)
        int roomX = land ? w * 52 / 100 : 0, roomW = land ? w - roomX : w;
        int floorY = land ? h * 80 / 100 : Math.min(h * 84 / 100, botTop - pad / 2);
        g.setColor(0x1C1230);
        g.fillRect(0, floorY, w, h - floorY);
        g.setColor(0x3C2C5A);
        g.drawLine(0, floorY, w, floorY);
        int deskW = roomW * 62 / 100, deskH = Math.max(6, h / 22);
        int deskX = roomX + (roomW - deskW) / 2, deskY = floorY - deskH / 2;
        // nhân vật đứng sau bàn
        int cnt = chars == null ? 0 : chars.length, totalW = 0, charTop = h;
        for (int i = 0; i < cnt; i++) if (chars[i] != null) totalW += chars[i].getWidth() + (i > 0 ? pad : 0);
        int cx = roomX + (roomW - totalW) / 2;
        g.setClip(0, 0, w, deskY + deskH);           // nhân vật không lấn xuống dưới bàn
        for (int i = 0; i < cnt; i++) {
            Image im = chars[i];
            if (im == null) continue;
            int iw = im.getWidth(), ih = im.getHeight();
            int iy = deskY + deskH / 2 - ih;
            charTop = Math.min(charTop, iy);
            if (glitch) {
                // nhiễu hình: cắt ảnh thành dải ngang, lệch ngẫu nhiên
                int strip = Math.max(4, ih / 24), r = (int) (ms / 30) + i * 97;
                for (int sy = 0; sy < ih; sy += strip) {
                    r = r * 1103515245 + 12345;
                    int dx = ((r >>> 16) % 9) - 4;
                    g.drawRegion(im, 0, sy, iw, Math.min(strip, ih - sy), 0, cx + dx * Math.max(1, iw / 40), iy + sy, Graphics.TOP | Graphics.LEFT);
                }
            } else {
                g.drawImage(im, cx, iy, Graphics.TOP | Graphics.LEFT);
            }
            cx += iw + pad;
        }
        g.setClip(0, 0, w, h);
        g.setColor(0x3A2618);
        g.fillRect(deskX, deskY, deskW, deskH);
        g.setColor(0x6A4A30);
        g.fillRect(deskX, deskY, deskW, Math.max(2, deskH / 4));
        // nhân vật đè lên chữ (màn nhỏ): phủ sọc tối sau chữ cho dễ đọc
        if (!land && charTop < textBottom) shade(g, 0, 0, w, textBottom + pad / 2);
        if (botTop < floorY) shade(g, 0, botTop - pad / 2, land ? textW + pad * 2 : w, h - botTop + pad / 2);
        int y = pad;
        for (int i = 0; i < top.size(); i++) {
            Object[] o = (Object[]) top.elementAt(i);
            Text t = (Text) o[0];
            t.draw(g, (String) o[1], textX + ((Integer) o[3]).intValue(), y, ((Integer) o[2]).intValue());
            y += t.height();
        }
        y = botTop;
        for (int i = 0; i < bot.size(); i++) {
            Object[] o = (Object[]) bot.elementAt(i);
            body.draw(g, (String) o[1], textX, y, ((Integer) o[2]).intValue());
            y += body.height();
        }
        // "Just Neko Yuki." chập chờn khi nhiễu
        if (glitch) {
            int jy = (int) ((ms / 170) % Math.max(1, h - body.height()));
            for (int x = -((int) (ms / 20) % 60); x < w; x += body.width(just) + pad * 2) body.draw(g, just, x, jy, 0xE8E0FF);
        }
    }

    /** Thêm các dòng (đã tự xuống dòng) của một đoạn vào danh sách vẽ. */
    static void add(Vector v, Text t, String s, int maxW, int rgb, int indent) {
        Vector ls = wrap(t, s, maxW);
        for (int i = 0; i < ls.size(); i++) v.addElement(new Object[]{t, ls.elementAt(i), new Integer(rgb), new Integer(indent)});
    }

    /** Làm tối một vùng bằng sọc ngang cách dòng (MIDP không có tô trong suốt). */
    static void shade(Graphics g, int x, int y, int w, int h) {
        g.setColor(0x05030A);
        for (int yy = y; yy < y + h; yy += 2) g.fillRect(x, yy, w, 1);
    }

    /** Vẽ một đoạn chữ tự xuống dòng; trả về y ngay dưới đoạn. */
    static int para(Graphics g, Text t, String s, int x, int y, int maxW, int rgb) {
        Vector ls = wrap(t, s, maxW);
        for (int i = 0; i < ls.size(); i++) {
            t.draw(g, (String) ls.elementAt(i), x, y, rgb);
            y += t.height();
        }
        return y;
    }

    static Vector wrap(Text t, String s, int maxW) {
        Vector out = new Vector();
        Vector words = split(s, ' ');
        String cur = "";
        for (int i = 0; i < words.size(); i++) {
            String wd = (String) words.elementAt(i);
            String nx = cur.length() == 0 ? wd : cur + " " + wd;
            if (cur.length() > 0 && t.width(nx) > maxW) {
                out.addElement(cur);
                cur = wd;
            } else {
                cur = nx;
            }
        }
        if (cur.length() > 0) out.addElement(cur);
        return out;
    }

    /** Phím khi đang khoá: mọi phím đều là thoát game (game gọi notifyDestroyed). Trả về true nếu game nên thoát. */
    static boolean key(int keyCode) {
        return locked;
    }

    // ------------------------------------------------------------------ tiện ích
    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    static int indexOf(byte[] d, byte[] p) {
        outer:
        for (int i = 0; i + p.length <= d.length; i++) {
            for (int j = 0; j < p.length; j++) if (d[i + j] != p[j]) continue outer;
            return i;
        }
        return -1;
    }

    static Vector split(String s, char sep) {
        Vector v = new Vector();
        int st = 0;
        while (true) {
            int i = s.indexOf(sep, st);
            if (i < 0) {
                String last = s.substring(st).trim();
                if (last.length() > 0) v.addElement(last);
                break;
            }
            v.addElement(s.substring(st, i).trim());
            st = i + 1;
        }
        return v;
    }

    // ------------------------------------------------------------------ SHA-256 (CLDC không có MessageDigest)
    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    static String sha256hex(byte[] d, int off, int len) {
        int[] h = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
        int[] w = new int[64];
        long bits = (long) len * 8;
        int total = ((len + 9 + 63) / 64) * 64;
        byte[] blk = new byte[64];
        for (int base = 0; base < total; base += 64) {
            for (int i = 0; i < 64; i++) {
                int p = base + i;
                int v;
                if (p < len) v = d[off + p];
                else if (p == len) v = 0x80;
                else if (p >= total - 8) v = (int) (bits >>> ((total - 1 - p) * 8));
                else v = 0;
                blk[i] = (byte) v;
            }
            for (int i = 0; i < 16; i++)
                w[i] = (blk[i * 4] & 255) << 24 | (blk[i * 4 + 1] & 255) << 16 | (blk[i * 4 + 2] & 255) << 8 | (blk[i * 4 + 3] & 255);
            for (int i = 16; i < 64; i++) {
                int x = w[i - 15], y = w[i - 2];
                int s0 = (x >>> 7 | x << 25) ^ (x >>> 18 | x << 14) ^ (x >>> 3);
                int s1 = (y >>> 17 | y << 15) ^ (y >>> 19 | y << 13) ^ (y >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0], b = h[1], c = h[2], dd = h[3], e = h[4], f = h[5], gg = h[6], hh = h[7];
            for (int i = 0; i < 64; i++) {
                int s1 = (e >>> 6 | e << 26) ^ (e >>> 11 | e << 21) ^ (e >>> 25 | e << 7);
                int t1 = hh + s1 + ((e & f) ^ (~e & gg)) + K[i] + w[i];
                int s0 = (a >>> 2 | a << 30) ^ (a >>> 13 | a << 19) ^ (a >>> 22 | a << 10);
                int t2 = s0 + ((a & b) ^ (a & c) ^ (b & c));
                hh = gg; gg = f; f = e; e = dd + t1; dd = c; c = b; b = a; a = t1 + t2;
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += dd; h[4] += e; h[5] += f; h[6] += gg; h[7] += hh;
        }
        String hex = "0123456789abcdef";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 8; i++)
            for (int sft = 28; sft >= 0; sft -= 4) sb.append(hex.charAt((h[i] >>> sft) & 15));
        return sb.toString();
    }
}
