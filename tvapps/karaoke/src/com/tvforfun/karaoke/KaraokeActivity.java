package com.tvforfun.karaoke;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Karaokê: toca song.mp3, mostra a letra (song.lrc) sincronizada e
 * recebe o microfone do PC (TCP 5005) pra cantar junto.
 * Arquivos em getExternalFilesDir()/song.mp3 e /song.lrc (enviados por ADB).
 */
public class KaraokeActivity extends Activity {

    private static final int MIC_PORT = 5005;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private volatile boolean running = true;
    private ServerSocket micServer;

    private final List<Long> times = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private TextView prev2, prev1, cur, next1, next2, title;
    private int lastIdx = -1;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0A0A0F);
        root.setPadding(dp(64), dp(40), dp(64), dp(40));

        title = line(36, 0xFF4FC3F7, true);
        title.setPadding(0, 0, 0, dp(28));
        prev2 = line(34, 0xFF3A3F4A, false);
        prev1 = line(40, 0xFF6B7280, false);
        cur   = line(60, 0xFFFFFFFF, true);
        next1 = line(40, 0xFF6B7280, false);
        next2 = line(34, 0xFF3A3F4A, false);
        root.addView(title); root.addView(prev2); root.addView(prev1);
        root.addView(cur); root.addView(next1); root.addView(next2);
        setContentView(root);

        File dir = getExternalFilesDir(null);
        loadLrc(new File(dir, "song.lrc"));
        startMusic(new File(dir, "song.mp3"));
        startMic();
        ui.post(tick);
    }

    private TextView line(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void loadLrc(File f) {
        Pattern p = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d+))?\\](.*)");
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String s;
            while ((s = r.readLine()) != null) {
                Matcher m = p.matcher(s);
                if (m.find()) {
                    long ms = Long.parseLong(m.group(1)) * 60000L
                            + Long.parseLong(m.group(2)) * 1000L
                            + (m.group(3) != null ? Long.parseLong((m.group(3) + "00").substring(0, 3)) : 0);
                    String txt = m.group(4).trim();
                    times.add(ms); lines.add(txt.isEmpty() ? "♪" : txt);
                }
            }
        } catch (Exception e) {
            times.add(0L); lines.add("(no lyrics file)");
        }
        if (times.isEmpty()) { times.add(0L); lines.add("♪"); }
    }

    private void startMusic(File mp3) {
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(mp3.getAbsolutePath());
            player.prepare();
            player.start();
        } catch (Exception e) {
            ui.post(() -> title.setText("TvForFun Karaoke  (no song.mp3)"));
        }
    }

    private int currentIndex(long t) {
        int idx = 0;
        for (int i = 0; i < times.size(); i++) if (t >= times.get(i)) idx = i; else break;
        return idx;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long t = 0;
            try { if (player != null) t = player.getCurrentPosition(); } catch (Exception ignored) {}
            int i = currentIndex(t);
            if (i != lastIdx) {
                lastIdx = i;
                cur.setText(get(i));
                prev1.setText(get(i - 1)); prev2.setText(get(i - 2));
                next1.setText(get(i + 1)); next2.setText(get(i + 2));
                title.setText("TvForFun Karaoke  •  ♪ sing!");
            }
            ui.postDelayed(this, 80);
        }
    };

    private String get(int i) { return (i >= 0 && i < lines.size()) ? lines.get(i) : ""; }

    // ---- microfone do PC (mesmo esquema do TvForFun Mic) ----
    private void startMic() {
        new Thread(() -> {
            try {
                micServer = new ServerSocket();
                micServer.setReuseAddress(true);
                micServer.bind(new java.net.InetSocketAddress(MIC_PORT));
                while (running) {
                    Socket c;
                    try { c = micServer.accept(); } catch (Exception e) { if (running) continue; else break; }
                    playMic(c);
                }
            } catch (Exception ignored) {}
        }, "kar-mic").start();
    }

    private void playMic(Socket client) {
        AudioTrack track = null;
        try {
            client.setTcpNoDelay(true);
            int sr = 16000;
            int min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, sr / 5 * 2), AudioTrack.MODE_STREAM);
            track.play();
            InputStream in = client.getInputStream();
            byte[] buf = new byte[1600]; int n;
            while (running && (n = in.read(buf)) > 0) track.write(buf, 0, n);
        } catch (Exception ignored) {
        } finally {
            if (track != null) try { track.stop(); track.release(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        running = false;
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) {}
        try { if (micServer != null) micServer.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
