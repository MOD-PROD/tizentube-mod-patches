package app.gonzo.patches.network

import app.gonzo.patches.shared.Constants.COMPATIBILITY_TIZENTUBE
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.adoptChild
import app.morphe.util.getNode
import org.w3c.dom.Element

private const val NETWORK_SECURITY_CONFIG_ATTRIBUTE = "android:networkSecurityConfig"
private const val DEFAULT_CONFIG_NAME = "network_security_config"

/** Config written when the app ships none: system + user CAs, user CAs may override pins. */
private val NEW_NETWORK_SECURITY_CONFIG = """
    <?xml version="1.0" encoding="utf-8"?>
    <network-security-config>
        <base-config cleartextTrafficPermitted="true">
            <trust-anchors>
                <certificates src="system" />
                <certificates src="user" overridePins="true" />
            </trust-anchors>
        </base-config>
        <debug-overrides>
            <trust-anchors>
                <certificates src="system" />
                <certificates src="user" overridePins="true" />
            </trust-anchors>
        </debug-overrides>
    </network-security-config>
""".trimIndent()

/**
 * Trusts user-installed CA certificates and lets them override certificate pins,
 * so the traffic of TizenTube can be inspected with a proxy (mitmproxy, HTTP Toolkit, ...).
 *
 * How it applies to Cobalt: the Chromium network stack inside Cobalt verifies certificates
 * through Android's X509 trust manager, which honours android:networkSecurityConfig.
 * Chromium also skips its built-in static pins when the chain ends at a locally-installed root.
 *
 * Logic (same as the official Morphe patch):
 *  - the manifest gets android:networkSecurityConfig if it has none,
 *  - an existing config is extended: <certificates src="user" overridePins="true"/> is added
 *    to every <trust-anchors> of <base-config> and <debug-overrides>,
 *  - otherwise a fresh config is written.
 */
@Suppress("unused")
val overrideCertificatePinningPatch = resourcePatch(
    name = "Override certificate pinning",
    description = "Trusts user-installed CA certificates and overrides certificate pinning, " +
        "allowing to inspect the app traffic through a proxy.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIZENTUBE)

    execute {
        var configName = DEFAULT_CONFIG_NAME

        // 1. Point the manifest to a network security config (reuse the existing one if present).
        document("AndroidManifest.xml").use { document ->
            val application = document.getNode("application") as Element

            if (application.hasAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE)) {
                // "@xml/foo" -> "foo"
                configName = application.getAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE).substringAfter('/')
            } else {
                application.setAttribute(NETWORK_SECURITY_CONFIG_ATTRIBUTE, "@xml/$DEFAULT_CONFIG_NAME")
            }
        }

        val configPath = "res/xml/$configName.xml"
        val configFile = get(configPath)

        // 2a. No config shipped: write ours.
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.writeText(NEW_NETWORK_SECURITY_CONFIG)
            return@execute
        }

        // 2b. Config shipped: make sure user CAs are trusted and can override pins.
        document(configPath).use { document ->
            val root = document.getNode("network-security-config")

            for (tagName in arrayOf("base-config", "debug-overrides")) {
                val config = document.getNode(tagName) as? Element ?: run {
                    root.adoptChild(tagName) {
                        if (tagName == "base-config") setAttribute("cleartextTrafficPermitted", "true")
                    }
                    document.getNode(tagName) as Element
                }

                // A config without <trust-anchors> only trusts system CAs: add one.
                if (config.getElementsByTagName("trust-anchors").length == 0) {
                    config.adoptChild("trust-anchors") {
                        adoptChild("certificates") { setAttribute("src", "system") }
                    }
                }

                val trustAnchorsList = config.getElementsByTagName("trust-anchors")
                for (i in 0 until trustAnchorsList.length) {
                    val trustAnchors = trustAnchorsList.item(i) as Element
                    val certificates = trustAnchors.getElementsByTagName("certificates")

                    var userCertificates: Element? = null
                    for (j in 0 until certificates.length) {
                        val node = certificates.item(j) as Element
                        if (node.getAttribute("src") == "user") userCertificates = node
                    }

                    if (userCertificates != null) {
                        userCertificates.setAttribute("overridePins", "true")
                    } else {
                        trustAnchors.adoptChild("certificates") {
                            setAttribute("src", "user")
                            setAttribute("overridePins", "true")
                        }
                    }
                }
            }
        }
    }
}
