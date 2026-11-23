package javax.microedition.io;

import java.io.*;
import java.net.*;

/** Stub chi de bien dich / test tren PC: socket:// dung java.net (khong dua vao jar). */
public class Connector {
    public static final int READ = 1, WRITE = 2, READ_WRITE = 3;
    /** test: dia chi "may that" gia lap cho server (mac dinh 127.0.0.1) */
    public static String fakeLocal = "127.0.0.1";

    public static Connection open(String name) throws IOException { return open(name, READ_WRITE, false); }
    public static Connection open(String name, int mode) throws IOException { return open(name, mode, false); }

    /** test: goi broadcast 255.255.255.255 duoc chuyen ve 127.0.0.1 (cung may) */
    public static boolean bcastLocal = true;
    /** test: dia chi tra ve khi hoi IP qua "datagram://8.8.8.8:53" (null = hoi he thong) */
    public static String fakeRouteIp = null;

    public static Connection open(String name, int mode, boolean timeouts) throws IOException {
        if (name.startsWith("datagram://")) return openDatagram(name.substring(11));
        if (!name.startsWith("socket://")) throw new ConnectionNotFoundException(name);
        String rest = name.substring(9);
        int c = rest.lastIndexOf(':');
        String host = rest.substring(0, c);
        int port = Integer.parseInt(rest.substring(c + 1));
        if (host.length() == 0) {
            final ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            return new ServerSocketConnection() {
                public StreamConnection acceptAndOpen() throws IOException { return wrap(ss.accept()); }
                public String getLocalAddress() { return fakeLocal; }
                public int getLocalPort() { return ss.getLocalPort(); }
                public void close() throws IOException { ss.close(); }
            };
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        return wrap(s);
    }

    static class DG implements Datagram {
        byte[] buf; int off, len; String addr;
        DG(byte[] b, int l, String a) { buf = b; len = l; addr = a; }
        public String getAddress() { return addr; }
        public byte[] getData() { return buf; }
        public int getLength() { return len; }
        public int getOffset() { return off; }
        public void setAddress(String a) { addr = a; }
        public void setAddress(Datagram r) { addr = r.getAddress(); }
        public void setLength(int l) { len = l; }
        public void setData(byte[] b, int o, int l) { buf = b; off = o; len = l; }
        public void reset() { off = 0; len = buf.length; }
    }

    static InetSocketAddress parse(String a) {
        if (a.startsWith("datagram://")) a = a.substring(11);
        int c = a.lastIndexOf(':');
        String h = a.substring(0, c);
        if (bcastLocal && h.equals("255.255.255.255")) h = "127.0.0.1";
        return new InetSocketAddress(h, Integer.parseInt(a.substring(c + 1)));
    }

    static Connection openDatagram(final String rest) throws IOException {
        int c = rest.lastIndexOf(':');
        final String host = rest.substring(0, c);
        int port = Integer.parseInt(rest.substring(c + 1));
        final DatagramSocket ds;
        if (host.length() == 0) {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(port));
        } else {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
        }
        final String target = host.length() == 0 ? null : rest;
        return new UDPDatagramConnection() {
            public String getLocalAddress() throws IOException {
                if (target != null && fakeRouteIp != null) return fakeRouteIp;
                if (target != null && !target.startsWith("255.")) {
                    try { ds.connect(parse(target)); } catch (Exception e) { }
                }
                return ds.getLocalAddress().getHostAddress();
            }
            public int getLocalPort() { return ds.getLocalPort(); }
            public int getMaximumLength() { return 1400; }
            public int getNominalLength() { return 512; }
            public void send(Datagram d) throws IOException {
                String a = d.getAddress() != null ? d.getAddress() : target;
                ds.send(new DatagramPacket(d.getData(), d.getOffset(), d.getLength(), parse(a)));
            }
            public void receive(Datagram d) throws IOException {
                DatagramPacket p = new DatagramPacket(d.getData(), d.getOffset(), d.getData().length - d.getOffset());
                ds.receive(p);
                d.setLength(p.getLength());
                ((DG) d).addr = "datagram://" + p.getAddress().getHostAddress() + ":" + p.getPort();
            }
            public Datagram newDatagram(int size) { return new DG(new byte[size], size, target); }
            public Datagram newDatagram(int size, String a) { return new DG(new byte[size], size, a); }
            public Datagram newDatagram(byte[] b, int size) { return new DG(b, size, target); }
            public Datagram newDatagram(byte[] b, int size, String a) { return new DG(b, size, a); }
            public void close() { ds.close(); }
        };
    }

    static SocketConnection wrap(final Socket s) {
        return new SocketConnection() {
            public void setSocketOption(byte o, int v) throws IOException { if (o == DELAY) s.setTcpNoDelay(v == 0); }
            public int getSocketOption(byte o) { return 0; }
            public String getLocalAddress() { return s.getLocalAddress().getHostAddress(); }
            public int getLocalPort() { return s.getLocalPort(); }
            public String getAddress() { return s.getInetAddress().getHostAddress(); }
            public int getPort() { return s.getPort(); }
            public InputStream openInputStream() throws IOException { return s.getInputStream(); }
            public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(s.getInputStream()); }
            public OutputStream openOutputStream() throws IOException { return s.getOutputStream(); }
            public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); }
            public void close() throws IOException { s.close(); }
        };
    }
}
