package nekoyuki.assets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/**
 * Tai tai nguyen game theo yeu cau cho J2ME (MIDP 2.0 + JSR-75).
 *
 * Game chi mang theo phan cot loi trong file JAR. Khi nguoi choi vao mot phan
 * nao do, goi ensurePack("ten-goi") de tai goi cua phan do ve the nho. Moi goi
 * duoc doi chieu SHA-256 voi manifest; goi sai ma hash se bi tu choi.
 *
 * Vi du:
 *   NekoAssetsME assets = new NekoAssetsME(
 *       "https://github.com/tudaica123gio/neko-yuki-games/releases/download/assets-demo/",
 *       NekoAssetsME.defaultDir("demo"));
 *   try { assets.refreshManifest(); } catch (IOException e) { assets.loadCachedManifest(); }
 *   assets.ensurePack("chapter1", progress);
 *   byte[] png = assets.read("chapter1", "img/title.png");
 *   Image img = Image.createImage(png, 0, png.length);
 *
 * Luu y:
 * - Moi ham co truy cap mang hoac file phai goi trong mot Thread rieng,
 *   khong goi truc tiep trong commandAction / keyPressed / paint.
 * - Can quyen javax.microedition.io.Connector.http, .https va
 *   javax.microedition.io.Connector.file.read, .file.write trong file JAD.
 * - Chi dung API cua CLDC 1.1: khong generics, khong StringBuilder.
 */
public final class NekoAssetsME {

    /** Bao tien do tai (byte da tai / tong so byte). */
    public interface Progress {
        void onProgress(String pack, int done, int total);
    }

    /** Goi tai ve (hoac da cai) khong khop ma hash trong manifest. */
    public static class TamperedException extends IOException {
        public TamperedException(String message) {
            super(message);
        }
    }

    private static final String KIND = "npk";
    private static final int MANIFEST_LIMIT = 256 * 1024;

    private final String baseUrl;
    private final String dir;
    /** ten goi -> String[] { kich thuoc, sha256, ten file tren may chu } */
    private final Hashtable manifest = new Hashtable();
    /** ten goi -> Hashtable (ten file -> int[] { vi tri, kich thuoc }) */
    private final Hashtable indexes = new Hashtable();
    private final Object downloadLock = new Object();

