package com.dlox.doit;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Base64;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@CapacitorPlugin(name = "FocusBlocker")
public class FocusBlockerPlugin extends Plugin {

    private boolean hasUsage() {
        Context c = getContext();
        AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        } else {
            mode = ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private String iconOf(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 56, 56);
            d.draw(cv);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 90, bo);
            return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        o.put("usage", hasUsage());
        o.put("overlay", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext()));
        call.resolve(o);
    }

    @PluginMethod
    public void openUsage(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openOverlay(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void apps(PluginCall call) {
        try {
            PackageManager pm = getContext().getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            Collections.sort(list, new ResolveInfo.DisplayNameComparator(pm));
            HashSet<String> seen = new HashSet<String>();
            JSArray out = new JSArray();
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getContext().getPackageName()) || !seen.add(pkg)) {
                    continue;
                }
                JSObject o = new JSObject();
                o.put("pkg", pkg);
                o.put("name", r.loadLabel(pm).toString());
                o.put("icon", iconOf(r.loadIcon(pm)));
                out.put(o);
            }
            JSObject res = new JSObject();
            res.put("apps", out);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Could not list apps");
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        Intent i = new Intent(getContext(), BlockService.class);
        JSArray a = call.getArray("pkgs");
        String[] arr = new String[a == null ? 0 : a.length()];
        for (int k = 0; k < arr.length; k++) {
            arr[k] = a.optString(k);
        }
        i.putExtra("pkgs", arr);
        i.putExtra("mode", call.getString("mode", "block"));
        Long u = call.getLong("until", 0L);
        i.putExtra("until", u == null ? 0L : u.longValue());
        ContextCompat.startForegroundService(getContext(), i);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), BlockService.class));
        call.resolve();
    }
}
