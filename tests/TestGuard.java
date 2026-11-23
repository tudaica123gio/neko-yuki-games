import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Kiem thu NekoGuard: danh sach ky (neko.sig) do tools/sign_assets.py tao, phat hien sua / them / xoa file, mat dau
 * ban quyen, credits bi xoa Neko Yuki, va chup phong khoa o nhieu co man. args: thu_muc_tai_nguyen_da_ky thu_muc_anh_ra thu_muc_art
 */
public class TestGuard {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("LOI: " + what);
        } else System.out.println("  ok  " + what);
    }

    static void reset() {
        NekoGuard.locked = false;
        NekoGuard.reason = "";
    }

    static byte[] read(File f) throws Exception {
        return Files.readAllBytes(f.toPath());
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File res = new File(a[0]), out = new File(a[1]), art = new File(a[2]);
        out.mkdirs();

        System.out.println("== SHA-256");
        Random r = new Random(7);
        boolean shaOk = true;
        for (int t = 0; t < 300; t++) {
            byte[] d = new byte[r.nextInt(300)];
            r.nextBytes(d);
            StringBuilder sb = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(d)) sb.append(String.format("%02x", b));
            if (!NekoGuard.sha256hex(d, 0, d.length).equals(sb.toString())) shaOk = false;
        }
        check(shaOk, "SHA-256 thuan Java khop chuan Java (300 mau)");

        System.out.println("== danh sach ky");
        byte[] sig = read(new File(res, "neko.sig"));
        check(NekoGuard.loadManifest(new ByteArrayInputStream(sig), NekoGuardData.SIG) && !NekoGuard.locked, "neko.sig hop le duoc nhan");
        check(NekoGuard.build.startsWith("Neko Yuki official build"), "dau ban build: " + NekoGuard.build);
        File png = new File(res, "img/nekoyuki_halfbody_240.png"), dat = new File(res, "game.dat"), cred = new File(res, "credits.txt");
        check(NekoGuard.checkFile("/img/nekoyuki_halfbody_240.png", read(png)), "anh Neko Yuki nguyen ven: qua");
        check(NekoGuard.checkFile("/game.dat", read(dat)) && NekoGuard.checkFile("/credits.txt", read(cred)) && !NekoGuard.locked, "tai nguyen game goc + credits nguyen ven: qua");
        check(NekoGuard.indexOf(read(png), NekoGuard.MARK) > 0, "anh cua Neko Yuki co dau ban quyen trong file");

        byte[] d = read(dat);
        d[d.length / 2] ^= 1;
        reset();
        check(!NekoGuard.checkFile("/game.dat", d) && NekoGuard.locked && NekoGuard.reason.indexOf("game.dat") > 0, "sua 1 bit trong file -> khoa (" + NekoGuard.reason + ")");
        reset();
        check(!NekoGuard.checkFile("/game.dat", new byte[]{1, 2, 3}) && NekoGuard.locked, "thay file khac -> khoa");
        reset();
        check(!NekoGuard.checkFile("/img/anh_an_cap.png", read(png)) && NekoGuard.locked, "file la khong co trong danh sach -> khoa");
        reset();
        // tai nguyen cua Neko Yuki bi go dau ban quyen (gia lap: danh sach ky ghi dung ma nhung file khong con dau)
        byte[] noMark = "anh khong co dau".getBytes("UTF-8");
        NekoGuard.files.put("/img/x.png", new String[]{String.valueOf(noMark.length), NekoGuard.sha256hex(noMark, 0, noMark.length).substring(0, 16), "o"});
        check(!NekoGuard.checkFile("/img/x.png", noMark) && NekoGuard.reason.indexOf("dấu bản quyền") > 0, "tai nguyen Neko Yuki mat dau ban quyen -> khoa");

        reset();
        byte[] bad = sig.clone();
        bad[bad.length - 5] ^= 1;
        check(!NekoGuard.loadManifest(new ByteArrayInputStream(bad), NekoGuardData.SIG) && NekoGuard.locked, "neko.sig bi sua -> khoa");
        reset();
        check(!NekoGuard.loadManifest(null, NekoGuardData.SIG) && NekoGuard.locked, "xoa neko.sig -> khoa");
        reset();
        NekoGuard.files = null;
        check(!NekoGuard.checkFile("/game.dat", read(dat)) && NekoGuard.locked, "chua doc neko.sig ma doc tai nguyen -> khoa");

        System.out.println("== credits + ban quyen");
        reset();
        check(NekoGuard.checkCredits("Chuyen the: Neko Yuki. Game goc: Team Salvato", new String[]{"Team Salvato"}) && !NekoGuard.locked, "credits du ten: qua");
        check(!NekoGuard.checkCredits("Chuyen the: AnCap. Game goc: Team Salvato", null) && NekoGuard.locked, "xoa Neko Yuki khoi credits -> khoa");
        reset();
        check(!NekoGuard.checkCredits("Neko Yuki", new String[]{"Team Salvato"}) && NekoGuard.locked, "xoa ten tac gia game goc -> khoa");
        NekoGuard.original = null;
        check(NekoGuard.copyright().equals("© Neko Yuki"), "ban quyen game rieng: " + NekoGuard.copyright());
        NekoGuard.original = "Team Salvato";
        check(NekoGuard.copyright().indexOf("game gốc © Team Salvato") > 0, "ban quyen ban chuyen the giu ten game goc: " + NekoGuard.copyright());
        check(NekoGuard.key(-6) && NekoGuard.locked, "dang khoa: moi phim deu la thoat");
        reset();
        check(!NekoGuard.key(-6), "khong khoa: phim binh thuong");

        System.out.println("== chup phong khoa");
        NekoGuard.trip("thu");
        int[] heights = {96, 128, 176, 240, 320, 480};
        int[][] sizes = {{128, 160}, {176, 220}, {240, 320}, {320, 240}, {360, 640}, {640, 360}, {1280, 720}};
        for (int[] sz : sizes) {
            int w = sz[0], h = sz[1];
            int target = w > h ? h * 78 / 100 : h * 50 / 100, pick = heights[0];
            for (int hh : heights) if (hh <= target) pick = hh;
            byte[] nb = read(new File(art, "nekoyuki_halfbody_" + pick + ".png"));
            Image nk = Image.createImage(nb, 0, nb.length);
            for (int mode = 0; mode < 3; mode++) {
                Image[] chars;
                String tag;
                NekoGuard.en = false;
                if (mode == 2) {
                    // DDLC: Monika + Neko Yuki. Test khong co anh Monika -> o giu cho cung kich thuoc
                    BufferedImage ph = new BufferedImage(nk.getWidth() * 2 / 3, nk.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2 = ph.createGraphics();
                    g2.setColor(new java.awt.Color(90, 90, 110, 200));
                    g2.fillRoundRect(0, 0, ph.getWidth(), ph.getHeight(), 20, 20);
                    g2.setColor(java.awt.Color.WHITE);
                    g2.drawString("Monika", 4, ph.getHeight() / 2);
                    g2.dispose();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    ImageIO.write(ph, "png", bo);
                    byte[] pb = bo.toByteArray();
                    chars = new Image[]{Image.createImage(pb, 0, pb.length), nk};
                    NekoGuard.just = "Just Monika. Just Neko Yuki.";
                    NekoGuard.original = "Team Salvato";
                    tag = "ddlc";
                } else {
                    chars = new Image[]{nk};
                    NekoGuard.just = "Just Neko Yuki.";
                    NekoGuard.original = mode == 0 ? "Scott Cawthon" : null;
                    NekoGuard.en = mode == 1;
                    tag = mode == 0 ? "vi" : "en";
                }
                for (int gl = 0; gl < 2; gl++) {
                    if (gl == 1 && mode != 0) continue;
                    BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    NekoGuard.paint(new Graphics(bi), w, h, chars, null, null, gl == 0 ? 1000 : 2600 * 7 + 60);
                    ImageIO.write(bi, "png", new File(out, w + "x" + h + "_" + tag + (gl == 1 ? "_glitch" : "") + ".png"));
                }
            }
        }
        check(new File(out, "240x320_vi.png").length() > 1000 && new File(out, "1280x720_ddlc.png").length() > 1000, "da chup phong khoa 7 co man x 3 kieu + nhieu hinh");
        System.out.println("TestGuard: " + checks + " kiem tra, " + fails + " loi");
        if (fails > 0) System.exit(1);
    }
}
