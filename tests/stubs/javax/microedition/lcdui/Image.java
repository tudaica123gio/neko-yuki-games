package javax.microedition.lcdui;
import java.awt.image.BufferedImage;
import java.io.*;
public class Image {
    public BufferedImage bi; boolean mutable;
    Image(BufferedImage b, boolean m) { bi = b; mutable = m; }
    public static Image createImage(String name) throws IOException {
        InputStream is = Image.class.getResourceAsStream(name);
        if (is == null) throw new IOException("not found " + name);
        BufferedImage src = javax.imageio.ImageIO.read(is);
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        dst.getGraphics().drawImage(src, 0, 0, null);
        return new Image(dst, false);
    }
    public static Image createImage(byte[] d, int off, int len) {
        try {
            BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(d, off, len));
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            dst.getGraphics().drawImage(src, 0, 0, null);
            return new Image(dst, false);
        } catch (IOException e) { throw new IllegalArgumentException(e.toString()); }
    }
    public static Image createImage(int w, int h) { return new Image(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), true); }
    public static Image createRGBImage(int[] rgb, int w, int h, boolean alpha) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] c = new int[w * h];
        for (int i = 0; i < c.length; i++) c[i] = alpha ? rgb[i] : (rgb[i] | 0xFF000000);
        b.setRGB(0, 0, w, h, c, 0, w);
        return new Image(b, false);
    }
    public void getRGB(int[] rgb, int off, int scan, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) rgb[off + j * scan + i] = bi.getRGB(x + i, y + j);
    }
    public Graphics getGraphics() { return new Graphics(bi); }
    public int getWidth() { return bi.getWidth(); }
    public int getHeight() { return bi.getHeight(); }
    public boolean isMutable() { return mutable; }
}
