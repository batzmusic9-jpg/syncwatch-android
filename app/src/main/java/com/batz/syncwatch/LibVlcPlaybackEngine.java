package com.batz.syncwatch;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Official LibVLC owns demux, decoding and subtitle timing; Syncplay only sees milliseconds. */
public final class LibVlcPlaybackEngine implements PlaybackEngine {
    private static final String TAG = "SyncWatchPlayback";
    private final Context context;
    private final LibVLC lib;
    private final PlaybackIntent intent;
    private Listener listener = new Listener() {};
    private MediaPlayer nativePlayer;
    private Media source;
    private ParcelFileDescriptor descriptor;
    private VLCVideoLayout surface;
    private boolean ready, preparing, released, externalAttached, nativeStarted;
    private int generation;
    private long offset, seekSent = -1, seekSentAt;
    private Uri external;
    private String externalLabel;
    private final Set<Integer> internalSubtitles = new HashSet<>();
    private final Map<Integer, String> externalNames = new HashMap<>();
    private String metadata = "";

    public LibVlcPlaybackEngine(Context context) {
        this.context = context.getApplicationContext();
        lib = new LibVLC(this.context, new ArrayList<>(Arrays.asList("--no-video-title-show", "--verbose=2")));
        intent = new PlaybackIntent(seek -> listener.onCommand(seek));
    }
    @Override public void setListener(Listener value) { listener = value; }
    @Override public void attachSurface(ViewGroup target) {
        surface = new VLCVideoLayout(target.getContext());
        target.addView(surface, new ViewGroup.LayoutParams(-1, -1));
        if (nativePlayer != null) attachNativeSurface();
    }
    private void attachNativeSurface() {
        nativePlayer.attachViews(surface, null, true, true);
        nativePlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
        nativePlayer.setVideoTrackEnabled(true);
    }
    @Override public void detachSurface() {
        if (nativePlayer != null && surface != null) nativePlayer.detachViews();
        if (surface != null && surface.getParent() instanceof ViewGroup)
            ((ViewGroup) surface.getParent()).removeView(surface);
        surface = null;
    }
    private void closeMedia() {
        ++generation;
        if (nativePlayer != null) {
            nativePlayer.setEventListener(null);
            nativePlayer.stop();
            if (surface != null) nativePlayer.detachViews();
            nativePlayer.release();
            nativePlayer = null;
        }
        if (source != null) { source.setEventListener(null); source.release(); source = null; }
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException error) { Log.w(TAG, "close fd", error); }
            descriptor = null;
        }
    }
    @Override public void open(Uri uri) throws IOException {
        if (released) throw new IOException("Player released");
        closeMedia();
        ready = false; preparing = false; nativeStarted = false; offset = 0; seekSent = -1;
        external = null; externalLabel = null; externalAttached = false;
        internalSubtitles.clear(); externalNames.clear(); intent.reset(); metadata = "";
        try {
            descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor == null) throw new IOException("Provider returned no descriptor");
            // Keep this descriptor alive until the native media is stopped and released.
            source = new Media(lib, descriptor.getFileDescriptor());
            source.setHWDecoderEnabled(true, false);
            source.addOption(":start-paused");
            nativePlayer = new MediaPlayer(lib);
            nativePlayer.setAudioDigitalOutputEnabled(false);
            int token = generation;
            nativePlayer.setEventListener(event -> { if (token == generation && !released) onNative(event); });
            source.setEventListener(event -> {
                if (token == generation && !released && event.type == IMedia.Event.ParsedChanged) updateMetadata();
            });
            nativePlayer.setMedia(source);
            if (surface != null) attachNativeSurface();
            String mime = context.getContentResolver().getType(uri);
            String filename = uri.getLastPathSegment();
            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) filename = cursor.getString(0);
            }
            String hint = filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".mkv") ? "Matroska (extension hint)" : "auto";
            Log.i(TAG, "open filename=" + filename + " scheme=" + uri.getScheme() + " declaredMime=" + mime
                    + " container=" + hint + " transport=SAF/fd; actual demux/selected decoder in native VLC logs");
            logDecoderCandidates();
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "OPEN_ERROR renderer=LibVLC cause=" + error.getClass().getName(), error);
            closeMedia();
            throw error;
        }
    }
    @Override public void prepare() {
        if (nativePlayer == null || preparing) return;
        preparing = true;
        source.parseAsync(IMedia.Parse.ParseLocal, 10000);
        nativePlayer.play(); // start-paused opens demux/decoders without an unintended room command.
    }
    private void onNative(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                nativeStarted = true;
                intent.observedPlaying(true);
                makeReady();
                if (!intent.wanted()) nativePlayer.pause();
                break;
            case MediaPlayer.Event.Paused:
                nativeStarted = true;
                intent.observedPlaying(false);
                makeReady();
                if (intent.wanted()) nativePlayer.play();
                break;
            case MediaPlayer.Event.LengthChanged:
            case MediaPlayer.Event.SeekableChanged:
                if (nativePlayer.isSeekable() && nativePlayer.getLength() > 0) makeReady();
                break;
            case MediaPlayer.Event.ESAdded:
                if (event.getEsChangedType() == IMedia.Track.Type.Text && externalAttached
                        && !internalSubtitles.contains(event.getEsChangedID()))
                    externalNames.put(event.getEsChangedID(), externalLabel);
                updateMetadata();
                nativePlayer.setSpuDelay(offset * 1000);
                listener.onTracksChanged();
                break;
            case MediaPlayer.Event.ESDeleted:
            case MediaPlayer.Event.ESSelected:
                updateMetadata();
                nativePlayer.setSpuDelay(offset * 1000);
                listener.onTracksChanged();
                break;
            case MediaPlayer.Event.EndReached:
                intent.observedPlaying(false);
                listener.onEnded();
                break;
            case MediaPlayer.Event.EncounteredError:
                ready = false;
                intent.observedPlaying(false);
                String error = "errorCode=" + event.type + " errorCodeName=EncounteredError renderer=LibVLC "
                        + "cause/selectedDecoder=not exposed by Java API; see native VLC logcat. " + metadata;
                Log.e(TAG, error);
                listener.onError(error);
                break;
            default: break;
        }
        if (ready && intent.pending() >= 0) applySeek();
    }
    private void makeReady() {
        if (ready || !nativeStarted || nativePlayer.getLength() <= 0) return;
        ready = true;
        updateMetadata();
        for (Track track : subtitleTracks()) if (track.id >= 0) internalSubtitles.add(track.id);
        applySeek();
        if (external != null) addExternal();
        nativePlayer.setSpuDelay(offset * 1000);
        listener.onReady();
    }
    @Override public void play() { setPlayWhenReady(true); }
    @Override public void pause() { setPlayWhenReady(false); }
    @Override public void setPlayWhenReady(boolean value) {
        intent.play(value);
        if (nativePlayer == null || !preparing) return;
        if (value) nativePlayer.play();
        else if (nativePlayer.isPlaying()) nativePlayer.pause();
    }
    @Override public void seekTo(long milliseconds) {
        long target = Math.max(0, milliseconds);
        if (getDuration() > 0) target = Math.min(target, getDuration());
        intent.seek(target);
        if (ready) applySeek();
    }
    private void applySeek() {
        if (intent.pending() < 0 || !nativePlayer.isSeekable()) return;
        long target = intent.pending();
        if (nativePlayer.setTime(target, false) >= 0) {
            seekSent = target;
            seekSentAt = android.os.SystemClock.elapsedRealtime();
            intent.seekApplied();
        }
    }
    @Override public long getCurrentPosition() {
        if (intent.pending() >= 0) return intent.pending();
        long time = nativePlayer == null ? 0 : Math.max(0, nativePlayer.getTime());
        // libvlc_set_time is async. Report the requested position until the decoder catches up.
        if (seekSent >= 0) {
            if (Math.abs(time - seekSent) < 250 || android.os.SystemClock.elapsedRealtime() - seekSentAt > 2000) seekSent = -1;
            else return seekSent;
        }
        return time;
    }
    @Override public long getDuration() { return nativePlayer == null ? -1 : nativePlayer.getLength(); }
    @Override public boolean isPlaying() { return nativePlayer != null && nativePlayer.isPlaying(); }
    @Override public boolean isReady() { return ready; }
    @Override public boolean getPlayWhenReady() { return intent.wanted(); }

    private IMedia.Track trackMetadata(int id, int type) {
        if (source == null || !nativeStarted) return null;
        for (int i = 0; i < source.getTrackCount(); i++) {
            IMedia.Track track = source.getTrack(i);
            if (track != null && track.id == id && track.type == type) return track;
        }
        return null;
    }
    private List<Track> tracks(MediaPlayer.TrackDescription[] descriptions, int type) {
        List<Track> result = new ArrayList<>();
        if (descriptions == null) return result;
        for (MediaPlayer.TrackDescription description : descriptions) {
            if (description.id < 0) continue;
            IMedia.Track track = trackMetadata(description.id, type);
            String label = description.name;
            if (track != null) {
                label += " · " + (track.language == null ? "idioma desconhecido" : track.language) + " · " + track.codec;
                if (track instanceof IMedia.AudioTrack) label += " · " + ((IMedia.AudioTrack) track).channels + " canais";
            }
            if (type == IMedia.Track.Type.Text && externalNames.containsKey(description.id))
                label = "Externa · " + externalNames.get(description.id);
            result.add(new Track(description.id, label));
        }
        return result;
    }
    @Override public List<Track> audioTracks() {
        return nativePlayer == null ? new ArrayList<>() : tracks(nativePlayer.getAudioTracks(), IMedia.Track.Type.Audio);
    }
    @Override public List<Track> subtitleTracks() {
        return nativePlayer == null ? new ArrayList<>() : tracks(nativePlayer.getSpuTracks(), IMedia.Track.Type.Text);
    }
    @Override public int selectedAudio() { return nativePlayer == null ? -1 : nativePlayer.getAudioTrack(); }
    @Override public int selectedSubtitle() { return nativePlayer == null ? -1 : nativePlayer.getSpuTrack(); }
    @Override public boolean selectAudio(int id) { return nativePlayer != null && nativePlayer.setAudioTrack(id); }
    @Override public boolean selectSubtitle(int id) { return nativePlayer != null && nativePlayer.setSpuTrack(id); }
    @Override public void addSubtitle(Uri uri, String label) {
        if (nativePlayer == null) return;
        external = uri; externalLabel = label;
        if (ready) addExternal();
    }
    private void addExternal() {
        for (Track track : subtitleTracks()) if (!externalNames.containsKey(track.id)) internalSubtitles.add(track.id);
        if (!nativePlayer.addSlave(IMedia.Slave.Type.Subtitle, external, true))
            throw new IllegalArgumentException("LibVLC rejected external subtitle");
        externalAttached = true;
        nativePlayer.setSpuDelay(offset * 1000);
    }
    @Override public void setSubtitleOffset(long milliseconds) {
        if (milliseconds < -600000 || milliseconds > 600000) throw new IllegalArgumentException("Subtitle offset out of range");
        offset = milliseconds;
        if (nativePlayer != null) nativePlayer.setSpuDelay(offset * 1000);
    }
    @Override public long getSubtitleOffset() { return offset; }
    public long nativeSubtitleDelay() { return nativePlayer == null ? 0 : nativePlayer.getSpuDelay(); }
    public long nativeTime() { return nativePlayer == null ? -1 : nativePlayer.getTime(); }
    public IMedia.Stats diagnostics() { return source == null ? null : source.getStats(); }
    public String metadata() { return metadata; }
    private void updateMetadata() {
        // Media caches its first track snapshot. ESAdded may describe only the first stream;
        // do not read it until Playing/Paused confirms initial stream discovery has finished.
        if (source == null || !nativeStarted) return;
        StringBuilder info = new StringBuilder("durationMs=" + getDuration());
        for (int i = 0; i < source.getTrackCount(); i++) {
            IMedia.Track track = source.getTrack(i);
            if (track == null) continue;
            info.append(" | id=").append(track.id).append(" type=").append(track.type).append(" codec=")
                    .append(track.codec).append(" original=").append(track.originalCodec).append(" language=").append(track.language);
            if (track instanceof IMedia.VideoTrack) {
                IMedia.VideoTrack video = (IMedia.VideoTrack) track;
                info.append(" resolution=").append(video.width).append('x').append(video.height)
                        .append(" fps=").append(video.frameRateNum).append('/').append(video.frameRateDen);
            } else if (track instanceof IMedia.AudioTrack) {
                IMedia.AudioTrack audio = (IMedia.AudioTrack) track;
                info.append(" channels=").append(audio.channels).append(" sampleRate=").append(audio.rate);
            }
        }
        String next = info.toString();
        if (!next.equals(metadata)) { metadata = next; Log.i(TAG, metadata); listener.onMetadata(metadata); }
    }
    private void logDecoderCandidates() {
        try {
            for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (codec.isEncoder()) continue;
                for (String mime : codec.getSupportedTypes())
                    if (mime.equalsIgnoreCase("video/hevc") || mime.equalsIgnoreCase("audio/ac3"))
                        Log.i(TAG, "Android candidate (not selected): mime=" + mime + " decoder=" + codec.getName());
            }
        } catch (RuntimeException error) { Log.w(TAG, "decoder inventory unavailable", error); }
    }
    @Override public void release() {
        if (released) return;
        closeMedia();
        detachSurface();
        lib.release();
        released = true;
    }
}
