package javax.microedition.media;
import java.io.*;
public final class Manager {
    public static java.util.Vector log = new java.util.Vector();
    public static int live = 0;
    static class P implements Player {
        int st = UNREALIZED, loop = 1; int id; int bytes;
        P(InputStream is) { try { byte[] b = new byte[4096]; int n; while ((n = is.read(b)) > 0) bytes += n; } catch (IOException e) {} id = log.size(); live++; }
        public void realize() { if (st < REALIZED) st = REALIZED; }
        public void prefetch() { if (st < PREFETCHED) st = PREFETCHED; }
        public void start() { st = STARTED; log.add("start#" + id + (loop < 0 ? "(loop)" : "") + " " + bytes + "B"); }
        public void stop() { if (st == STARTED) st = PREFETCHED; log.add("stop#" + id); }
        public void deallocate() {}
        public void close() { if (st != CLOSED) { st = CLOSED; live--; } }
        public int getState() { return st; }
        public void setLoopCount(int c) { loop = c; }
        public long setMediaTime(long t) { return t; }
        public void addPlayerListener(PlayerListener l) {}
        public Control getControl(String s) { return null; }
    }
    public static Player createPlayer(InputStream is, String type) throws IOException, MediaException { return new P(is); }
}
