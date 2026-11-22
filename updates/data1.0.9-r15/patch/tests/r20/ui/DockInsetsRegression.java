package io.github.phiscript.root;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

public final class DockInsetsRegression {
    public static void main(String[] args) {
        View dock = new View(new Context()), page = new View(new Context());
        WindowInsets insets = new WindowInsets();
        Build.VERSION.SDK_INT = 35;
        insets.bars = new Insets(0, 24, 0, 20);
        HomeInsets.apply(dock, insets);
        HomeInsets.padPage(page, dock, 68);
        if (page.getPaddingTop() != 24 || page.getPaddingBottom() != 88 || dock.getPaddingBottom() != 20)
            throw new AssertionError("End of page must be reachable above the floating navigation");
        int writes = page.paddingWrites;
        HomeInsets.padPage(page, dock, 68);
        if (page.paddingWrites != writes) throw new AssertionError("Dock requested another layout");
        insets.bars = new Insets(0, 0, 48, 0);
        insets.keyboard = new Insets(0, 0, 0, 310);
        HomeInsets.apply(dock, insets);
        HomeInsets.padPage(page, dock, 62);
        if (page.getPaddingRight() != 48 || page.getPaddingBottom() != 372 || page.getPaddingTop() != 0)
            throw new AssertionError("Stale bottom or side navigation padding after rotation / IME");
        System.out.println("PASS floating dock end-of-scroll clearance, keyboard and rotated insets (host model).");
    }
}
