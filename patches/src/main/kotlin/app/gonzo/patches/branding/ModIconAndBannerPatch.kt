package app.gonzo.patches.branding

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.asSequence
import app.morphe.util.getNode
import org.w3c.dom.Element
import java.util.logging.Logger

/**
 * Root of the bundled artwork inside the .mpp (see patches/src/main/resources/tizentube/branding/).
 *
 *   tizentube/branding/icon/<density>.png    -> replaces android:icon   (e.g. @mipmap/ic_app)
 *   tizentube/branding/banner/<density>.png  -> replaces android:banner (e.g. @drawable/app_banner)
 *
 * <density> is one of: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi.
 */
private const val BRANDING_ROOT = "/tizentube/branding"

private val DENSITIES = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

/** Anchor class so bundled resources are loaded through the patch bundle's own classloader. */
private object BrandingResources

private val logger = Logger.getLogger("ModIconAndBannerPatch")

/**
 * Maps a resource directory to the density bucket of the artwork to copy.
 *  "drawable"             -> mdpi (baseline)
 *  "drawable-xhdpi-v4"    -> xhdpi
 *  "mipmap-nodpi"         -> xxxhdpi (never scaled up, so use the sharpest image)
 *  "mipmap-anydpi-v26"    -> null (vector / adaptive XML, not a bitmap)
 */
private fun densityOf(directory: String): String? {
    val qualifiers = directory.split('-').drop(1)
    return when {
        "anydpi" in qualifiers -> null
        "nodpi" in qualifiers -> "xxxhdpi"
        // No density qualifier (or ldpi) -> baseline mdpi image.
        else -> DENSITIES.lastOrNull { it in qualifiers } ?: "mdpi"
    }
}

/**
 * Replaces the TizenTube launcher artwork with the YouTube-style icon and banner bundled in
 * patches/src/main/resources/tizentube/branding/ (used as they are, no overlay added).
 *
 * On Android TV the home screen draws `android:banner`, NOT `android:icon`; a patch that only
 * swapped the icon would be invisible on the TV launcher, so both are replaced.
 *
 * Resource names are not hardcoded: they are read from the manifest (<application> and every
 * <activity>) so the patch keeps working if a release renames ic_app / app_banner.
 * In TizenTube 2.0.x they are @mipmap/ic_app and @drawable/app_banner.
 *
 * Must stay a [resourcePatch] (not a raw resource patch): resources must be decoded so that
 * `listDecodableFiles("res/...")` and `get("res/...")` can resolve the resource paths.
 */
@Suppress("unused")
val modIconAndBannerPatch = resourcePatch(
    name = "MOD Icon and Banner",
    description = "Replaces the app icon and the Android TV launcher banner with YouTube artwork. " +
        "On Android TV the launcher shows the banner, so both are replaced.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE)

    val replaceIcon by booleanOption(
        key = "replaceIcon",
        default = true,
        title = "Replace icon",
        description = "Replace android:icon (app list, settings, phone launchers).",
    )

    val replaceBanner by booleanOption(
        key = "replaceBanner",
        default = true,
        title = "Replace banner",
        description = "Replace android:banner (Android TV / Google TV home screen).",
    )

    execute {
        // 1. Collect the resource references used for the icon and the banner.
        val iconRefs = mutableSetOf<String>()
        val bannerRefs = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            val application = document.getNode("application") as Element
            val activities = document.getElementsByTagName("activity").asSequence().map { it as Element }

            (sequenceOf(application) + activities).forEach { element ->
                element.getAttribute("android:icon").takeIf { it.startsWith('@') }?.let(iconRefs::add)
                element.getAttribute("android:roundIcon").takeIf { it.startsWith('@') }?.let(iconRefs::add)
                element.getAttribute("android:banner").takeIf { it.startsWith('@') }?.let(bannerRefs::add)
            }
        }

        if (iconRefs.isEmpty() && bannerRefs.isEmpty()) {
            throw PatchException("No android:icon or android:banner found in the manifest")
        }

        // 2. Replace every bitmap of a reference, density by density.
        fun replaceAll(references: Set<String>, kind: String): Int {
            var replaced = 0

            for (reference in references) {
                // "@mipmap/ic_app" -> type "mipmap", name "ic_app"
                val type = reference.substringAfter('@').substringBefore('/')
                val name = reference.substringAfter('/')

                // Matches res/mipmap-xhdpi-v4/ic_app.png, res/drawable/app_banner, ...
                // (TizenTube ships some resource files without extension, so both forms are matched.)
                val paths = listDecodableFiles("res/$type*/$name*").filter { path ->
                    val fileName = path.substringAfterLast('/')
                    fileName == name || fileName.substringBeforeLast('.') == name
                }

                for (path in paths) {
                    val directory = path.split('/')[1]

                    if (path.endsWith(".xml")) {
                        logger.warning(
                            "$path is an XML drawable (adaptive or vector), it is not replaced. " +
                                "On API 26+ it may take priority over the bitmaps.",
                        )
                        continue
                    }

                    val density = densityOf(directory) ?: continue
                    val source = BrandingResources::class.java.getResourceAsStream("$BRANDING_ROOT/$kind/$density.png")
                        ?: throw PatchException("Missing bundled artwork: $BRANDING_ROOT/$kind/$density.png")

                    // PNG bytes are written even if the target is .webp or has no extension:
                    // Android decodes bitmaps by content, not by file extension.
                    source.use { input -> get(path).outputStream().use(input::copyTo) }
                    replaced++
                }
            }

            return replaced
        }

        val icons = if (replaceIcon == true) replaceAll(iconRefs, "icon") else 0
        val banners = if (replaceBanner == true) replaceAll(bannerRefs, "banner") else 0

        logger.info("MOD Icon and Banner: replaced $icons icon file(s) and $banners banner file(s)")

        if (replaceBanner == true && banners == 0) {
            logger.warning("No banner bitmap was replaced, the Android TV launcher will still show the old banner.")
        }
    }
}
