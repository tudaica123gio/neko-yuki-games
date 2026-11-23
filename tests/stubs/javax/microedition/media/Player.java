package javax.microedition.media;
public interface Player {
    int CLOSED = 0, UNREALIZED = 100, REALIZED = 200, PREFETCHED = 300, STARTED = 400;
    void realize() throws MediaException; void prefetch() throws MediaException; void start() throws MediaException;
    void stop() throws MediaException; void deallocate(); void close(); int getState();
    void setLoopCount(int c); long setMediaTime(long t) throws MediaException;
    void addPlayerListener(PlayerListener l); Control getControl(String s);
}
