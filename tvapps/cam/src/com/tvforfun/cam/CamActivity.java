package com.tvforfun.cam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
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
 * Mostra em tela cheia o vídeo da webcam do PC, recebido por TCP como MJPEG
 * (sequência de JPEGs). O PC (ffmpeg) conecta como cliente e envia os quadros.
 */
public class CamActivity extends Activity {

    private static final int PORT = 5006;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = true;
    private ServerSocket server;

    private ImageView video;
    private TextView status;

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
        status.setText("TV Cam — waiting for your PC…\nListening on " + getLocalIp() + " : " + PORT);
        status.setTextColor(0xFF4FC3F7);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        FrameLayout.LayoutParams lpS = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lpS.gravity = Gravity.CENTER;
        status.setLayoutParams(lpS);
        root.addView(status);

        setContentView(root);
        startServer();
    }

    private void startServer() {
        new Thread(() -> {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(PORT));
                while (running) {
                    Socket client;
                    try { client = server.accept(); }
                    catch (Exception e) { if (running) continue; else break; }
                    ui.post(() -> status.setVisibility(android.view.View.GONE));
                    readMjpeg(client);
                    ui.post(() -> {
                        status.setText("Disconnected — run the cam script again");
                        status.setVisibility(android.view.View.VISIBLE);
                        video.setImageDrawable(null);
                    });
                }
            } catch (Exception e) {
                ui.post(() -> { status.setText("Error: " + e.getMessage()); status.setTextColor(0xFFFF3B30); });
            }
        }, "cam-server").start();
    }

    /** Lê o fluxo TCP e extrai cada JPEG (SOI 0xFFD8 … EOI 0xFFD9). */
    private void readMjpeg(Socket client) {
        try {
            client.setTcpNoDelay(true);
            BufferedInputStream in = new BufferedInputStream(client.getInputStream(), 1 << 16);
            ByteArrayOutputStream frame = new ByteArrayOutputStream(1 << 16);
            boolean inFrame = false;
            int prev = -1, cur;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            while (running && (cur = in.read()) != -1) {
                if (!inFrame) {
                    if (prev == 0xFF && cur == 0xD8) { // início do JPEG
                        inFrame = true;
                        frame.reset();
                        frame.write(0xFF); frame.write(0xD8);
                    }
                } else {
                    frame.write(cur);
                    if (prev == 0xFF && cur == 0xD9) { // fim do JPEG
                        byte[] jpg = frame.toByteArray();
                        final Bitmap bmp = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, opts);
                        if (bmp != null) ui.post(() -> showFrame(bmp));
                        inFrame = false;
                    }
                }
                prev = cur;
            }
        } catch (Exception ignored) {
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private Bitmap lastShown;
    private void showFrame(Bitmap bmp) {
        video.setImageBitmap(bmp);
        if (lastShown != null && lastShown != bmp) lastShown.recycle();
        lastShown = bmp;
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
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
