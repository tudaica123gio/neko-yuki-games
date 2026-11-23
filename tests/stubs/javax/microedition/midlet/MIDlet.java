package javax.microedition.midlet;
public abstract class MIDlet {
    protected MIDlet() {}
    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;
    public final void notifyDestroyed() { System.out.println("[stub] notifyDestroyed"); }
    public final void notifyPaused() {}
    public final void resumeRequest() {}
    public static java.util.Hashtable props = new java.util.Hashtable();
    public final String getAppProperty(String k) { return (String) props.get(k); }
    public final boolean platformRequest(String url) { return false; }
}
