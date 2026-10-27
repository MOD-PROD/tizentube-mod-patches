package app.gonzo.patches.updates

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.returnEarly
import app.morphe.util.getNode
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import org.w3c.dom.Element

private const val EXTENSION_PACKAGE = "Lapp/gonzo/extension/"
private const val EXTENSION_CLASS = "${EXTENSION_PACKAGE}versioncode/VersionCodePatch;"

/** Original android:versionCode, read before it is overwritten. */
private var originalVersionCode = 0

/** Manifest side: remember the real version code, then set it to the maximum. */
private val disablePlayStoreUpdatesResourcePatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getNode("manifest") as Element
            originalVersionCode = manifest.getAttribute("android:versionCode").toInt()
        }
    }

    // finalize: run after every other patch, so nothing writes the version code back.
    finalize {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getNode("manifest") as Element
            // Play Store caps at 2_100_000_000, Android itself accepts Int.MAX_VALUE.
            // Either way the store never offers an "update" over it.
            manifest.setAttribute("android:versionCode", Int.MAX_VALUE.toString())
        }
    }
}

/**
 * Stops the Play Store from replacing the patched app with the stock one.
 *
 * Note: TizenTube is distributed through GitHub, not Google Play. This patch matters mostly
 * together with "Change installer source" (which makes the app claim it came from Play) or
 * when a store app is set to auto-update everything.
 */
@Suppress("unused")
val disablePlayStoreUpdatesPatch = bytecodePatch(
    name = "Disable Play Store updates",
    description = "Disables Play Store updates by setting the version code to the maximum allowed. " +
        "Reads of the version code inside the app still return the original value. " +
        "Does not work with root-mount installs.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE)

    dependsOn(disablePlayStoreUpdatesResourcePatch)

    extendWith("extensions/extension.mpe")

    finalize {
        // 1. Bake the original version code into the extension.
        Fingerprint(
            definingClass = EXTENSION_CLASS,
            name = "originalVersionCode",
        ).method.returnEarly(originalVersionCode)

        /** Never rewrite our own extension classes. */
        val notExtension = { _: Method, classDef: ClassDef ->
            !classDef.type.startsWith(EXTENSION_PACKAGE)
        }

        // 2. iget vA, vB, PackageInfo->versionCode:I
        //    -> invoke-static {vB}, getVersionCode(PackageInfo)I
        //       move-result vA
        //    iget uses 4-bit registers, so vA/vB always fit move-result (8-bit) and invoke/range.
        Fingerprint(
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.IGET,
                    smali = "Landroid/content/pm/PackageInfo;->versionCode:I",
                ),
            ),
            custom = notExtension,
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val instruction = getInstruction<TwoRegisterInstruction>(index)
            val resultRegister = instruction.registerA
            val packageInfoRegister = instruction.registerB

            replaceInstruction(
                index,
                "invoke-static/range { v$packageInfoRegister .. v$packageInfoRegister }, " +
                    "$EXTENSION_CLASS->getVersionCode(Landroid/content/pm/PackageInfo;)I",
            )
            addInstruction(index + 1, "move-result v$resultRegister")
        }

        // 3. invoke-virtual {vC}, PackageInfo->getLongVersionCode()J
        //    -> invoke-static/range {vC .. vC}, getVersionCodeLong(PackageInfo)J (move-result-wide unchanged)
        Fingerprint(
            filters = listOf(
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    smali = "Landroid/content/pm/PackageInfo;->getLongVersionCode()J",
                ),
            ),
            custom = notExtension,
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            if (getInstruction(index + 1).opcode != Opcode.MOVE_RESULT_WIDE) return@matchAllMethodIndicesForEach

            val register = getInstruction<FiveRegisterInstruction>(index).registerC
            replaceInstruction(
                index,
                "invoke-static/range { v$register .. v$register }, " +
                    "$EXTENSION_CLASS->getVersionCodeLong(Landroid/content/pm/PackageInfo;)J",
            )
        }
    }
}
