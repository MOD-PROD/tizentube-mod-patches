package app.gonzo.patches.signature

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.fix.spoofsignature.spoofSignaturePatch as sharedSpoofSignaturePatch

/**
 * Makes PackageManager.getPackageInfo(...) return the signature of the ORIGINAL APK,
 * even though the patched APK is re-signed with the Morphe key.
 *
 * Wraps the shared `spoofSignaturePatch` from morphe-patches-library, which:
 *  1. reads the signing certificate of the input APK (the original file is required,
 *     an already re-signed APK is rejected),
 *  2. adds its own extension class (signature.dex) to the APK,
 *  3. redirects every getPackageInfo() call to that class, which patches the
 *     `signatures` / `signingInfo` fields of the returned PackageInfo.
 *
 * Useful when TizenTube (or a library it embeds) compares its own signature.
 */
@Suppress("unused")
val spoofSignaturePatch = resourcePatch(
    name = "Spoof signature",
    description = "Spoofs the package signature of the original APK. Requires the original, unmodified TizenTube APK.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE)

    dependsOn(sharedSpoofSignaturePatch)
}
