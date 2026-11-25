package io.github.phiscript.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ordering used by UpdateCatalog for both GitHub tags and installed version names. */
public final class ReleaseVersion {
    private static final Pattern TAG = Pattern.compile(
        "^(data|v)?([0-9]{1,6})\\.([0-9]{1,6})\\.([0-9]{1,6})(?:-r([0-9]{1,6})|[-+][A-Za-z0-9.-]+)?$",
        Pattern.CASE_INSENSITIVE);

    private ReleaseVersion() {}

    public static int[] parts(String tag) {
        if (tag == null) return null;
        Matcher match = TAG.matcher(tag.trim());
        if (!match.matches()) return null;
        int major = Integer.parseInt(match.group(2));
        // The early v0.x series and data-prefixed releases both predate the 1.0.0 relaunch.
        int series = major >= 1 && !"data".equalsIgnoreCase(match.group(1)) ? 1 : 0;
        return new int[] {
            series, major, Integer.parseInt(match.group(3)), Integer.parseInt(match.group(4)),
            match.group(5) == null ? 0 : Integer.parseInt(match.group(5))
        };
    }
}
