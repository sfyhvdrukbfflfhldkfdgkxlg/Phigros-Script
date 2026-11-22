package io.github.phiscript.root;

import android.graphics.Color;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/** Paint behind system bars, but keep controls outside the handle, cutout and keyboard. */
final class HomeInsets {
    private HomeInsets() {}

    static void install(Window window, View root, View controls, Runnable updateScrollPadding) {
        View decor = window.getDecorView();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
            decor.setForceDarkAllowed(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            int darkIcons = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(darkIcons, darkIcons);
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            int softInput = window.getAttributes().softInputMode;
            window.setSoftInputMode((softInput & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            apply(controls, insets);
            updateScrollPadding.run();
            return insets;
        });
        root.requestApplyInsets();
    }

    static WindowInsets apply(View view, WindowInsets window) {
        int left, top, right, bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets safe = window.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime()
                    | WindowInsets.Type.mandatorySystemGestures());
            left = safe.left;
            top = safe.top;
            right = safe.right;
            bottom = safe.bottom;
        } else {
            left = window.getSystemWindowInsetLeft();
            top = window.getSystemWindowInsetTop();
            right = window.getSystemWindowInsetRight();
            bottom = window.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = window.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                android.graphics.Insets handle = window.getMandatorySystemGestureInsets();
                left = Math.max(left, handle.left);
                top = Math.max(top, handle.top);
                right = Math.max(right, handle.right);
                bottom = Math.max(bottom, handle.bottom);
            }
        }
        if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                || view.getPaddingRight() != right || view.getPaddingBottom() != bottom) {
            view.setPadding(left, top, right, bottom);
        }
        return window;
    }

    /** ScrollView clips to its full bounds; this padding places the last action above the dock. */
    static void padPage(View page, View controls, int dockHeight) {
        int bottom=controls.getPaddingBottom()+dockHeight;
        if(page.getPaddingLeft()!=controls.getPaddingLeft() || page.getPaddingTop()!=controls.getPaddingTop()
                || page.getPaddingRight()!=controls.getPaddingRight() || page.getPaddingBottom()!=bottom) {
            page.setPadding(controls.getPaddingLeft(),controls.getPaddingTop(),controls.getPaddingRight(),bottom);
        }
    }
}
