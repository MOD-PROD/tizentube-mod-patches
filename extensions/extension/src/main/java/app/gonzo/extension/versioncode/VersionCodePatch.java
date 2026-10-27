package app.gonzo.extension.versioncode;

import android.content.pm.PackageInfo;
import android.os.Build;

/**
 * Runtime half of "Disable Play Store updates".
 *
 * The patch raises android:versionCode to Integer.MAX_VALUE so the Play Store never sees a
 * "newer" version. Code that reads its own versionCode would then get a nonsense value, so every
 * read of PackageInfo.versionCode / getLongVersionCode() is redirected here and the original
 * value is handed back.
 */
@SuppressWarnings("unused")
public final class VersionCodePatch {

    private VersionCodePatch() {
    }

    /** Return value is replaced at patch time with the original android:versionCode. */
    private static int originalVersionCode() {
        return 0;
    }

    /** Injection point: replaces {@code iget PackageInfo->versionCode:I}. */
    @SuppressWarnings("deprecation")
    public static int getVersionCode(PackageInfo info) {
        final int versionCode = info.versionCode;
        return versionCode == Integer.MAX_VALUE ? originalVersionCode() : versionCode;
    }

    /** Injection point: replaces {@code PackageInfo.getLongVersionCode()}. */
    public static long getVersionCodeLong(PackageInfo info) {
        if (Build.VERSION.SDK_INT < 28) {
            return getVersionCode(info);
        }
        final long versionCode = info.getLongVersionCode();
        final long lowerBits = 0x00000000FFFFFFFFL;
        if ((versionCode & lowerBits) == Integer.MAX_VALUE) {
            // Keep versionCodeMajor (upper 32 bits), restore the original lower 32 bits.
            return (versionCode & 0xFFFFFFFF00000000L) | (originalVersionCode() & lowerBits);
        }
        return versionCode;
    }
}
