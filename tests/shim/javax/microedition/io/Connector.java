package javax.microedition.io;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.microedition.io.file.FileConnection;

/** Ban gia lap toi thieu cua API J2ME de chay thu tren may tinh. Hanh vi co y nghiem nhu JSR-75. */
public class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;
    public static int openFiles = 0, openHttp = 0;

    public static Connection open(String name) throws IOException { return open(name, READ_WRITE, false); }
    public static Connection open(String name, int mode) throws IOException { return open(name, mode, false); }
    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name.startsWith("http://") || name.startsWith("https://")) return new Http(name);
        if (name.startsWith("file:///")) return new FileImpl(name, mode);
        throw new IOException("unsupported: " + name);
    }

    static final class Http implements HttpConnection {
        final HttpURLConnection c; boolean closed;
        Http(String url) throws IOException {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);   // may that khong tu chuyen huong
            c.setConnectTimeout(5000); c.setReadTimeout(5000);
            openHttp++;
        }
        public void setRequestMethod(String m) throws IOException { c.setRequestMethod(m); }
        public void setRequestProperty(String k, String v) { c.setRequestProperty(k, v); }
        public int getResponseCode() throws IOException { return c.getResponseCode(); }
        public String getHeaderField(String n) { return c.getHeaderField(n); }
        public InputStream openInputStream() throws IOException { return c.getInputStream(); }
        public long getLength() { return c.getContentLengthLong(); }
        public void close() { if (!closed) { closed = true; openHttp--; c.disconnect(); } }
    }

    static final class FileImpl implements FileConnection {
        File f; final int mode; boolean closed;
        FileImpl(String url, int mode) throws IOException {
            String root = System.getProperty("shim.root");
            if (root == null) throw new IOException("shim.root chua dat");
            String rel = url.substring("file:///".length());
            if (rel.contains("..")) throw new IOException("bad path");
            this.f = new File(root, rel); this.mode = mode; openFiles++;
        }
        void needWrite() throws IOException { if ((mode & WRITE) == 0) throw new IOException("mo chi-doc ma lai ghi: " + f); }
        public boolean exists() { return f.exists(); }
        public boolean isDirectory() { return f.isDirectory(); }
        public void create() throws IOException {
            needWrite();
            if (f.exists()) throw new IOException("create(): da ton tai " + f);
            if (!f.getParentFile().isDirectory()) throw new IOException("create(): chua co thu muc cha " + f);
            if (!f.createNewFile()) throw new IOException("create() that bai " + f);
        }
        public void mkdir() throws IOException {
            needWrite();
            if (!f.getParentFile().isDirectory()) throw new IOException("mkdir(): chua co thu muc cha " + f);
            if (!f.mkdir()) throw new IOException("mkdir() that bai " + f);
        }
        public void delete() throws IOException { needWrite(); if (!f.delete()) throw new IOException("delete() that bai " + f); }
        public void rename(String n) throws IOException {
            needWrite();
            if (n.indexOf('/') >= 0) throw new IllegalArgumentException("rename(): ten khong duoc chua '/'");
            File t = new File(f.getParentFile(), n);
            if (t.exists()) throw new IOException("rename(): dich da ton tai " + t);
            if (!f.renameTo(t)) throw new IOException("rename() that bai");
            f = t;
        }
        public long fileSize() throws IOException { return f.isFile() ? f.length() : -1; }
        public InputStream openInputStream() throws IOException {
            if ((mode & READ) == 0) throw new IOException("mo chi-ghi ma lai doc");
            return new FileInputStream(f);
        }
        public OutputStream openOutputStream() throws IOException {
            needWrite();
            if (!f.isFile()) throw new IOException("openOutputStream(): file chua ton tai " + f);
            return new FileOutputStream(f);
        }
        public void close() { if (!closed) { closed = true; openFiles--; } }
    }
}
