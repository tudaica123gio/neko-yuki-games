package javax.microedition.io.file;
import java.util.Enumeration;
import java.util.Vector;
public class FileSystemRegistry {
    public static Enumeration listRoots() { Vector v = new Vector(); v.addElement("root1/"); return v.elements(); }
}
