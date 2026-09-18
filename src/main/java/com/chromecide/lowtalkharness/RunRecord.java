package com.chromecide.lowtalkharness;

import org.bson.Document;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What has been exercised, and how it went, on one server version.
 *
 * <p>One file per server version, because that is the question worth answering: not "does LowTalk work" but
 * "has this been tried on the version people are about to run". Testing 0.7 must not erase what 0.6.7 proved,
 * which is the thing an in-game progress tracker cannot do.
 *
 * <p>Two separate facts are kept per check. <b>Seen</b> is recorded by watching LowTalk's own events, so it is
 * never wrong and never forgotten: it means the passage was reached or the option was picked. <b>Verdict</b> is
 * a human saying whether it looked right, because no event can tell that a title rendered in the wrong style.
 * A check that is seen but has no verdict is the interesting case — it ran, nobody said whether it worked.
 */
public final class RunRecord {

    /** How a check stands on this version. */
    public enum Verdict { PASS, FAIL, SKIP }

    public static final class Check {
        public boolean seen;
        @Nullable public Verdict verdict;
        @Nullable public String note;
        @Nullable public String firstSeen;
        @Nullable public String decidedAt;

        Document toDocument() {
            Document d = new Document();
            d.put("seen", seen);
            if (verdict != null) d.put("verdict", verdict.name());
            if (note != null) d.put("note", note);
            if (firstSeen != null) d.put("firstSeen", firstSeen);
            if (decidedAt != null) d.put("decidedAt", decidedAt);
            return d;
        }

        static Check fromDocument(Document d) {
            Check c = new Check();
            c.seen = d.getBoolean("seen", false);
            String v = d.getString("verdict");
            if (v != null) {
                try {
                    c.verdict = Verdict.valueOf(v);
                } catch (IllegalArgumentException ignored) {
                    // a verdict written by a later version of this harness; keep the file, drop the value
                }
            }
            c.note = d.getString("note");
            c.firstSeen = d.getString("firstSeen");
            c.decidedAt = d.getString("decidedAt");
            return c;
        }
    }

    private final Path file;
    private final String serverVersion;
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private boolean dirty;

    public RunRecord(@Nonnull Path folder, @Nonnull String serverVersion) {
        this.serverVersion = serverVersion;
        // the version is part of the name, so a run against another server cannot overwrite this one
        this.file = folder.resolve("run-" + serverVersion.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
        load();
    }

    public String serverVersion() {
        return serverVersion;
    }

    public Path file() {
        return file;
    }

    /** The check as it stands, created empty if this is the first time it has come up. */
    public synchronized Check check(@Nonnull String id) {
        return checks.computeIfAbsent(id, k -> new Check());
    }

    /** Record that a check was exercised. Idempotent; the first time is the one that is timestamped. */
    public synchronized void markSeen(@Nonnull String id) {
        Check c = check(id);
        if (c.seen) return;
        c.seen = true;
        c.firstSeen = Instant.now().toString();
        dirty = true;
    }

    /** Record a human's verdict, with an optional note about what was wrong. */
    public synchronized void decide(@Nonnull String id, @Nonnull Verdict verdict, @Nullable String note) {
        Check c = check(id);
        c.verdict = verdict;
        c.note = note == null || note.isBlank() ? null : note.trim();
        c.decidedAt = Instant.now().toString();
        dirty = true;
    }

    /** Forget a check entirely, so it can be run again from nothing. */
    public synchronized boolean clear(@Nonnull String id) {
        boolean had = checks.remove(id) != null;
        if (had) dirty = true;
        return had;
    }

    public synchronized List<String> ids() {
        return new ArrayList<>(checks.keySet());
    }

    /** Counts: how many of the given checks are seen, decided, passed, failed. */
    public synchronized int[] tally(@Nonnull List<String> ids) {
        int seen = 0, decided = 0, passed = 0, failed = 0;
        for (String id : ids) {
            Check c = checks.get(id);
            if (c == null) continue;
            if (c.seen) seen++;
            if (c.verdict == null) continue;
            decided++;
            if (c.verdict == Verdict.PASS) passed++;
            if (c.verdict == Verdict.FAIL) failed++;
        }
        return new int[] {seen, decided, passed, failed};
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Document root = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
            Document cs = root.get("checks", Document.class);
            if (cs == null) return;
            for (String id : cs.keySet()) {
                Document d = cs.get(id, Document.class);
                if (d != null) checks.put(id, Check.fromDocument(d));
            }
        } catch (RuntimeException | IOException e) {
            // A record we cannot read is not worth losing a test run over, but it must not be silently
            // overwritten either: keep it out of the way so the run can start clean and the file can be looked at.
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".unreadable"));
            } catch (IOException ignored) {
                // nothing further to try; the run continues in memory
            }
        }
    }

    /** Write the record out if anything changed. */
    public synchronized void flush() {
        if (!dirty) return;
        Document cs = new Document();
        for (Map.Entry<String, Check> e : checks.entrySet()) cs.put(e.getKey(), e.getValue().toDocument());
        Document root = new Document();
        root.put("serverVersion", serverVersion);
        root.put("updated", Instant.now().toString());
        root.put("checks", cs);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".partial");
            Files.writeString(tmp, root.toJson(org.bson.json.JsonWriterSettings.builder().indent(true).build()) + "\n",
                    StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            throw new IllegalStateException("could not write the run record to " + file, e);
        }
    }
}
