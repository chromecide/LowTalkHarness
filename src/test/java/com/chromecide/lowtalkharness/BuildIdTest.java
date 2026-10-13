package com.chromecide.lowtalkharness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the build id is allowed to notice.
 *
 * <p>The id decides whether a walk of the test tree still describes the jar that ships, so what it reacts to is
 * a policy and not an implementation detail. It reacted to the version string once: 0.4.0 became 0.5.0, the id
 * moved, and the release gate discounted fifty-six checks against a jar identical in every other entry. These
 * tests are the statement that it may not do that again, and that it must still notice everything else.
 */
class BuildIdTest {

    private static final String MANIFEST = """
            {
                "Group": "Chromecide",
                "Name": "LowTalk",
                "Version": "%s",
                "ServerVersion": "%s",
                "Dependencies": { "Hytale:AssetModule": "*" }
            }""";

    private static Path jar(Path dir, String name, Map<String, String> entries) throws Exception {
        Path p = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(p); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return p;
    }

    private static Map<String, String> contents(String version, String serverVersion, String code) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Build.MANIFEST, MANIFEST.formatted(version, serverVersion));
        m.put("com/chromecide/lowtalk/LowTalkPlugin.class", code);
        m.put("Server/LowTalk/Dialogues/example.talk", "== start\nhello\n");
        return m;
    }

    @Test
    void aVersionBumpAloneDoesNotMoveTheId(@TempDir Path dir) throws Exception {
        String a = Build.hash(jar(dir, "a.jar", contents("0.4.0", ">=0.7.0 <0.8.0", "CODE")));
        String b = Build.hash(jar(dir, "b.jar", contents("0.5.0", ">=0.7.0 <0.8.0", "CODE")));
        assertNotNull(a);
        assertEquals(a, b, "a version bump is the one difference the id must ignore, or it throws away a walk "
                + "of a jar whose code never changed");
    }

    @Test
    void changedCodeMovesTheId(@TempDir Path dir) throws Exception {
        String a = Build.hash(jar(dir, "a.jar", contents("0.5.0", ">=0.7.0 <0.8.0", "CODE")));
        String b = Build.hash(jar(dir, "b.jar", contents("0.5.0", ">=0.7.0 <0.8.0", "CODE CHANGED")));
        assertNotEquals(a, b, "the id exists to notice this");
    }

    /** Not behaviour, but it decides what the jar will load against, and a walk cannot cover a jar that will not load. */
    @Test
    void aChangedServerVersionRangeMovesTheId(@TempDir Path dir) throws Exception {
        String a = Build.hash(jar(dir, "a.jar", contents("0.5.0", ">=0.7.0 <0.8.0", "CODE")));
        String b = Build.hash(jar(dir, "b.jar", contents("0.5.0", ">=0.6.8 <0.8.0", "CODE")));
        assertNotEquals(a, b, "only the version is blanked; the rest of the manifest still counts");
    }

    /** A shipped asset is as much of the mod as a class file: a changed dialogue is a changed jar. */
    @Test
    void aChangedShippedAssetMovesTheId(@TempDir Path dir) throws Exception {
        Map<String, String> changed = contents("0.5.0", ">=0.7.0 <0.8.0", "CODE");
        changed.put("Server/LowTalk/Dialogues/example.talk", "== start\ngoodbye\n");
        String a = Build.hash(jar(dir, "a.jar", contents("0.5.0", ">=0.7.0 <0.8.0", "CODE")));
        String b = Build.hash(jar(dir, "b.jar", changed));
        assertNotEquals(a, b, "shipped assets are part of what was walked");
    }

    /** A zip promises nothing about the order it hands entries back, so the id must not depend on it. */
    @Test
    void entryOrderDoesNotMoveTheId(@TempDir Path dir) throws Exception {
        Map<String, String> forward = contents("0.5.0", ">=0.7.0 <0.8.0", "CODE");
        Map<String, String> reversed = new LinkedHashMap<>();
        forward.entrySet().stream().toList().reversed().forEach(e -> reversed.put(e.getKey(), e.getValue()));
        assertEquals(Build.hash(jar(dir, "a.jar", forward)), Build.hash(jar(dir, "b.jar", reversed)));
    }

    /** A jar it cannot read has no identity; it must say so rather than invent one. */
    @Test
    void anUnreadableJarHasNoId(@TempDir Path dir) throws Exception {
        Path notAJar = dir.resolve("broken.jar");
        Files.writeString(notAJar, "this is not a zip");
        org.junit.jupiter.api.Assertions.assertNull(Build.hash(notAJar));
    }
}
