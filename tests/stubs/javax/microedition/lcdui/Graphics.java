package javax.microedition.lcdui;
import java.awt.*;
import java.awt.image.BufferedImage;
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    Graphics2D g; int tx, ty; int color; Font font = Font.getDefaultFont();
    public Graphics(BufferedImage bi) {
        g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setClip(0, 0, bi.getWidth(), bi.getHeight());
    }
    public void translate(int x, int y) { tx += x; ty += y; }
    public int getTranslateX() { return tx; }
    public int getTranslateY() { return ty; }
    public void setColor(int rgb) { color = rgb & 0xFFFFFF; g.setColor(new Color(color)); }
    public void setColor(int r, int gg, int b) { setColor((r << 16) | (gg << 8) | b); }
    public int getColor() { return color; }
    public void setFont(Font f) { font = f; }
    public Font getFont() { return font; }
    public void setClip(int x, int y, int w, int h) { g.setClip(x + tx, y + ty, w, h); }
    public void clipRect(int x, int y, int w, int h) { g.clipRect(x + tx, y + ty, w, h); }
    public int getClipX() { return g.getClipBounds().x - tx; }
    public int getClipY() { return g.getClipBounds().y - ty; }
    public int getClipWidth() { return g.getClipBounds().width; }
    public int getClipHeight() { return g.getClipBounds().height; }
    public void fillRect(int x, int y, int w, int h) { g.fillRect(x + tx, y + ty, w, h); }
    public void drawRect(int x, int y, int w, int h) { g.drawRect(x + tx, y + ty, w, h); }
    public void drawLine(int x1, int y1, int x2, int y2) { g.drawLine(x1 + tx, y1 + ty, x2 + tx, y2 + ty); }
    public void fillArc(int x, int y, int w, int h, int a, int b) { g.fillArc(x + tx, y + ty, w, h, a, b); }
    public void drawArc(int x, int y, int w, int h, int a, int b) { g.drawArc(x + tx, y + ty, w, h, a, b); }
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { g.fillRoundRect(x + tx, y + ty, w, h, aw, ah); }
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { g.drawRoundRect(x + tx, y + ty, w, h, aw, ah); }
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        g.fillPolygon(new int[]{x1 + tx, x2 + tx, x3 + tx}, new int[]{y1 + ty, y2 + ty, y3 + ty}, 3);
    }
    public void drawString(String s, int x, int y, int anchor) {
        g.setFont(font.af);
        FontMetrics fm = font.fm;
        int w = fm.stringWidth(s);
        int px = x, py;
        if ((anchor & HCENTER) != 0) px = x - w / 2; else if ((anchor & RIGHT) != 0) px = x - w;
        if ((anchor & BASELINE) != 0) py = y;
        else if ((anchor & BOTTOM) != 0) py = y - fm.getDescent();
        else py = y + fm.getAscent();
        g.drawString(s, px + tx, py + ty);
    }
    int ax(int x, int w, int anchor) {
        if ((anchor & HCENTER) != 0) return x - w / 2;
        if ((anchor & RIGHT) != 0) return x - w;
        return x;
    }
    int ay(int y, int h, int anchor) {
        if ((anchor & VCENTER) != 0) return y - h / 2;
        if ((anchor & BOTTOM) != 0) return y - h;
        return y;
    }
    public void drawImage(Image im, int x, int y, int anchor) {
        g.drawImage(im.bi, ax(x, im.getWidth(), anchor) + tx, ay(y, im.getHeight(), anchor) + ty, null);
    }
    public void drawRegion(Image im, int sx, int sy, int w, int h, int trans, int x, int y, int anchor) {
        Shape old = g.getClip();
        int dx = ax(x, w, anchor) + tx, dy = ay(y, h, anchor) + ty;
        g.clipRect(dx, dy, w, h);
        g.drawImage(im.bi, dx - sx, dy - sy, null);
        g.setClip(old);
    }
    /** Chỉ có ở bản Android (ngoài MIDP): vẽ vùng ảnh phóng vào ô đích. Bản máy tính: để kiểm thử. */
    public void drawScaled(Image im, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, boolean smooth) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(im.bi, dx + tx, dy + ty, dx + tx + dw, dy + ty + dh, sx, sy, sx + sw, sy + sh, null);
    }
    public void drawRGB(int[] rgb, int off, int scan, int x, int y, int w, int h, boolean alpha) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int p = rgb[off + j * scan + i];
            b.setRGB(i, j, alpha ? p : (p | 0xFF000000));
        }
        g.drawImage(b, x + tx, y + ty, null);
    }
}
