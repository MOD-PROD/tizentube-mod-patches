package app.gonzo.patches.branding

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.gonzo.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.asSequence
import app.morphe.util.getNode
import org.w3c.dom.Element
import java.util.logging.Logger

/**
 * Renames the app as shown by the launcher, the app list and the system settings.
 *
 * The label of <application> is read first; every <activity> / <activity-alias> using the very
 * same label (typically the launcher activity, which on Android TV defines the name shown under
 * the banner) gets the new name too. Activities with their own distinct label are left alone.
 *
 * The new name is written as a literal attribute value, which works whether the original was
 * a literal or a @string reference, and does not touch strings.xml (so no other text changes).
 *
 * Line break: typing `\n` in the option (e.g. `YouTube\nPremium`) inserts a real newline, so
 * the launcher can show the name on two lines. Whether both lines are displayed depends on the
 * launcher (most phone launchers allow two lines; some TV launchers show a single one).
 *
 * Version independent: it only edits the manifest, so it applies to TizenTube and to YouTube.
 */
@Suppress("unused")
val changeAppNamePatch = resourcePatch(
    name = "Change app name",
    description = "Changes the app name shown on the launcher and in the app list.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE, COMPATIBILITY_YOUTUBE)

    val appName by stringOption(
        key = "appName",
        default = "YouTube",
        values = mapOf(
            "YouTube" to "YouTube",
            "YouTube MOD" to "YouTube MOD",
            "TizenTube MOD" to "TizenTube MOD",
            "YouTube Premium (2 lignes)" to "YouTube\nPremium",
        ),
        title = "App name",
        description = "Name shown under the icon / banner. Any text works. " +
            "Write \\n for a line break, e.g. YouTube\\nPremium.",
        required = true,
    ) {
        // A leading @ or ? would be read by Android as a resource reference.
        !it.isNullOrBlank() && it.trim().first() !in "@?"
    }

    execute {
        // A typed \n (backslash + n) in the option becomes a real line break.
        val newName = appName!!.trim().replace("\\n", "\n")

        document("AndroidManifest.xml").use { document ->
            val application = document.getNode("application") as Element
            val originalLabel = application.getAttribute("android:label")
            if (originalLabel.isEmpty()) throw PatchException("<application> has no android:label")

            application.setAttribute("android:label", newName)
            var renamed = 1

            for (tag in arrayOf("activity", "activity-alias")) {
                document.getElementsByTagName(tag).asSequence().map { it as Element }
                    .filter { it.getAttribute("android:label") == originalLabel }
                    .forEach {
                        it.setAttribute("android:label", newName)
                        renamed++
                    }
            }

            Logger.getLogger("ChangeAppNamePatch").info(
                "App renamed to \"${newName.replace("\n", "\\n")}\" ($renamed label(s), was $originalLabel)",
            )
        }
    }
}
