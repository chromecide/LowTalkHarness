plugins {
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}

tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

group = project.property("group").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
    hytaleHomeOverride = property("hytaleHomeOverride").toString()
}

repositories {
    mavenCentral()
}

// LowTalk comes from the sibling checkout through a composite build (settings.gradle.kts includeBuild), so both mods
// are compiled from source at matching commits; the Hytale plugin stages it as a mod for runServer.
dependencies {
    vineImplementation("com.chromecide.lowtalk:LowTalk:${property("lowtalk_version")}")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
}

// ---- one commit, one jar per Hytale patchline (same scheme as LowTalk) --------------------------------------------
val distDir = layout.buildDirectory.dir("dist")

tasks.register<Copy>("collectRelease") {
    group = "distribution"
    description = "Copies the release-line jar into build/dist."
    dependsOn("build")
    from(tasks.named<Jar>("jar").map { it.archiveFile })
    into(distDir)
}

tasks.register<Exec>("buildPreRelease") {
    group = "distribution"
    description = "Builds the jar for the Hytale pre-release line into build/dist (LowTalk is built the same way through the composite build)."
    val preVersion = project.property("prerelease_hytale_version").toString()
    val modVersion = project.property("version").toString()
    workingDir = projectDir
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew",
        "jar", "--console=plain",
        "-Ppatchline=pre-release",
        "-Phytale_version=$preVersion",
        "-Pserver_version=$preVersion",
        "-PmanifestServerVersion=${project.property("prerelease_manifestServerVersion")}",
        "-PhytaleHomeOverride=${project.property("prerelease_hytaleHomeOverride")}",
        "-Pversion=$modVersion+hytale.$preVersion",
        "-PjarDir=${distDir.get().asFile.absolutePath}"
    )
    doFirst { distDir.get().asFile.mkdirs() }
}

tasks.register("buildAll") {
    group = "distribution"
    description = "Builds the release-line and pre-release jars into build/dist."
    dependsOn("collectRelease")
    finalizedBy("buildPreRelease")
}

if (project.hasProperty("jarDir")) {
    tasks.named<Jar>("jar") {
        destinationDirectory.set(file(project.property("jarDir").toString()))
    }
}
