package app.gonzo.patches.installer

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.all.misc.fix.changepackageinstaller.changePackageInstallerPatch

/**
 * Makes the app believe it was installed by an app store.
 *
 * The heavy lifting is done by the shared `changePackageInstallerPatch` from
 * morphe-patches-library: every call to
 *  - PackageManager.getInstallerPackageName(String)
 *  - InstallSourceInfo.getInstallingPackageName()
 *  - InstallSourceInfo.getInitiatingPackageName()
 * has its result replaced with the chosen package name, and
 * InstallSourceInfo.getPackageSource() returns PACKAGE_SOURCE_STORE.
 *
 * If TizenTube 2.0.3 has no such call, the shared patch logs it and changes nothing.
 */
@Suppress("unused")
val changeInstallerSourcePatch = resourcePatch(
    name = "Change installer source",
    description = "Spoofs the installer source so TizenTube appears to be installed from Google Play.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE)

    val packageInstallerName by stringOption(
        key = "packageInstallerName",
        default = "com.android.vending",
        values = mapOf(
            "Google Play" to "com.android.vending",
            "Amazon Appstore" to "com.amazon.venezia",
            "Samsung Galaxy Store" to "com.sec.android.app.samsungapps",
            "Huawei AppGallery" to "com.huawei.appmarket",
        ),
        title = "Spoofed package installer name",
        description = "Package name the app will see as its installer.",
        required = true,
    )

    // The provider is evaluated lazily, when the dependency executes, so the user's option value is used.
    dependsOn(changePackageInstallerPatch { packageInstallerName!! })
}
