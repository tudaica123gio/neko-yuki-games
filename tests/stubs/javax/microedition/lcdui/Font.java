package javax.microedition.lcdui;
import java.awt.image.BufferedImage;
public class Font {
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public java.awt.Font af; java.awt.FontMetrics fm;
    static BufferedImage tmp = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    Font(int face, int style, int size) {
        int px = size == SIZE_SMALL ? 11 : size == SIZE_LARGE ? 17 : 13;
        af = new java.awt.Font(face == FACE_MONOSPACE ? "Monospaced" : "SansSerif",
            (style & 1) != 0 ? java.awt.Font.BOLD : java.awt.Font.PLAIN, px);
        fm = tmp.createGraphics().getFontMetrics(af);
    }
    public static Font getFont(int face, int style, int size) { return new Font(face, style, size); }
    public static Font getDefaultFont() { return new Font(0, 0, 0); }
    public int stringWidth(String s) { return fm.stringWidth(s); }
    public int charWidth(char c) { return fm.charWidth(c); }
    public int substringWidth(String s, int o, int l) { return fm.stringWidth(s.substring(o, o + l)); }
    public int getHeight() { return fm.getHeight(); }
    public int getBaselinePosition() { return fm.getAscent(); }
}
