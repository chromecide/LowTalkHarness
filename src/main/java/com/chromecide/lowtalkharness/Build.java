package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.LowTalkPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Which build of LowTalk a result was observed on.
 *
 * <p>The record is filed per Hytale server version, which is the right shelf but not a whole answer. On
 * 2026-09-19 fifty-two checks were recorded against "0.7.0-pre.3" over an evening in which LowTalk itself was
 * rebuilt and redeployed five times. Every one of those results was honestly observed, and the file still
 * overstated: it read as one clean run against one thing, when half of it described jars that no longer
 * existed. A skeptical reader is right to discount a record that cannot tell those apart, and this one could
 * not.
 *
 * <p>The version string alone will not do it — every build of 0.4.0 carries the same one. So the identity is
 * the jar itself, hashed.
 *
 * <p><b>But not the version string inside it.</b> {@link #describe()} already prints the version beside the
 * hash, so putting it in the hash as well says the same thing twice and costs something real: bumping 0.4.0
 * to 0.5.0 moved the id and the release gate discounted a walk of 56 checks against a jar that was identical
 * in all 411 of its other entries. A day of testing thrown away to one string. The id answers "did the code
 * change", so the version is blanked before hashing and everything else in the manifest still counts — a
 * changed server-version range or a new dependency moves the id, as it should.
 */
public final class Build {

    private Build() {}

    /** Version and short content hash, e.g. {@code 0.4.0+hytale.0.7.0-pre.3 (3f9a1c02)}. */
    public static String describe() {
        JavaPlugin lowtalk = lowTalk();
        if (lowtalk == null) return "unknown";
        String version;
        try {
            version = String.valueOf(lowtalk.getManifest().getVersion());
        } catch (RuntimeException e) {
            version = "?";
        }
        String hash = hash(lowtalk.getFile());
        return hash == null ? version : version + " (" + hash + ")";
    }

    private static JavaPlugin lowTalk() {
        try {
            PluginManager plugins = PluginManager.get();
            if (plugins == null) return null;
            LowTalkPlugin p = plugins.getPlugin(LowTalkPlugin.class);
            return p instanceof JavaPlugin jp ? jp : null;
        } catch (RuntimeException | LinkageError e) {
            // a build identity is worth having and never worth failing a test run over
            return null;
        }
    }

    /** The manifest entry whose version is blanked before hashing, so a version bump alone leaves the id alone. */
    static final String MANIFEST = "manifest.json";

    /** Matches the manifest's own version value, whatever it is, so it can be replaced by a constant. */
    private static final java.util.regex.Pattern VERSION =
            java.util.regex.Pattern.compile("(\"Version\"\\s*:\\s*)\"[^\"]*\"");

    /**
     * First four bytes of a SHA-256 over the jar's entries, with the manifest's version blanked.
     *
     * <p>Entries are sorted by name and fed in as name then content. A zip makes no promise about the order it
     * hands its entries back, and an id that depended on that order could differ between two identical jars.
     *
     * <p>If the version cannot be found to blank it, the manifest is hashed as it stands. That is the old
     * behaviour, which discounts a walk it did not have to — the safe direction to fail, because the other one
     * carries a result over to a jar that might not deserve it.
     */
    static String hash(@Nonnull Path jar) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            java.util.List<String> names = zip.stream().filter(e -> !e.isDirectory())
                    .map(java.util.zip.ZipEntry::getName).sorted().toList();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String name : names) {
                digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byte[] content;
                try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                    content = in.readAllBytes();
                }
                if (name.equals(MANIFEST)) {
                    String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                    content = VERSION.matcher(text).replaceFirst("$1\"\"")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                digest.update(content);
            }
            byte[] out = digest.digest();
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) hex.append(String.format("%02x", out[i]));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
