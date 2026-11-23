package javax.microedition.io;
public interface ServerSocketConnection extends StreamConnectionNotifier {
    String getLocalAddress() throws java.io.IOException;
    int getLocalPort() throws java.io.IOException;
}
