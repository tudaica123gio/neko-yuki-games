package javax.microedition.rms;
import java.util.*;
public class RecordStore {
    public static Hashtable stores = new Hashtable();
    Vector recs;
    public static RecordStore openRecordStore(String n, boolean create) throws RecordStoreException {
        Vector v = (Vector) stores.get(n);
        if (v == null) { if (!create) throw new RecordStoreNotFoundException(); v = new Vector(); stores.put(n, v); }
        RecordStore r = new RecordStore(); r.recs = v; return r;
    }
    public void closeRecordStore() {}
    public int getNumRecords() { return recs.size(); }
    public int addRecord(byte[] d, int o, int l) { byte[] c = new byte[l]; System.arraycopy(d, o, c, 0, l); recs.add(c); return recs.size(); }
    public void setRecord(int id, byte[] d, int o, int l) throws RecordStoreException {
        if (id < 1 || id > recs.size()) throw new RecordStoreException("bad id");
        byte[] c = new byte[l]; System.arraycopy(d, o, c, 0, l); recs.set(id - 1, c);
    }
    public byte[] getRecord(int id) throws RecordStoreException {
        if (id < 1 || id > recs.size()) throw new RecordStoreException("bad id");
        return (byte[]) recs.get(id - 1);
    }
    public static void deleteRecordStore(String n) { stores.remove(n); }
}
