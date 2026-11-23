package javax.microedition.lcdui;
import javax.microedition.midlet.MIDlet;
public class Display {
    static Display d = new Display();
    Displayable cur;
    public static Display getDisplay(MIDlet m) { return d; }
    public void setCurrent(Displayable x) { cur = x; }
    public Displayable getCurrent() { return cur; }
    public static int vibrations, flashes;
    public boolean vibrate(int ms) { vibrations++; return true; }
    public boolean flashBacklight(int ms) { flashes++; return true; }
}
