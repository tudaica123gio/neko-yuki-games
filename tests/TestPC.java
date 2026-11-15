import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import nekoyuki.assets.NekoAssets;

public class TestPC {
    static int checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError("FAIL: " + what); System.out.println("  ok  " + what); }
    static void get(String u) throws IOException { new URL(u).openStream().close(); }
    static List<String> walk(File root, File d, List<String> out) {
        File[] kids = d.listFiles(); Arrays.sort(kids);
        for (File k : kids) { if (k.isDirectory()) walk(root, k, out); else out.add(root.toURI().relativize(k.toURI()).getPath()); }
        return out;
    }
    public static void main(String[] a) throws Exception {
        String phase = a[0], server = a[1]; File src = new File(a[2]); File dir = new File(a[3]);
        String base = server + "/dl/";
        final long[] last = {0, 0}; final int[] calls = {0};
        NekoAssets.Progress p = new NekoAssets.Progress() { public void onProgress(String pk, long d, long t) { last[0] = d; last[1] = t; calls[0]++; } };
        NekoAssets as = new NekoAssets(base, dir);
        if (phase.equals("1")) {
            check(!as.loadCachedManifest(), "chua co manifest luu san");
            as.refreshManifest();
            check(as.packs().toString().equals("[chapter1, chapter2]"), "manifest co 2 goi: " + as.packs());
            check(!as.isInstalled("chapter1") && !as.isInstalled("chapter2"), "ban dau chua co goi nao");
            as.ensurePack("chapter1", p);
            check(as.isInstalled("chapter1"), "chapter1 da tai");
            check(!as.isInstalled("chapter2"), "chapter2 KHONG bi tai theo (dung kieu theo yeu cau)");
            check(calls[0] > 0 && last[0] == last[1] && last[1] == as.sizeOf("chapter1"), "bao tien do den 100%");
            File c1 = new File(src, "chapter1");
            for (String name : walk(c1, c1, new ArrayList<String>())) {
                check(Arrays.equals(as.read("chapter1", name), Files.readAllBytes(new File(c1, name).toPath())), "noi dung khop: " + name);
            }
            check(as.verify("chapter1"), "verify() dung voi goi nguyen ven");
            int before = calls[0]; as.ensurePack("chapter1", p);
            check(calls[0] == before, "goi da co thi khong tai lai");
            File ex = as.extract("chapter1", "img/title.png");
            check(Arrays.equals(Files.readAllBytes(ex.toPath()), Files.readAllBytes(new File(c1, "img/title.png").toPath())), "extract() ra file dung");
            boolean rejected = false;
            try { as.extract("chapter1", "../../evil.txt"); } catch (IOException e) { rejected = true; }
            check(rejected, "tu choi ten file thoat thu muc (../)");
            try { as.read("chapter1", "khong-co.png"); check(false, "x"); } catch (FileNotFoundException e) { check(true, "file khong ton tai -> bao loi ro"); }
            // --- sua file tren may nguoi choi
            as.close();
            File zip = new File(dir, "packs/chapter1.zip");
            byte[] b = Files.readAllBytes(zip.toPath()); b[b.length / 3] ^= 0x10; Files.write(zip.toPath(), b);
            check(!as.verify("chapter1"), "verify() PHAT HIEN file tren may bi sua");
            as.remove("chapter1");
            check(!as.isInstalled("chapter1") && !zip.exists(), "remove() xoa goi");
            // --- goi tren duong truyen / may chu bi sua
            get(server + "/_tamper/on");
            boolean tampered = false;
            try { as.ensurePack("chapter1", p); } catch (NekoAssets.TamperedException e) { tampered = true; }
            check(tampered, "goi bi sua tren may chu -> TamperedException");
            check(!as.isInstalled("chapter1") && new File(dir, "packs").list().length == 0, "goi bi sua khong duoc luu lai");
            get(server + "/_tamper/off");
            as.ensurePack("chapter1", p);
            check(as.isInstalled("chapter1") && as.verify("chapter1"), "tai lai goi sach thanh cong");
            // --- mat mang
            NekoAssets off = new NekoAssets("http://127.0.0.1:9/", dir);
            boolean netFail = false;
            try { off.refreshManifest(); } catch (IOException e) { netFail = true; }
            check(netFail && off.loadCachedManifest(), "mat mang -> dung manifest da luu");
            check(off.isInstalled("chapter1") && off.read("chapter1", "script.txt").length > 0, "mat mang van doc duoc goi da tai");
            boolean needNet = false;
            try { off.ensurePack("chapter2", p); } catch (IOException e) { needNet = !(e instanceof NekoAssets.TamperedException); }
            check(needNet, "mat mang + goi chua tai -> bao loi mang (khong phai loi sua file)");
            off.close();
        } else {
            check(as.loadCachedManifest() && as.isInstalled("chapter1"), "lan chay sau: goi cu van con");
            String old = new String(as.read("chapter1", "script.txt"), "UTF-8");
            as.refreshManifest();
            check(!as.isInstalled("chapter1"), "co ban cap nhat -> goi cu bi coi la chua cai");
            as.ensurePack("chapter1", p);
            String now = new String(as.read("chapter1", "script.txt"), "UTF-8");
            check(!now.equals(old) && now.contains("BAN CAP NHAT"), "tai ban cap nhat va doc duoc noi dung moi");
            check(as.verify("chapter1"), "ban cap nhat qua kiem tra");
            check(as.installedBytes() == as.sizeOf("chapter1"), "khong con file thua tren may");
        }
        as.close();
        System.out.println("PC phase " + phase + ": " + checks + " kiem tra deu dat");
    }
}
