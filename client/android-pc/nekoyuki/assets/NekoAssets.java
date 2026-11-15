package nekoyuki.assets;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tai tai nguyen game theo yeu cau (Android va PC).
 *
 * Game chi mang theo phan cot loi. Khi nguoi choi vao mot phan nao do, goi
 * ensurePack("ten-goi") de tai goi tai nguyen cua phan do. Moi goi duoc doi
 * chieu SHA-256 voi manifest; goi sai ma hash se bi tu choi.
 *
 * Vi du:
 *   NekoAssets assets = new NekoAssets(
 *       "https://github.com/tudaica123gio/neko-yuki-games/releases/download/assets-demo/",
 *       new File(context.getFilesDir(), "assets"));      // PC: new File("assets")
 *   assets.refreshManifest();                            // can mang; neu loi thi loadCachedManifest()
 *   assets.ensurePack("chapter1", progress);             // tai neu chua co
 *   InputStream in = assets.open("chapter1", "img/title.png");
 *
 * Luu y: cac ham co truy cap mang phai goi ngoai luong giao dien (UI thread).
 * Chi dung cu phap Java 7, khong phu thuoc thu vien ngoai.
 */
public final class NekoAssets {

    /** Bao tien do tai. total = -1 neu khong biet truoc. */
    public interface Progress {
        void onProgress(String pack, long done, long total);
    }

    /** Goi tai ve (hoac da cai) khong khop ma hash trong manifest. */
    public static class TamperedException extends IOException {
        private static final long serialVersionUID = 1L;
        public TamperedException(String message) { super(message); }
    }

    private static final class Entry {
        final long size; final String sha256; final String file;
        Entry(long size, String sha256, String file) { this.size = size; this.sha256 = sha256; this.file = file; }
    }

    private static final String KIND = "zip";
    private static final int TIMEOUT_MS = 20000;

    private final String baseUrl;
    private final File dir;
    private final Map<String, Entry> manifest = new HashMap<String, Entry>();
    private final Map<String, ZipFile> open = new HashMap<String, ZipFile>();
    private final Object downloadLock = new Object();

    /**
     * @param baseUrl dia chi thu muc chua manifest.txt, ket thuc bang "/"
     * @param dir     thu muc luu goi tren may nguoi choi
     */
    public NekoAssets(String baseUrl, File dir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir;
    }

    // ---------------------------------------------------------------- manifest

    /** Tai manifest moi nhat tu may chu va luu mot ban de dung khi mat mang. */
    public synchronized void refreshManifest() throws IOException {
        byte[] data = readAll(openUrl(baseUrl + "manifest.txt"), 1 << 20);
        Map<String, Entry> parsed = parseManifest(new String(data, "UTF-8"));
        mkdirs(dir);
        writeFile(new File(dir, "manifest.txt"), data);
        manifest.clear();
        manifest.putAll(parsed);
    }

    /** Dung ban manifest da luu lan truoc. Tra ve false neu chua tung tai. */
    public synchronized boolean loadCachedManifest() {
        File f = new File(dir, "manifest.txt");
        if (!f.isFile()) return false;
        try {
            Map<String, Entry> parsed = parseManifest(new String(readAll(new FileInputStream(f), 1 << 20), "UTF-8"));
            manifest.clear();
            manifest.putAll(parsed);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Map<String, Entry> parseManifest(String text) throws IOException {
        String[] lines = text.split("\n");
        if (lines.length == 0 || !lines[0].startsWith("NEKO-MANIFEST|1|")) {
            throw new IOException("Manifest khong hop le");
        }
        Map<String, Entry> out = new HashMap<String, Entry>();
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].trim().split("\\|");
            if (p.length != 6 || !"pack".equals(p[0]) || !KIND.equals(p[2])) continue;
            if (!isSafeName(p[1]) || !isSafeName(p[5]) || p[4].length() != 64) {
                throw new IOException("Manifest khong hop le o dong " + (i + 1));
            }
            try {
                out.put(p[1], new Entry(Long.parseLong(p[3]), p[4], p[5]));
            } catch (NumberFormatException e) {
                throw new IOException("Manifest khong hop le o dong " + (i + 1));
            }
        }
        return out;
    }

