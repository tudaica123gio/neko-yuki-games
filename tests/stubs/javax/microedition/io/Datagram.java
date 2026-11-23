package javax.microedition.io;

/** Stub toi gian (ban that ke thua DataInput / DataOutput). */
public interface Datagram {
    String getAddress();
    byte[] getData();
    int getLength();
    int getOffset();
    void setAddress(String addr) throws java.io.IOException;
    void setAddress(Datagram reference);
    void setLength(int len);
    void setData(byte[] buffer, int offset, int len);
    void reset();
}
