package javax.microedition.io;
import java.io.IOException;
import java.io.InputStream;
public interface HttpConnection extends Connection {
    String GET = "GET";
    int HTTP_OK = 200;
    void setRequestMethod(String method) throws IOException;
    void setRequestProperty(String key, String value) throws IOException;
    int getResponseCode() throws IOException;
    String getHeaderField(String name) throws IOException;
    InputStream openInputStream() throws IOException;
    long getLength();
}
