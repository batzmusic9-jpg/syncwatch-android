package com.batz.syncwatch;

import android.net.Uri;
import android.view.ViewGroup;
import java.util.List;

/** Main-thread API. Times are milliseconds; native callbacks never emit onCommand. */
public interface PlaybackEngine {
    interface Listener {
        default void onCommand(boolean seek) {}
        default void onReady() {}
        default void onEnded() {}
        default void onError(String details) {}
        default void onMetadata(String details) {}
        default void onTracksChanged() {}
    }
    final class Track {
        public final int id;
        public final String label;
        public Track(int id, String label) { this.id = id; this.label = label; }
    }
    void setListener(Listener listener);
    void attachSurface(ViewGroup container);
    void detachSurface();
    void open(Uri uri) throws java.io.IOException;
    void prepare();
    void play();
    void pause();
    void setPlayWhenReady(boolean value);
    void seekTo(long milliseconds);
    long getCurrentPosition();
    long getDuration();
    boolean isPlaying();
    boolean isReady();
    boolean getPlayWhenReady();
    List<Track> audioTracks();
    List<Track> subtitleTracks();
    int selectedAudio();
    int selectedSubtitle();
    boolean selectAudio(int id);
    boolean selectSubtitle(int id);
    void addSubtitle(Uri uri, String label);
    void setSubtitleOffset(long milliseconds);
    long getSubtitleOffset();
    void release();
}
