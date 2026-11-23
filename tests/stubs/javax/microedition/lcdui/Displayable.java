package javax.microedition.lcdui;
public abstract class Displayable {
    public int w = 240, h = 320;
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public boolean isShown() { return true; }
}
