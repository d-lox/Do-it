package com.dlox.doit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import java.util.HashSet;
import java.util.Set;

public class BlockService extends Service {
    private static final String CH = "doit_block";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> picked = new HashSet<String>();
    private final Set<String> safe = new HashSet<String>();
    private boolean allowMode = false;
    private boolean running = false;
    private long until = 0;
    private long lastTs = 0;
    private String current = "";
    private View overlay = null;
    private WindowManager wm;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() > until) {
                stopSelf();
                return;
            }
            poll();
            handler.postDelayed(this, 700);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        picked.clear();
        if (intent != null) {
            String[] arr = intent.getStringArrayExtra("pkgs");
            if (arr != null) {
                for (String s : arr) {
                    picked.add(s);
                }
            }
            allowMode = "allow".equals(intent.getStringExtra("mode"));
            until = intent.getLongExtra("until", 0);
        }
        buildSafe();
        startAsForeground();
        if (!running) {
            running = true;
            handler.post(loop);
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Focus blocking", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open == null) {
            open = new Intent();
        }
        PendingIntent po = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, BlockService.class);
        stop.setAction("STOP");
        PendingIntent ps = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        int icon = getResources().getIdentifier("ic_stat_doit", "drawable", getPackageName());
        if (icon == 0) {
            icon = android.R.drawable.ic_dialog_info;
        }
        Notification n = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(icon)
            .setContentTitle("Focus mode is on")
            .setContentText("Blocked apps are paused while your timer runs.")
            .setContentIntent(po)
            .addAction(0, "Stop blocking", ps)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, n);
        }
    }

    private void buildSafe() {
        safe.clear();
        safe.add(getPackageName());
        safe.add("android");
        safe.add("com.android.systemui");
        safe.add("com.android.settings");
        safe.add("com.android.phone");
        safe.add("com.android.server.telecom");
        safe.add("com.android.dialer");
        safe.add("com.google.android.dialer");
        safe.add("com.samsung.android.dialer");
        safe.add("com.samsung.android.incallui");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0)) {
            safe.add(r.activityInfo.packageName);
        }
    }

    private void poll() {
        try {
            UsageStatsManager u = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = u.queryEvents(lastTs == 0 ? now - 600000 : lastTs, now);
            if (ev != null) {
                UsageEvents.Event e = new UsageEvents.Event();
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e);
                    if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        current = e.getPackageName();
                    }
                }
            }
            lastTs = now - 2000;
        } catch (Exception ex) {
            return;
        }
        boolean blocked = false;
        if (!current.isEmpty() && !current.equals(getPackageName())) {
            if (allowMode) {
                blocked = !picked.contains(current) && !safe.contains(current);
            } else {
                blocked = picked.contains(current);
            }
        }
        if (blocked) {
            showOverlay(current);
        } else {
            hideOverlay();
        }
    }

    private void showOverlay(String pkg) {
        if (overlay != null || wm == null) {
            return;
        }
        String label = pkg;
        try {
            label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            label = pkg;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xF2101018);
        box.setPadding(72, 72, 72, 72);
        TextView t = new TextView(this);
        t.setText("Stay focused");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(30);
        t.setGravity(Gravity.CENTER);
        TextView s = new TextView(this);
        s.setText(label + " is blocked while your Do It timer is running.");
        s.setTextColor(0xFFB9AEC9);
        s.setTextSize(16);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, 24, 0, 48);
        Button b1 = new Button(this);
        b1.setText("Back to Do It");
        b1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            }
        });
        Button b2 = new Button(this);
        b2.setText("Go home");
        b2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent h = new Intent(Intent.ACTION_MAIN);
                h.addCategory(Intent.CATEGORY_HOME);
                h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(h);
            }
        });
        box.addView(t);
        box.addView(s);
        box.addView(b1);
        box.addView(b2);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        try {
            wm.addView(box, p);
            overlay = box;
        } catch (Exception e) {
            overlay = null;
        }
    }

    private void hideOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception e) {
                // ignore
            }
        }
        overlay = null;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
        super.onDestroy();
    }
}
