package com.tvforfun.cast;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;

/**
 * Casting PC → TV: recebe VÍDEO (MJPEG, porta 5006) e ÁUDIO (PCM 16-bit/16 kHz,
 * porta 5005) ao mesmo tempo. Um "Chromecast caseiro" pela LAN.
 */
public class CastActivity extends Activity {

    private static final int VIDEO_PORT = 5006;
    private static final int AUDIO_PORT = 5005;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;
    private ServerSocket videoServer, audioServer;

    private ImageView video;
    private TextView status;
    private Bitmap lastShown;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        video = new ImageView(this);
        video.setScaleType(ImageView.ScaleType.FIT_CENTER);
        video.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(video);

        status = new TextView(this);
        status.setText("TV Cast — waiting for your PC…\nListening on " + getLocalIp()
                + "  (video " + VIDEO_PORT + ", audio " + AUDIO_PORT + ")");
        status.setTextColor(0xFF4FC3F7);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        status.setLayoutParams(lp);
        root.addView(status);

        setContentView(root);

        serve(VIDEO_PORT, true);
        serve(AUDIO_PORT, false);
    }

    private void serve(final int port, final boolean isVideo) {
        new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(port));
                if (isVideo) videoServer = ss; else audioServer = ss;
                while (running) {
                    Socket c;
                    try { c = ss.accept(); } catch (Exception e) { if (running) continue; else break; }
                    if (isVideo) { ui.post(() -> status.setVisibility(View.GONE)); readVideo(c); ui.post(this::videoGone); }
                    else playAudio(c);
                }
            } catch (Exception ignored) {}
        }, isVideo ? "cast-video" : "cast-audio").start();
    }

    private void videoGone() {
        status.setText("Disconnected — run the cast command again");
        status.setVisibility(View.VISIBLE);
        video.setImageDrawable(null);
    }

    private void readVideo(Socket client) {
        try {
            client.setTcpNoDelay(true);
            BufferedInputStream in = new BufferedInputStream(client.getInputStream(), 1 << 16);
            ByteArrayOutputStream frame = new ByteArrayOutputStream(1 << 16);
            boolean inFrame = false; int prev = -1, cur;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            while (running && (cur = in.read()) != -1) {
                if (!inFrame) {
                    if (prev == 0xFF && cur == 0xD8) { inFrame = true; frame.reset(); frame.write(0xFF); frame.write(0xD8); }
                } else {
                    frame.write(cur);
                    if (prev == 0xFF && cur == 0xD9) {
                        byte[] jpg = frame.toByteArray();
                        final Bitmap bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, opts);
                        if (bmp != null) ui.post(() -> showFrame(bmp));
                        inFrame = false;
                    }
                }
                prev = cur;
            }
        } catch (Exception ignored) {
        } finally { try { client.close(); } catch (Exception ignored) {} }
    }

    private void showFrame(Bitmap bmp) {
        video.setImageBitmap(bmp);
        if (lastShown != null && lastShown != bmp) lastShown.recycle();
        lastShown = bmp;
    }

    private void playAudio(Socket client) {
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

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a.getAddress().length == 4 && a.isSiteLocalAddress())
                        return a.getHostAddress();
        } catch (Exception ignored) {}
        return "this TV";
    }

    @Override
    protected void onDestroy() {
        running = false;
        try { if (videoServer != null) videoServer.close(); } catch (Exception ignored) {}
        try { if (audioServer != null) audioServer.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
