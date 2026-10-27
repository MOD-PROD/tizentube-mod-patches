package app.gonzo.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {

    const val TIZENTUBE_PACKAGE = "io.gh.reisxd.tizentube.cobalt"
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    /**
     * TizenTube Cobalt: a standalone Cobalt (Chrobalt) build that runs the TizenTube
     * userscript on Android TV. It is NOT a modified copy of YouTube for Android TV.
     *
     * Every patch in this bundle is pinned to 2.0.3 only. Shipped as a single APK
     * (cobalt-arm.apk / cobalt-arm64.apk on the GitHub releases), not a split bundle.
     */
    val COMPATIBILITY_TIZENTUBE = Compatibility(
        name = "TizenTube", // Name shown by the Android launcher.
        packageName = TIZENTUBE_PACKAGE,
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF0000, // YouTube red, used for the app card in Morphe Manager.
        targets = listOf(
            AppTarget(version = "2.0.3"),
        ),
    )

    /**
     * YouTube (mobile app). Only used by version-independent patches such as "Change app name",
     * which edit the manifest and do not depend on the app's bytecode.
     *
     * `version = null` means any version. Declared as APK_REQUIRED like the official Morphe
     * YouTube patches: Morphe Manager then points users to the full (non-bundle) APK.
     */
    val COMPATIBILITY_YOUTUBE = Compatibility(
        name = "YouTube",
        packageName = YOUTUBE_PACKAGE,
        apkFileType = ApkFileType.APK_REQUIRED,
        appIconColor = 0xFF0033, // YouTube red, same as the official Morphe bundle.
        targets = listOf(
            AppTarget(version = null),
        ),
    )
}
