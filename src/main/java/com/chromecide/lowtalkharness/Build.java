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
 * the jar itself, hashed. Two builds differ if and only if their bytes do, which is exactly the question.
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

    /** First four bytes of the jar's SHA-256, which is plenty to tell one evening's builds apart. */
    private static String hash(@Nonnull Path jar) {
        try (InputStream in = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            byte[] out = digest.digest();
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) hex.append(String.format("%02x", out[i]));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