    private static boolean isSafeName(String s) {
        if (s.length() == 0 || s.length() > 200) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || (c == '.' && i > 0);
            if (!ok) return false;
        }
        return true;
    }

    // -------------------------------------------------------------- thong tin

    /** Ten cac goi co trong manifest. */
    public synchronized Set<String> packs() {
        return Collections.unmodifiableSet(new TreeSet<String>(manifest.keySet()));
    }

    /** Dung luong can tai cua goi (byte), -1 neu khong co goi nay. */
    public synchronized long sizeOf(String pack) {
        Entry e = manifest.get(pack);
        return e == null ? -1 : e.size;
    }

    /** Goi da duoc tai ve va dung phien ban trong manifest chua. */
    public synchronized boolean isInstalled(String pack) {
        Entry e = manifest.get(pack);
        if (e == null) return false;
        File f = packFile(pack);
        return f.isFile() && f.length() == e.size && e.sha256.equals(readInstalledSha(pack));
    }

    /** Tong dung luong cac goi dang luu tren may. */
    public synchronized long installedBytes() {
        long total = 0;
        File[] files = new File(dir, "packs").listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith("." + KIND)) total += f.length();
        return total;
    }

    // ------------------------------------------------------------ tai va kiem

    /** Tai goi neu chua co (hoac da co ban moi). Khong lam gi neu da cai dung ban. */
    public void ensurePack(String pack, Progress progress) throws IOException {
        synchronized (downloadLock) {   // moi luc chi tai mot goi
            download(pack, progress);
        }
    }

    private void download(String pack, Progress progress) throws IOException {
        Entry e;
        synchronized (this) {
            e = manifest.get(pack);
            if (e == null) throw new FileNotFoundException("Khong co goi '" + pack + "' trong manifest");
            if (isInstalled(pack)) return;
            closePack(pack);
        }
        File packs = new File(dir, "packs");
        mkdirs(packs);
        File part = new File(packs, pack + ".part");
        MessageDigest md = sha256();
        long done = 0;
        InputStream in = openUrl(baseUrl + e.file);
        try {
            OutputStream out = new FileOutputStream(part);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    done += n;
                    if (done > e.size) break;   // dai hon manifest: chac chan sai
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    if (progress != null) progress.onProgress(pack, done, e.size);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        if (done != e.size || !e.sha256.equals(hex(md.digest()))) {
            part.delete();
            throw new TamperedException("Goi '" + pack + "' tai ve khong khop ma kiem tra, da huy.");
        }
        synchronized (this) {
            File target = packFile(pack);
            target.delete();
            if (!part.renameTo(target)) {
                part.delete();
                throw new IOException("Khong luu duoc goi '" + pack + "'");
            }
            writeFile(shaFile(pack), e.sha256.getBytes("UTF-8"));
        }
    }

    /**
     * Tinh lai SHA-256 cua goi da cai va so voi manifest.
     * Tra ve false neu file tren may da bi sua. Nen goi khi mo game hoac truoc khi vao phan choi.
     */
    public boolean verify(String pack) throws IOException {
        Entry e;
        File f;
        synchronized (this) {
            e = manifest.get(pack);
            f = packFile(pack);
        }
        if (e == null || !f.isFile() || f.length() != e.size) return false;
        MessageDigest md = sha256();
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        } finally {
            in.close();
        }
        return e.sha256.equals(hex(md.digest()));
    }

    /** Xoa goi khoi may de giai phong bo nho. */
    public synchronized void remove(String pack) {
        closePack(pack);
        packFile(pack).delete();
        shaFile(pack).delete();
        deleteTree(new File(new File(dir, "extracted"), pack));
    }

    // ------------------------------------------------------------ doc du lieu

    /** Mo mot file trong goi da cai. Nho dong stream sau khi dung. */
    public synchronized InputStream open(String pack, String name) throws IOException {
        ZipFile zip = open.get(pack);
        if (zip == null) {
            if (!isInstalled(pack)) throw new FileNotFoundException("Goi '" + pack + "' chua duoc tai");
            zip = new ZipFile(packFile(pack));
            open.put(pack, zip);
        }
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new FileNotFoundException(pack + "/" + name);
        return zip.getInputStream(entry);
    }

    /** Doc toan bo mot file trong goi vao bo nho. */
    public byte[] read(String pack, String name) throws IOException {
        return readAll(open(pack, name), Integer.MAX_VALUE);
    }

    /**
     * Giai nen mot file ra dia va tra ve duong dan. Dung cho thu vien chi nhan
     * file that (vi du MediaPlayer cua Android).
     */
    public synchronized File extract(String pack, String name) throws IOException {
        File root = new File(new File(dir, "extracted"), pack);
        File out = new File(root, name);
        String rootPath = root.getCanonicalPath() + File.separator;
        if (!out.getCanonicalPath().startsWith(rootPath)) throw new IOException("Ten file khong hop le: " + name);
        File stamp = new File(root, ".sha");
        String installed = readInstalledSha(pack);
        if (!installed.equals(readText(stamp))) {   // goi da doi phien ban: bo ban giai nen cu
            deleteTree(root);
            mkdirs(root);
            writeFile(stamp, installed.getBytes("UTF-8"));
        }
        if (!out.isFile()) {
            mkdirs(out.getParentFile());
            File tmp = new File(out.getPath() + ".tmp");
            writeFile(tmp, read(pack, name));
            if (!tmp.renameTo(out)) throw new IOException("Khong ghi duoc " + out);
        }
        return out;
    }

    /** Dong cac goi dang mo. Goi khi thoat game. */
    public synchronized void close() {
        for (ZipFile z : open.values()) {
            try { z.close(); } catch (IOException ignored) { }
        }
        open.clear();
    }

    // ---------------------------------------------------------------- noi bo

    private File packFile(String pack) { return new File(new File(dir, "packs"), pack + "." + KIND); }

    private File shaFile(String pack) { return new File(new File(dir, "packs"), pack + ".sha"); }

    private String readInstalledSha(String pack) { return readText(shaFile(pack)); }

    private void closePack(String pack) {
        ZipFile z = open.remove(pack);
        if (z != null) try { z.close(); } catch (IOException ignored) { }
    }

    private static InputStream openUrl(String url) throws IOException {
        // GitHub chuyen huong sang may chu luu tru; HttpURLConnection tu di theo.
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NekoAssets/1");
            int code = c.getResponseCode();
            if (code == 200) return c.getInputStream();
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (code >= 300 && code < 400 && location != null) {
                url = new URL(new URL(url), location).toString();
                continue;
            }
            throw new IOException("May chu tra ve ma " + code);
        }
        throw new IOException("Chuyen huong qua nhieu lan");
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > limit) throw new IOException("Du lieu qua lon");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String readText(File f) {
        if (!f.isFile()) return "";
        try {
            return new String(readAll(new FileInputStream(f), 4096), "UTF-8").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(f);
        try { out.write(data); } finally { out.close(); }
    }

    private static void mkdirs(File d) throws IOException {
        if (!d.isDirectory() && !d.mkdirs() && !d.isDirectory()) throw new IOException("Khong tao duoc thu muc " + d);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("May khong ho tro SHA-256");
        }
    }

    private static String hex(byte[] b) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = digits[(b[i] >> 4) & 15];
            out[i * 2 + 1] = digits[b[i] & 15];
        }
        return new String(out);
    }
}
