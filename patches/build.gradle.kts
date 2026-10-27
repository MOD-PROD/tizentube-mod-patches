group = "app.gonzo"

patches {
    about {
        name = "TizenTube MOD Patches"
        description = "Patches for TizenTube Cobalt (io.gh.reisxd.tizentube.cobalt) 2.0.3, compatible with Morphe."
        source = "git@github.com:MOD-PROD/tizentube-mod-patches.git"
        author = "Gonzo"
        contact = "https://github.com/MOD-PROD/tizentube-mod-patches/issues"
        website = "https://github.com/MOD-PROD/tizentube-mod-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the .mpp file.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    // Bundled into the .mpp: provides changePackageInstallerPatch, spoofSignaturePatch
    // (with its prebuilt signature.dex) and the XML/resource helpers.
    implementation(libs.morphe.patches.library)

    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