    /**
     * @param baseUrl dia chi thu muc chua manifest.txt
     * @param dir     thu muc luu goi, dang "file:///.../", xem defaultDir()
     */
    public NekoAssetsME(String baseUrl, String dir) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.dir = dir.endsWith("/") ? dir : dir + "/";
    }

    /** Thu muc luu mac dinh tren the nho: <goc>/NekoYuki/<gameId>/ */
    public static String defaultDir(String gameId) {
        String root = null;
        try {
            root = System.getProperty("fileconn.dir.private");
        } catch (Exception e) {
            root = null;
        }
        if (root == null || root.length() == 0) {
            Enumeration roots = FileSystemRegistry.listRoots();
            if (roots.hasMoreElements()) root = "file:///" + (String) roots.nextElement();
        }
        if (root == null) root = "file:///root/";
        if (!root.endsWith("/")) root = root + "/";
        return root + "NekoYuki/" + gameId + "/";
    }

    // ---------------------------------------------------------------- manifest

    /** Tai manifest moi nhat va luu mot ban de dung khi mat mang. */
    public synchronized void refreshManifest() throws IOException {
        byte[] data = httpBytes(baseUrl + "manifest.txt", MANIFEST_LIMIT);
        Hashtable parsed = parseManifest(new String(data, "UTF-8"));
        mkdirs(dir);
        writeFile(dir + "manifest.txt", data);
        replaceManifest(parsed);
    }

    /** Dung ban manifest da luu lan truoc. Tra ve false neu chua tung tai. */
    public synchronized boolean loadCachedManifest() {
        try {
            byte[] data = readFile(dir + "manifest.txt", MANIFEST_LIMIT);
            if (data == null) return false;
            replaceManifest(parseManifest(new String(data, "UTF-8")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void replaceManifest(Hashtable parsed) {
        manifest.clear();
        Enumeration keys = parsed.keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            manifest.put(k, parsed.get(k));
        }
    }

    private static Hashtable parseManifest(String text) throws IOException {
        Vector lines = split(text, '\n');
        if (lines.size() == 0 || !((String) lines.elementAt(0)).startsWith("NEKO-MANIFEST|1|")) {
            throw new IOException("Manifest khong hop le");
        }
        Hashtable out = new Hashtable();
        for (int i = 1; i < lines.size(); i++) {
            Vector p = split(((String) lines.elementAt(i)).trim(), '|');
            if (p.size() != 6 || !"pack".equals(p.elementAt(0)) || !KIND.equals(p.elementAt(2))) continue;
            String pack = (String) p.elementAt(1);
            String size = (String) p.elementAt(3);
            String sha = (String) p.elementAt(4);
            String file = (String) p.elementAt(5);
            if (!isSafeName(pack) || !isSafeName(file) || sha.length() != 64) {
                throw new IOException("Manifest khong hop le o dong " + (i + 1));
            }
            try {
                if (Integer.parseInt(size) < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new IOException("Manifest khong hop le o dong " + (i + 1));
            }
            out.put(pack, new String[] { size, sha, file });
        }
        return out;
    }

    // -------------------------------------------------------------- thong tin

    /** Ten cac goi co trong manifest (Vector cac String). */
    public synchronized Vector packs() {
        Vector v = new Vector();
        Enumeration keys = manifest.keys();
        while (keys.hasMoreElements()) v.addElement(keys.nextElement());
        return v;
    }

    /** Dung luong can tai cua goi (byte), -1 neu khong co goi nay. */
    public synchronized int sizeOf(String pack) {
        String[] e = (String[]) manifest.get(pack);
        return e == null ? -1 : Integer.parseInt(e[0]);
    }

    /** Goi da duoc tai ve va dung phien ban trong manifest chua. */
    public synchronized boolean isInstalled(String pack) {
        String[] e = (String[]) manifest.get(pack);
        if (e == null) return false;
        try {
            if (fileSize(packUrl(pack)) != Integer.parseInt(e[0])) return false;
            byte[] sha = readFile(shaUrl(pack), 256);
            return sha != null && e[1].equals(new String(sha, "UTF-8").trim());
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------ tai va kiem

    /** Tai goi neu chua co (hoac da co ban moi). Khong lam gi neu da cai dung ban. */
    public void ensurePack(String pack, Progress progress) throws IOException {
        synchronized (downloadLock) {   // moi luc chi tai mot goi
            download(pack, progress);
        }
    }

    private void download(String pack, Progress progress) throws IOException {
        String[] e;
        synchronized (this) {
            e = (String[]) manifest.get(pack);
            if (e == null) throw new IOException("Khong co goi '" + pack + "' trong manifest");
            if (isInstalled(pack)) return;
            indexes.remove(pack);
        }
        int size = Integer.parseInt(e[0]);
        mkdirs(dir);
        String partName = pack + ".part";
        Sha256 sha = new Sha256();
        int done = 0;

        HttpConnection http = httpGet(baseUrl + e[2]);
        FileConnection part = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            part = (FileConnection) Connector.open(dir + partName, Connector.READ_WRITE);
            if (part.exists()) part.delete();
            part.create();
            in = http.openInputStream();
            out = part.openOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                done += n;
                if (done > size) break;   // dai hon manifest: chac chan sai
                out.write(buf, 0, n);
                sha.update(buf, 0, n);
                if (progress != null) progress.onProgress(pack, done, size);
            }
            out.close();
            out = null;
            if (done != size || !e[1].equals(sha.digestHex())) {
                part.delete();
                throw new TamperedException("Goi '" + pack + "' tai ve khong khop ma kiem tra, da huy.");
            }
            synchronized (this) {
                deleteFile(packUrl(pack));
                part.rename(pack + "." + KIND);
                writeFile(shaUrl(pack), e[1].getBytes("UTF-8"));
            }
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            try { http.close(); } catch (Exception ignored) { }
            if (part != null) try { part.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * Tinh lai SHA-256 cua goi da cai va so voi manifest.
     * Tra ve false neu file tren may da bi sua.
     */
    public boolean verify(String pack) throws IOException {
        String[] e;
        synchronized (this) {
            e = (String[]) manifest.get(pack);
        }
        if (e == null) return false;
        FileConnection fc = (FileConnection) Connector.open(packUrl(pack), Connector.READ);
        InputStream in = null;
        try {
            if (!fc.exists() || fc.fileSize() != Integer.parseInt(e[0])) return false;
            in = fc.openInputStream();
            Sha256 sha = new Sha256();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) sha.update(buf, 0, n);
            return e[1].equals(sha.digestHex());
        } finally {
            closeQuietly(in);
            fc.close();
        }
    }

    /** Xoa goi khoi may de giai phong bo nho. */
    public synchronized void remove(String pack) {
        indexes.remove(pack);
        try { deleteFile(packUrl(pack)); } catch (Exception ignored) { }
        try { deleteFile(shaUrl(pack)); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ doc du lieu

    /** Mo mot file trong goi da cai. Nho dong stream sau khi dung. */
    public InputStream open(String pack, String name) throws IOException {
        int[] pos = locate(pack, name);
        FileConnection fc = (FileConnection) Connector.open(packUrl(pack), Connector.READ);
        InputStream in = null;
        try {
            in = fc.openInputStream();
            skipFully(in, pos[0]);
            return new Bounded(in, fc, pos[1]);
        } catch (IOException ex) {
            closeQuietly(in);
            fc.close();
            throw ex;
        }
    }

    /** Doc toan bo mot file trong goi vao bo nho (dung cho Image.createImage). */
    public byte[] read(String pack, String name) throws IOException {
        InputStream in = open(pack, name);
        try {
            int size = in.available();
            byte[] data = new byte[size];
            int off = 0;
            while (off < size) {
                int n = in.read(data, off, size - off);
                if (n < 0) throw new IOException("Goi '" + pack + "' bi hong");
                off += n;
            }
            return data;
        } finally {
            in.close();
        }
    }

    /** Co file nay trong goi da cai khong. */
    public boolean has(String pack, String name) {
        try {
            locate(pack, name);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private synchronized int[] locate(String pack, String name) throws IOException {
        Hashtable index = (Hashtable) indexes.get(pack);
        if (index == null) {
            if (!isInstalled(pack)) throw new IOException("Goi '" + pack + "' chua duoc tai");
            index = readIndex(packUrl(pack));
            indexes.put(pack, index);
        }
        int[] pos = (int[]) index.get(name);
        if (pos == null) throw new IOException("Khong co file " + pack + "/" + name);
        return pos;
    }

    /** Doc muc luc cua file NPK: ten file -> { vi tri, kich thuoc }. */
    private static Hashtable readIndex(String url) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ);
        DataInputStream din = null;
        try {
            long fileSize = fc.fileSize();
            din = new DataInputStream(fc.openInputStream());
            if (din.readByte() != 'N' || din.readByte() != 'P' || din.readByte() != 'K' || din.readByte() != '1') {
                throw new IOException("Goi khong dung dinh dang");
            }
            int count = din.readInt();
            if (count < 0 || count > 100000) throw new IOException("Goi khong dung dinh dang");
            String[] names = new String[count];
            int[] sizes = new int[count];
            long header = 8;
            for (int i = 0; i < count; i++) {
                int len = din.readUnsignedShort();
                byte[] nb = new byte[len];
                din.readFully(nb);
                names[i] = new String(nb, "UTF-8");
                sizes[i] = din.readInt();
                if (sizes[i] < 0) throw new IOException("Goi khong dung dinh dang");
                header += 2 + len + 4;
            }
            Hashtable index = new Hashtable();
            long offset = header;
            for (int i = 0; i < count; i++) {
                index.put(names[i], new int[] { (int) offset, sizes[i] });
                offset += sizes[i];
            }
            if (offset != fileSize) throw new IOException("Goi khong dung dinh dang");
            return index;
        } finally {
            closeQuietly(din);
            fc.close();
        }
    }

    // ---------------------------------------------------------------- noi bo

    private String packUrl(String pack) { return dir + pack + "." + KIND; }

    private String shaUrl(String pack) { return dir + pack + ".sha"; }

    /** Mo ket noi HTTP, tu di theo chuyen huong (GitHub chuyen sang may chu luu tru). */
    private static HttpConnection httpGet(String url) throws IOException {
        for (int hop = 0; hop < 6; hop++) {
            HttpConnection c = (HttpConnection) Connector.open(url, Connector.READ, true);
            int code;
            String location;
            try {
                c.setRequestMethod(HttpConnection.GET);
                c.setRequestProperty("User-Agent", "NekoAssetsME/1");
                code = c.getResponseCode();
                if (code == HttpConnection.HTTP_OK) return c;
                location = c.getHeaderField("Location");
                if (location == null) location = c.getHeaderField("location");
            } catch (IOException ex) {
                try { c.close(); } catch (Exception ignored) { }
                throw ex;
            }
            c.close();
            if (code >= 300 && code < 400 && location != null) {
                url = resolve(url, location);
                continue;
            }
            throw new IOException("May chu tra ve ma " + code);
        }
        throw new IOException("Chuyen huong qua nhieu lan");
    }

    private static String resolve(String base, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) return location;
        int hostStart = base.indexOf("://") + 3;
        int pathStart = base.indexOf('/', hostStart);
        String origin = pathStart < 0 ? base : base.substring(0, pathStart);
        if (location.startsWith("/")) return origin + location;
        int query = base.indexOf('?');
        String path = query < 0 ? base : base.substring(0, query);
        int slash = path.lastIndexOf('/');
        if (slash < hostStart) return origin + "/" + location;
        return path.substring(0, slash + 1) + location;
    }

    private static byte[] httpBytes(String url, int limit) throws IOException {
        HttpConnection c = httpGet(url);
        InputStream in = null;
        try {
            in = c.openInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > limit) throw new IOException("Du lieu qua lon");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            closeQuietly(in);
            try { c.close(); } catch (Exception ignored) { }
        }
    }

    private static long fileSize(String url) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ);
        try {
            return fc.exists() ? fc.fileSize() : -1;
        } finally {
            fc.close();
        }
    }

    /** Tra ve null neu file khong ton tai. */
    private static byte[] readFile(String url, int limit) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ);
        InputStream in = null;
        try {
            if (!fc.exists()) return null;
            in = fc.openInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > limit) throw new IOException("Du lieu qua lon");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            closeQuietly(in);
            fc.close();
        }
    }

    private static void writeFile(String url, byte[] data) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
        OutputStream out = null;
        try {
            if (fc.exists()) fc.delete();
            fc.create();
            out = fc.openOutputStream();
            out.write(data);
        } finally {
            closeQuietly(out);
            fc.close();
        }
    }

    private static void deleteFile(String url) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
        try {
            if (fc.exists()) fc.delete();
        } finally {
            fc.close();
        }
    }

    /** Tao lan luot tung cap thu muc (JSR-75 chi tao duoc mot cap moi lan). */
    private static void mkdirs(String dirUrl) throws IOException {
        int rootEnd = dirUrl.indexOf('/', 8);   // sau "file:///<goc>"
        if (rootEnd < 0) return;
        int pos = rootEnd + 1;
        while (true) {
            int next = dirUrl.indexOf('/', pos);
            if (next < 0) break;
            FileConnection fc = (FileConnection) Connector.open(dirUrl.substring(0, next + 1), Connector.READ_WRITE);
            try {
                if (!fc.exists()) fc.mkdir();
            } finally {
                fc.close();
            }
            pos = next + 1;
        }
    }

    private static void skipFully(InputStream in, int count) throws IOException {
        byte[] scratch = null;
        while (count > 0) {
            long skipped = in.skip(count);
            if (skipped <= 0) {   // mot so may khong ho tro skip: doc bo
                if (scratch == null) scratch = new byte[4096];
                int n = in.read(scratch, 0, count < scratch.length ? count : scratch.length);
                if (n < 0) throw new IOException("Goi bi cat ngan");
                skipped = n;
            }
            count -= (int) skipped;
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) try { in.close(); } catch (Exception ignored) { }
    }

    private static void closeQuietly(OutputStream out) {
        if (out != null) try { out.close(); } catch (Exception ignored) { }
    }

    private static Vector split(String s, char sep) {
        Vector v = new Vector();
        int start = 0;
        while (true) {
            int i = s.indexOf(sep, start);
            if (i < 0) {
                if (start < s.length()) v.addElement(s.substring(start));
                break;
            }
            v.addElement(s.substring(start, i));
            start = i + 1;
        }
        return v;
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

    /** Stream chi doc dung phan cua mot file ben trong goi. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private final FileConnection fc;
        private int left;

        Bounded(InputStream in, FileConnection fc, int size) {
            this.in = in;
            this.fc = fc;
            this.left = size;
        }

        public int read() throws IOException {
            if (left <= 0) return -1;
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            if (len > left) len = left;
            int n = in.read(b, off, len);
            if (n > 0) left -= n;
            return n;
        }

        public int available() {
            return left;
        }

        public void close() throws IOException {
            try {
                in.close();
            } finally {
                fc.close();
            }
        }
    }
}
