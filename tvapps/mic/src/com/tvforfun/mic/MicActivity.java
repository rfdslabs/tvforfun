package com.tvforfun.mic;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;

/**
 * Recebe voz (PCM 16-bit, 16 kHz, mono) do PC por TCP e toca no som da TV.
 * O PC (ffmpeg) conecta como cliente e envia o áudio do microfone.
 */
public class MicActivity extends Activity {

    private static final int PORT = 5005;
    private static final int SAMPLE_RATE = 16000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;
    private ServerSocket server;
    private Thread serverThread;

    private TextView status;
    private LevelView level;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0A0A0F);
        root.setPadding(dp(64), dp(48), dp(64), dp(48));

        root.addView(text("TV Mic", 54, 0xFFFFFFFF, true));

        level = new LevelView(this);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(dp(220), dp(220));
        lpL.setMargins(0, dp(28), 0, dp(28));
        level.setLayoutParams(lpL);
        root.addView(level);

        status = text("Starting…", 32, 0xFF4FC3F7, false);
        root.addView(status);

        TextView hint = text("On your computer, run the talk script.\n"
                + "Listening on " + getLocalIp() + " : " + PORT, 24, 0xFFB3B3B3, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(24), 0, 0);
        root.addView(hint);

        setContentView(root);
        startServer();
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new java.net.InetSocketAddress(PORT));
                setStatus("Waiting for your voice…", 0xFF4FC3F7);
                while (running) {
                    Socket client;
                    try { client = server.accept(); }
                    catch (Exception e) { if (running) continue; else break; }
                    setStatus("● Connected — speak!", 0xFF34C759);
                    playFrom(client);
                    setStatus("Waiting for your voice…", 0xFF4FC3F7);
                    ui.post(() -> level.setLevel(0f));
                }
            } catch (Exception e) {
                setStatus("Error: " + e.getMessage(), 0xFFFF3B30);
            }
        }, "mic-server");
        serverThread.start();
    }

    private void playFrom(Socket client) {
        AudioTrack track = null;
        try {
            client.setTcpNoDelay(true);
            int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, SAMPLE_RATE / 5 * 2); // ~200ms
            track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufSize, AudioTrack.MODE_STREAM);
            track.play();

            InputStream in = client.getInputStream();
            byte[] buf = new byte[1600]; // 50ms @ 16kHz mono 16-bit
            int n;
            while (running && (n = in.read(buf)) > 0) {
                track.write(buf, 0, n);
                ui.post(() -> level.setLevel(amplitude(buf, buf.length)));
            }
        } catch (Exception ignored) {
        } finally {
            if (track != null) try { track.stop(); track.release(); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    /** Pico de amplitude normalizado (0..1) do bloco PCM. */
    private float amplitude(byte[] buf, int len) {
        int peak = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int s = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
            int a = Math.abs(s);
            if (a > peak) peak = a;
        }
        return Math.min(1f, peak / 20000f);
    }

    private void setStatus(final String s, final int color) {
        ui.post(() -> { status.setText(s); status.setTextColor(color); });
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif-light",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4
                            && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "this TV";
    }

    @Override
    protected void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /** Círculo que pulsa com o volume da voz. */
    static class LevelView extends View {
        private float lvl = 0f;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        LevelView(android.content.Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(8f);
            ring.setColor(0xFF2A2F3A);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFF4FC3F7);
        }

        void setLevel(float l) {
            // suaviza
            lvl = Math.max(l, lvl * 0.75f);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float base = Math.min(w, h) * 0.28f;
            float grow = Math.min(w, h) * 0.20f;
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.46f, ring);
            fill.setColor(lvl > 0.02f ? 0xFF34C759 : 0xFF4FC3F7);
            canvas.drawCircle(cx, cy, base + grow * lvl, fill);
        }
    }
}
