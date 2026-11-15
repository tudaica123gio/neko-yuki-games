import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import javax.microedition.io.Connector;
import nekoyuki.assets.NekoAssetsME;
import nekoyuki.assets.Sha256;

public class TestME {
    static int checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError("FAIL: " + what); System.out.println("  ok  " + what); }
    static void get(String u) throws IOException { new URL(u).openStream().close(); }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    static List<String> walk(File root, File d, List<String> out) {
        File[] kids = d.listFiles(); Arrays.sort(kids);
        for (File k : kids) { if (k.isDirectory()) walk(root, k, out); else out.add(root.toURI().relativize(k.toURI()).getPath()); }
        return out;
    }
    static void noLeaks() { check(Connector.openFiles == 0 && Connector.openHttp == 0, "khong ro ri ket noi (file=" + Connector.openFiles + ", http=" + Connector.openHttp + ")"); }

    public static void main(String[] a) throws Exception {
        String phase = a[0], server = a[1]; File src = new File(a[2]);
        if (phase.equals("sha")) {
            Sha256 s = new Sha256();
            check(s.digestHex().equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"), "SHA-256 chuoi rong");
            s.update("abc".getBytes(), 0, 3);
            check(s.digestHex().equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA-256 'abc'");
            byte[] m = new byte[1000000]; Arrays.fill(m, (byte) 'a'); s.update(m, 0, m.length);
            check(s.digestHex().equals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0"), "SHA-256 mot trieu chu 'a'");
            Random r = new Random(42); boolean all = true;
            for (int t = 0; t < 400; t++) {
                byte[] d = new byte[r.nextInt(700)]; r.nextBytes(d);
                int off = 0; while (off < d.length) { int n = Math.min(d.length - off, 1 + r.nextInt(130)); s.update(d, off, n); off += n; }
                if (!s.digestHex().equals(hex(MessageDigest.getInstance("SHA-256").digest(d)))) all = false;
            }
            check(all, "400 mau ngau nhien khop voi SHA-256 chuan cua Java");
            System.out.println("SHA: " + checks + " kiem tra deu dat"); return;
        }
        File root = new File(System.getProperty("shim.root"));
        String base = server + "/dl/";
        String dir = NekoAssetsME.defaultDir("demo");
        final int[] last = {0, 0, 0};
        NekoAssetsME.Progress p = new NekoAssetsME.Progress() { public void onProgress(String pk, int d, int t) { last[0] = d; last[1] = t; last[2]++; } };
        NekoAssetsME as = new NekoAssetsME(base, dir);
        File local = new File(root, "root1/NekoYuki/demo");
        if (phase.equals("1")) {
            check(dir.equals("file:///root1/NekoYuki/demo/"), "thu muc mac dinh: " + dir);
            check(!as.loadCachedManifest(), "chua co manifest luu san");
            as.refreshManifest();
            check(as.packs().size() == 2 && as.packs().contains("chapter1") && as.packs().contains("chapter2"), "manifest co 2 goi");
            check(local.isDirectory(), "tu tao thu muc nhieu cap tren the nho");
            check(!as.isInstalled("chapter1"), "ban dau chua co goi");
            as.ensurePack("chapter1", p);
            check(as.isInstalled("chapter1") && !as.isInstalled("chapter2"), "chi chapter1 duoc tai (theo yeu cau)");
            check(last[2] > 0 && last[0] == last[1] && last[1] == as.sizeOf("chapter1"), "bao tien do den 100%");
            File c1 = new File(src, "chapter1");
            for (String name : walk(c1, c1, new ArrayList<String>())) {
                check(Arrays.equals(as.read("chapter1", name), Files.readAllBytes(new File(c1, name).toPath())), "noi dung khop: " + name);
            }
            InputStream in = as.open("chapter1", "script.txt"); int n = 0; while (in.read() != -1) n++; in.close();
            check(n == new File(c1, "script.txt").length(), "open() doc tung byte dung do dai, khong lan sang file ke");
            check(as.has("chapter1", "img/room.png") && !as.has("chapter1", "nope.png"), "has() dung");
            check(as.verify("chapter1"), "verify() dung voi goi nguyen ven");
            int before = last[2]; as.ensurePack("chapter1", p);
            check(last[2] == before, "goi da co thi khong tai lai");
            noLeaks();
            File npk = new File(local, "chapter1.npk");
            byte[] b = Files.readAllBytes(npk.toPath()); b[b.length / 3] ^= 0x10; Files.write(npk.toPath(), b);
            check(!as.verify("chapter1"), "verify() PHAT HIEN file tren the nho bi sua");
            as.remove("chapter1");
            check(!as.isInstalled("chapter1") && !npk.exists(), "remove() xoa goi");
            get(server + "/_tamper/on");
            boolean tampered = false;
            try { as.ensurePack("chapter1", p); } catch (NekoAssetsME.TamperedException e) { tampered = true; }
            check(tampered, "goi bi sua tren may chu -> TamperedException");
            check(!as.isInstalled("chapter1") && !npk.exists() && !new File(local, "chapter1.part").exists(), "goi bi sua khong duoc luu lai");
            get(server + "/_tamper/off");
            as.ensurePack("chapter1", p);
            check(as.isInstalled("chapter1") && as.verify("chapter1"), "tai lai goi sach thanh cong");
            noLeaks();
            NekoAssetsME off = new NekoAssetsME("http://127.0.0.1:9/", dir);
            boolean netFail = false;
            try { off.refreshManifest(); } catch (IOException e) { netFail = true; }
            check(netFail && off.loadCachedManifest(), "mat mang -> dung manifest da luu");
            check(off.isInstalled("chapter1") && off.read("chapter1", "script.txt").length > 0, "mat mang van doc duoc goi da tai");
            noLeaks();
        } else {
            check(as.loadCachedManifest() && as.isInstalled("chapter1"), "lan chay sau: goi cu van con");
            String old = new String(as.read("chapter1", "script.txt"), "UTF-8");
            as.refreshManifest();
            check(!as.isInstalled("chapter1"), "co ban cap nhat -> goi cu bi coi la chua cai");
            as.ensurePack("chapter1", p);
            String now = new String(as.read("chapter1", "script.txt"), "UTF-8");
            check(!now.equals(old) && now.contains("BAN CAP NHAT"), "tai ban cap nhat va doc duoc noi dung moi");
            check(as.verify("chapter1"), "ban cap nhat qua kiem tra");
            noLeaks();
        }
        System.out.println("J2ME phase " + phase + ": " + checks + " kiem tra deu dat");
    }
}
