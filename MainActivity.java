package com.dlox.doit;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private String insetsJs = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets k = insets.getInsets(WindowInsetsCompat.Type.ime());
            float d = getResources().getDisplayMetrics().density;
            insetsJs = "var r=document.documentElement.style;"
                + "r.setProperty('--sat','" + (s.top / d) + "px');"
                + "r.setProperty('--sab','" + (Math.max(s.bottom, k.bottom) / d) + "px');"
                + "r.setProperty('--sal','" + (s.left / d) + "px');"
                + "r.setProperty('--sar','" + (s.right / d) + "px');";
            push();
            int[] delays = new int[]{300, 900, 2000, 4000};
            for (int i = 0; i < delays.length; i++) {
                handler.postDelayed(this::push, delays[i]);
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void push() {
        if (insetsJs.isEmpty() || getBridge() == null || getBridge().getWebView() == null) {
            return;
        }
        getBridge().getWebView().evaluateJavascript(insetsJs, null);
    }
}
