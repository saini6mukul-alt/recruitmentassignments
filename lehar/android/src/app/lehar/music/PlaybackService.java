package app.lehar.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

/**
 * Foreground media service: keeps the process alive while music plays, shows the media notification,
 * drives lock-screen / headset controls through a MediaSession, and handles audio focus
 * (calls, Instagram reels, YouTube in another app).
 */
public class PlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    public interface Listener { void onMedia(String action); }

    static final String CHANNEL = "lehar_playback";
    static final int NOTIF_ID = 7;
    static final String ACT_PLAY = "app.lehar.music.PLAY";
    static final String ACT_PAUSE = "app.lehar.music.PAUSE";
    static final String ACT_NEXT = "app.lehar.music.NEXT";
    static final String ACT_PREV = "app.lehar.music.PREV";

    static volatile PlaybackService instance;
    static volatile Listener listener;
    private static JSONObject pendingNowPlaying;

    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private boolean hasFocus;
    private PowerManager.WakeLock wakeLock;
    private final Handler main = new Handler(Looper.getMainLooper());

    private String title = "Lehar";
    private String artist = "";
    private String artUrl = "";
    private Bitmap art;
    private long durationMs;
    private long positionMs;
    private boolean playing;

    /** Called from the JS bridge (any thread). Starts the service on first use. */
    static void nowPlaying(Context ctx, JSONObject info) {
        PlaybackService s = instance;
        if (s == null) {
            pendingNowPlaying = info;
            Intent i = new Intent(ctx, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
            return;
        }
        s.main.post(() -> s.apply(info));
    }

    static void position(long ms) {
        PlaybackService s = instance;
        if (s != null) s.main.post(() -> { s.positionMs = ms; s.updateState(); });
    }

    static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, PlaybackService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        session = new MediaSession(this, "Lehar");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { send("play"); }
            @Override public void onPause() { send("pause"); }
            @Override public void onSkipToNext() { send("next"); }
            @Override public void onSkipToPrevious() { send("prev"); }
            @Override public void onSeekTo(long pos) { positionMs = pos; updateState(); send("seek:" + pos); }
            @Override public void onStop() { send("pause"); }
        });
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        session.setSessionActivity(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
        session.setActive(true);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lehar:playback");
        wakeLock.setReferenceCounted(false);

        startInForeground();
        if (pendingNowPlaying != null) { apply(pendingNowPlaying); pendingNowPlaying = null; }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        String a = intent == null ? null : intent.getAction();
        if (ACT_PLAY.equals(a)) send("play");
        else if (ACT_PAUSE.equals(a)) send("pause");
        else if (ACT_NEXT.equals(a)) send("next");
        else if (ACT_PREV.equals(a)) send("prev");
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
    }

    private void send(String action) {
        Listener l = listener;
        if (l != null) main.post(() -> l.onMedia(action));
    }

    private void apply(JSONObject info) {
        title = info.optString("title", title);
        artist = info.optString("artist", artist);
        durationMs = (long) (info.optDouble("durationSec", durationMs / 1000.0) * 1000);
        positionMs = (long) (info.optDouble("positionSec", positionMs / 1000.0) * 1000);
        boolean nowPlaying = info.optBoolean("isPlaying", playing);
        String newArt = info.optString("thumb", "");
        if (!newArt.equals(artUrl)) {
            artUrl = newArt;
            art = null;
            final String want = newArt;
            if (!want.isEmpty()) Net.POOL.execute(() -> {
                byte[] b = Net.download(want);
                Bitmap bmp = b == null ? null : BitmapFactory.decodeByteArray(b, 0, b.length);
                main.post(() -> { if (want.equals(artUrl)) { art = bmp; updateMetadata(); refresh(); } });
            });
        }
        if (nowPlaying && !hasFocus) requestFocus();
        playing = nowPlaying;
        if (playing) wakeLock.acquire(6 * 60 * 60 * 1000L); else if (wakeLock.isHeld()) wakeLock.release();
        updateMetadata();
        updateState();
        refresh();
    }

    private void updateMetadata() {
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(b.build());
    }

    private void updateState() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, positionMs, playing ? 1f : 0f)
                .build());
    }

    private void refresh() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, build());
    }

    private PendingIntent action(String a, int code) {
        Intent i = new Intent(this, PlaybackService.class).setAction(a);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action act(int icon, String label, String action, int code) {
        return new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, action(action, code)).build();
    }

    private Notification build() {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(art)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE))
                .addAction(act(R.drawable.ic_prev, "Pichla", ACT_PREV, 10))
                .addAction(playing ? act(R.drawable.ic_pause, "Pause", ACT_PAUSE, 11) : act(R.drawable.ic_play, "Play", ACT_PLAY, 12))
                .addAction(act(R.drawable.ic_next, "Agla", ACT_NEXT, 13))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private void requestFocus() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(this, main)
                    .build();
        }
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                main.removeCallbacks(waitForSilence);
                send("focusGain");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Call or short clip: Android gives focus back with GAIN when it ends.
                send("focusLoss");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Another app (Instagram reel, YouTube) took focus for good. Watch for it to go quiet, then resume.
                hasFocus = false;
                send("focusLoss");
                silentChecks = 0;
                main.removeCallbacks(waitForSilence);
                main.postDelayed(waitForSilence, 2000);
                break;
            default:
                break; // ducking: keep playing
        }
    }

    private int silentChecks;
    private final Runnable waitForSilence = new Runnable() {
        @Override
        public void run() {
            if (instance == null) return;
            silentChecks = audio.isMusicActive() ? 0 : silentChecks + 1;
            if (silentChecks >= 2) {
                requestFocus();
                if (hasFocus) { send("focusGain"); return; }
            }
            main.postDelayed(this, 2000);
        }
    };

    @Override
    public void onDestroy() {
        instance = null;
        main.removeCallbacks(waitForSilence);
        if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        session.setActive(false);
        session.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
