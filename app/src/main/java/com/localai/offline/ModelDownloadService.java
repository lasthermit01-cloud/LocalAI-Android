package com.localai.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public class ModelDownloadService extends Service {
    public static final String ACTION_STATUS = "com.localai.offline.DOWNLOAD_STATUS";
    public static final String EXTRA_IDS = "ids";
    private static final String CH = "model_downloads";
    private volatile boolean cancelled;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Model downloads", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String ids = intent == null ? null : intent.getStringExtra(EXTRA_IDS);
        if (ids == null || ids.isBlank()) { stopSelf(startId); return START_NOT_STICKY; }
        startForeground(41, notification("Preparing…", 0, true));
        new Thread(() -> runDownloads(ids.split(","), startId), "model-downloader").start();
        return START_NOT_STICKY;
    }

    private void runDownloads(String[] ids, int startId) {
        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalAI:modelDownload");
            wl.acquire(6L * 60 * 60 * 1000);
            for (String raw : ids) {
                ModelCatalog.Item item = ModelCatalog.get(raw.trim());
                if (item == null) continue;
                if (item.destination(this).isFile() && verify(item.destination(this), item.sha256)) {
                    send(item.name + " ✓", 100, false, true);
                    continue;
                }
                download(item);
            }
            send(getString(R.string.download_done), 100, false, true);
        } catch (Throwable t) {
            send(getString(R.string.download_failed) + " " + t.getMessage(), 0, false, false);
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
        }
    }

    private void download(ModelCatalog.Item item) throws Exception {
        File dst = item.destination(this);
        File part = new File(dst.getAbsolutePath()+".part");
        long existing = part.exists() ? part.length() : 0;
        HttpURLConnection con = open(item.url, existing);
        int code = con.getResponseCode();
        if (existing > 0 && code != 206) { part.delete(); existing = 0; con.disconnect(); con = open(item.url, 0); code = con.getResponseCode(); }
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + item.name);
        long remain = con.getContentLengthLong();
        long total = remain > 0 ? existing + remain : item.expectedBytes;
        byte[] buf = new byte[1024*1024];
        long done = existing, lastNotify=0;
        try (InputStream in = new BufferedInputStream(con.getInputStream(), 1024*1024);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(part, existing > 0), 1024*1024)) {
            int n;
            while ((n=in.read(buf)) >= 0) {
                if (cancelled) throw new IOException("cancelled");
                out.write(buf,0,n); done += n;
                if (done-lastNotify > 8L*1024*1024) {
                    lastNotify=done;
                    int p = total > 0 ? (int)Math.min(99, done*100/total) : 0;
                    String msg = item.name + "  " + human(done) + " / " + human(total);
                    send(msg,p,total<=0,false);
                    getSystemService(NotificationManager.class).notify(41, notification(msg,p,total<=0));
                }
            }
        } finally { con.disconnect(); }
        if (!verify(part,item.sha256)) { part.delete(); throw new IOException("SHA-256 mismatch: " + item.name); }
        if (dst.exists() && !dst.delete()) throw new IOException("Cannot replace " + dst.getName());
        if (!part.renameTo(dst)) throw new IOException("Cannot finalize " + dst.getName());
        send(item.name + " ✓",100,false,true);
    }

    private HttpURLConnection open(String url, long offset) throws Exception {
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection)u.openConnection();
        c.setConnectTimeout(20_000); c.setReadTimeout(60_000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","LocalAI-Android/0.1");
        if (offset > 0) c.setRequestProperty("Range","bytes="+offset+"-");
        return c;
    }

    public static boolean verify(File f, String expected) {
        try {
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            byte[] b=new byte[1024*1024]; int n;
            try(InputStream in=new BufferedInputStream(new FileInputStream(f),1024*1024)) { while((n=in.read(b))>0)d.update(b,0,n); }
            StringBuilder s=new StringBuilder(); for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));
            return s.toString().equalsIgnoreCase(expected);
        } catch(Exception e){ return false; }
    }

    private static String human(long b) {
        if (b<=0) return "?";
        double g=b/1073741824.0; if(g>=1) return String.format(Locale.US,"%.2f GB",g);
        return String.format(Locale.US,"%.0f MB",b/1048576.0);
    }

    private Notification notification(String text,int progress,boolean indeterminate){
        return new Notification.Builder(this,CH).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("LocalAI").setContentText(text).setOngoing(true)
            .setProgress(100,Math.max(0,progress),indeterminate).build();
    }
    private void send(String msg,int progress,boolean indeterminate,boolean ok){
        Intent i=new Intent(ACTION_STATUS).setPackage(getPackageName());
        i.putExtra("message",msg); i.putExtra("progress",progress); i.putExtra("indeterminate",indeterminate); i.putExtra("ok",ok);
        sendBroadcast(i);
    }
    @Override public void onDestroy(){ cancelled=true; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent){ return null; }
}
