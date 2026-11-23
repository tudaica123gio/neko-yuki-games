package javax.microedition.media;
public interface PlayerListener {
    String END_OF_MEDIA = "endOfMedia"; String STARTED = "started"; String STOPPED = "stopped";
    void playerUpdate(Player p, String event, Object data);
}
