package com.chromecide.lowtalkharness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which checks a Hytale update makes worth re-running.
 *
 * <p>Re-testing everything on every server release does not survive contact with a Tuesday, and testing nothing
 * is how a change slips through. Between the two: compare the two server jars class by class, and name the
 * checks whose {@code covers} tags mention something that moved.
 *
 * <p>Nothing here is clever. It compares CRCs, which is enough to tell a class that changed from one that did
 * not, and it matches tags against class names by substring. It answers "where should I look" rather than
 * "what broke", and it is honest about the checks it cannot reason about: anything tagged only with
 * {@code lowtalk:} surfaces is this mod's own behaviour, unaffected by the server changing underneath it, and
 * is reported separately rather than silently dropped.
 *
 * <p>Run against the archive kept by {@code tools/hytale-archive.sh}:
 *
 * <pre>
 *   ./gradlew whatChanged --args="~/hytale-archive/release/0.6.7 ~/hytale-archive/release/0.6.8"
 * </pre>
 */
public final class WhatChanged {

    private WhatChanged() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("usage: whatChanged <old jar or archive dir> <new jar or archive dir>");
            System.out.println("  e.g. ~/hytale-archive/release/0.6.7 ~/hytale-archive/release/0.6.8");
            return;
        }
        Path oldJar = resolveJar(args[0]);
        Path newJar = resolveJar(args[1]);
        if (oldJar == null || newJar == null) {
            System.out.println("could not find a server jar at " + (oldJar == null ? args[0] : args[1]));
            return;
        }

        Map<String, Long> before = classCrcs(oldJar);
        Map<String, Long> after = classCrcs(newJar);

        Set<String> changed = new TreeSet<>();
        for (Map.Entry<String, Long> e : after.entrySet()) {
            Long was = before.get(e.getKey());
            if (was == null || !was.equals(e.getValue())) changed.add(e.getKey());
        }
        for (String gone : before.keySet()) if (!after.containsKey(gone)) changed.add(gone);

        System.out.println(oldJar.getFileName() + " -> " + newJar.getFileName());
        System.out.println(before.size() + " classes before, " + after.size() + " after, " + changed.size() + " changed");

        // A check is worth re-running when one of the Hytale surfaces it names moved.
        Map<String, Set<String>> hits = new LinkedHashMap<>();
        List<Checks.Check> ourOwn = new ArrayList<>();
        for (Checks.Check c : Checks.all()) {
            Set<String> why = new LinkedHashSet<>();
            boolean namesServerSurface = false;
            for (String surface : c.covers()) {
                if (surface.toLowerCase(Locale.ROOT).startsWith("lowtalk:")) continue;
                namesServerSurface = true;
                for (String cls : changed) {
                    if (simpleName(cls).equalsIgnoreCase(surface) || cls.contains(surface)) {
                        why.add(simpleName(cls));
                        break;
                    }
                }
            }
            if (!why.isEmpty()) hits.put(c.id(), why);
            else if (!namesServerSurface) ourOwn.add(c);
        }

        System.out.println();
        if (hits.isEmpty()) {
            System.out.println("No check names anything that changed.");
        } else {
            System.out.println("Re-run these " + hits.size() + ":");
            for (Map.Entry<String, Set<String>> e : hits.entrySet()) {
                Checks.Check c = Checks.byId(e.getKey());
                System.out.printf("  %-28s %s%n", e.getKey(), c == null ? "" : c.title());
                System.out.println("      because " + String.join(", ", e.getValue()) + " changed");
            }
        }
        System.out.println();
        System.out.println(ourOwn.size() + " checks name only LowTalk's own behaviour, so a server change cannot");
        System.out.println("  reach them. They still need running when LowTalk itself changes.");

        // The dangerous case. "Nothing to re-run" after five hundred classes moved is reassuring and might be
        // wrong: it also says that of a class LowTalk leans on that no check has ever named. ISpawnProvider
        // changed shape in pre.3 and broke the build, and no check mentions it to this day.
        Set<String> used = surfacesLowTalkUses();
        if (!used.isEmpty()) {
            Set<String> covered = new LinkedHashSet<>();
            for (String s : Checks.coveredSurfaces()) covered.add(s.toLowerCase(Locale.ROOT));
            Set<String> blind = new TreeSet<>();
            for (String cls : changed) {
                String name = simpleName(cls);
                if (used.contains(name) && !covered.contains(name.toLowerCase(Locale.ROOT))) blind.add(name);
            }
            System.out.println();
            if (blind.isEmpty()) {
                System.out.println("Nothing changed that LowTalk uses and no check names.");
            } else {
                System.out.println("Changed, used by LowTalk, and named by no check (" + blind.size() + "):");
                for (String name : blind) System.out.println("  " + name);
                System.out.println("  These are the blind spots this update moved. Worth a check each.");
            }
        }
    }

    /**
     * The Hytale types LowTalk actually mentions, read from its source next door. Only the simple names, which
     * is all the coverage tags use. Returns empty when the sibling checkout is not there, rather than pretending
     * to know: a wrong answer here would report blind spots that are not real, or hide ones that are.
     */
    private static Set<String> surfacesLowTalkUses() {
        Path src = Path.of(System.getProperty("user.home"), "hytale-mods", "lowtalk", "src", "main", "java");
        if (!Files.isDirectory(src)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        java.util.regex.Pattern ref = java.util.regex.Pattern.compile("com\\.hypixel\\.hytale\\.[A-Za-z0-9_.]*?([A-Z][A-Za-z0-9_]*)");
        try (java.util.stream.Stream<Path> files = Files.walk(src)) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                java.util.regex.Matcher m = ref.matcher(Files.readString(f));
                while (m.find()) out.add(m.group(1));
            }
        } catch (IOException e) {
            return Set.of();
        }
        return out;
    }

    /** An archive directory holds HytaleServer.jar; a jar path is taken as given. */
    private static Path resolveJar(String raw) {
        Path p = Path.of(raw.replaceFirst("^~", System.getProperty("user.home")));
        if (Files.isRegularFile(p)) return p;
        Path inDir = p.resolve("HytaleServer.jar");
        return Files.isRegularFile(inDir) ? inDir : null;
    }

    private static Map<String, Long> classCrcs(Path jar) throws IOException {
        Map<String, Long> out = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().endsWith(".class")) out.put(e.getName(), e.getCrc());
            }
        }
        return out;
    }

    private static String simpleName(String classPath) {
        String s = classPath.substring(classPath.lastIndexOf('/') + 1);
        if (s.endsWith(".class")) s = s.substring(0, s.length() - ".class".length());
        int dollar = s.indexOf('$');
        return dollar < 0 ? s : s.substring(0, dollar);
    }
}
