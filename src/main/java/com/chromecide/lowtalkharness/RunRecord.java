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
 * what somebody said about it afterwards, because no event can tell that a title rendered in the wrong style.
 *
 * <p><b>Silence is a pass.</b> A check that ran and drew no comment counts as PASS. That is not laziness, it is
 * the honest reading of what happened: a person walked the tree, watched the effect, and moved on. Asking them
 * to then type {@code /harness pass} forty-eight times produces forty-eight keystrokes of no information and a
 * strong pull towards typing them without looking. What carries information is a complaint, so a complaint is
 * the thing that has to be typed — see {@link #addFeedback}. An explicit verdict still wins over the implied
 * one, whoever recorded it: a command that threw fails its check without anyone having to notice.
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
        /** The LowTalk build this was last observed on. A result from an older jar is evidence about that jar. */
        @Nullable public String build;

        /**
         * What this check counts as, taking silence for a pass. Null means nobody has run it and nobody has
         * ruled on it, which is the only state that is genuinely unknown.
         */
        @Nullable
        public Verdict outcome() {
            if (verdict != null) return verdict;
            return seen ? Verdict.PASS : null;
        }

        /** True when the pass is only implied by having run, so nothing was said about it either way. */
        public boolean impliedPass() {
            return verdict == null && seen;
        }

        Document toDocument() {
            Document d = new Document();
            d.put("seen", seen);
            if (verdict != null) d.put("verdict", verdict.name());
            if (note != null) d.put("note", note);
            if (firstSeen != null) d.put("firstSeen", firstSeen);
            if (decidedAt != null) d.put("decidedAt", decidedAt);
            if (build != null) d.put("build", build);
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
            c.build = d.getString("build");
            return c;
        }
    }

    /**
     * Something a tester said was wrong, in their own words, at the moment they noticed.
     *
     * <p>Free text on purpose. "station 5 title didn't show when i selected goblin" is written in two seconds
     * without leaving the game or looking up an id; the same complaint routed through a check id is written in
     * twenty, or not at all. Where the player was standing is recorded alongside it, so the id can usually be
     * recovered afterwards without the person having to supply it.
     */
    public static final class Feedback {
        public String at = Instant.now().toString();
        public String text = "";
        @Nullable public String player;
        @Nullable public String dialogue;
        @Nullable public String node;
        /** The checks this was taken to be about, which are also marked FAIL. Empty when it was free-floating. */
        public List<String> checks = new ArrayList<>();
        /** What was done about it, once something was. Null while it is still outstanding. */
        @Nullable public String resolution;
        @Nullable public String resolvedAt;

        Document toDocument() {
            Document d = new Document();
            d.put("at", at);
            d.put("text", text);
            if (player != null) d.put("player", player);
            if (dialogue != null) d.put("dialogue", dialogue);
            if (node != null) d.put("node", node);
            if (!checks.isEmpty()) d.put("checks", new ArrayList<>(checks));
            if (resolution != null) d.put("resolution", resolution);
            if (resolvedAt != null) d.put("resolvedAt", resolvedAt);
            return d;
        }

        @SuppressWarnings("unchecked")
        static Feedback fromDocument(Document d) {
            Feedback f = new Feedback();
            f.at = d.getString("at") == null ? f.at : d.getString("at");
            f.text = d.getString("text") == null ? "" : d.getString("text");
            f.player = d.getString("player");
            f.dialogue = d.getString("dialogue");
            f.node = d.getString("node");
            f.resolution = d.getString("resolution");
            f.resolvedAt = d.getString("resolvedAt");
            Object cs = d.get("checks");
            if (cs instanceof List<?> list) {
                for (Object o : list) f.checks.add(String.valueOf(o));
            }
            return f;
        }

        public boolean outstanding() {
            return resolution == null;
        }

        /** One line for chat: the words first, because that is what anyone reading this wants. */
        public String line() {
            StringBuilder b = new StringBuilder(text);
            if (resolution != null) b.append("  -> ").append(resolution);
            if (!checks.isEmpty()) b.append("  [").append(String.join(", ", checks)).append("]");
            else if (node != null) b.append("  [at ").append(dialogue).append("/").append(node).append("]");
            return b.toString();
        }
    }

    private final Path file;
    private final String serverVersion;
    /** The LowTalk build running right now, stamped onto everything observed during this run. */
    private final String build;
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private final List<Feedback> feedback = new ArrayList<>();
    /**
     * Entries read from the file whose check no longer exists. Kept verbatim and written back out under
     * {@code retired}: a check removed from the list should stop being counted, but a record of a real run on a
     * real server is not ours to throw away because we renamed something.
     */
    private final Map<String, Document> retired = new LinkedHashMap<>();
    private boolean dirty;

    public RunRecord(@Nonnull Path folder, @Nonnull String serverVersion) {
        this(folder, serverVersion, "unknown");
    }

    public RunRecord(@Nonnull Path folder, @Nonnull String serverVersion, @Nonnull String build) {
        this.serverVersion = serverVersion;
        this.build = build;
        // the version is part of the name, so a run against another server cannot overwrite this one
        this.file = folder.resolve("run-" + serverVersion.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
        load();
    }

    public String serverVersion() {
        return serverVersion;
    }

    /** The LowTalk build under test. */
    public String build() {
        return build;
    }

    /**
     * Results carried over from a different build of LowTalk than the one running.
     *
     * <p>Honest evidence about a jar that no longer exists. Worth keeping — it is how a version's history
     * reads — and worth naming, because a release gate that counts it is counting the wrong thing.
     */
    public synchronized List<String> fromAnotherBuild(@Nonnull List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            Check c = checks.get(id);
            if (c == null || c.outcome() == null) continue;
            // An unstamped result is not a matching one: it was written before the stamp existed, which is
            // exactly the run this was built for — fifty-two results across five jars, indistinguishable.
            if (!build.equals(c.build)) out.add(id);
        }
        return out;
    }

    public Path file() {
        return file;
    }

    /** The check as it stands, created empty if this is the first time it has come up. */
    public synchronized Check check(@Nonnull String id) {
        return checks.computeIfAbsent(id, k -> new Check());
    }

    /** What a check counts as right now, silence included. Null when it has neither run nor been ruled on. */
    @Nullable
    public synchronized Verdict outcome(@Nonnull String id) {
        return check(id).outcome();
    }

    /**
     * Record that a check was exercised. The first time is the one that is timestamped, but every time
     * restamps the build: running it again on a new jar is what makes the evidence current again, and a
     * stamp that only ever recorded the first sighting would make that impossible to say.
     */
    public synchronized void markSeen(@Nonnull String id) {
        Check c = check(id);
        if (c.seen && build.equals(c.build)) return;
        if (!c.seen) {
            c.seen = true;
            c.firstSeen = Instant.now().toString();
        }
        c.build = build;
        dirty = true;
    }

    /** Record a verdict over the implied one, with an optional note about what was seen. */
    public synchronized void decide(@Nonnull String id, @Nonnull Verdict verdict, @Nullable String note) {
        Check c = check(id);
        c.verdict = verdict;
        c.note = note == null || note.isBlank() ? null : note.trim();
        c.decidedAt = Instant.now().toString();
        c.build = build;
        dirty = true;
    }

    /** Drop an explicit verdict, leaving whatever running the check implies. */
    public synchronized void undecide(@Nonnull String id) {
        Check c = checks.get(id);
        if (c == null || c.verdict == null) return;
        c.verdict = null;
        c.note = null;
        c.decidedAt = null;
        dirty = true;
    }

    /**
     * File a complaint in the tester's own words, against the checks it is taken to be about.
     *
     * <p>Those checks are failed by it. A pass is implied by nobody saying anything; the moment somebody says
     * something, the implication is gone and the check is a problem until it is looked at.
     */
    public synchronized Feedback addFeedback(@Nonnull String text, @Nullable String player,
                                             @Nullable String dialogue, @Nullable String node,
                                             @Nonnull List<String> about) {
        Feedback f = new Feedback();
        f.text = text.trim();
        f.player = player;
        f.dialogue = dialogue;
        f.node = node;
        f.checks.addAll(about);
        feedback.add(f);
        for (String id : about) decide(id, Verdict.FAIL, f.text);
        dirty = true;
        return f;
    }

    /** Everything anyone said was wrong on this version, oldest first. */
    public synchronized List<Feedback> feedback() {
        return new ArrayList<>(feedback);
    }

    /**
     * Say what was done about a piece of feedback, and stop it counting as outstanding.
     *
     * <p>Kept rather than deleted. What someone reported and what came of it is the most interesting thing in
     * the file — "rain was not working" and "the check asked for a cloudy sky" is a better record of an
     * evening than either half alone, and a release gate that counts outstanding reports needs a way to close
     * one honestly rather than by forgetting it.
     */
    public synchronized boolean resolve(int index, @Nonnull String resolution) {
        if (index < 0 || index >= feedback.size()) return false;
        Feedback f = feedback.get(index);
        f.resolution = resolution.trim();
        f.resolvedAt = Instant.now().toString();
        dirty = true;
        return true;
    }

    /** Everything reported and not yet answered. */
    public synchronized List<Feedback> outstandingFeedback() {
        List<Feedback> out = new ArrayList<>();
        for (Feedback f : feedback) if (f.outstanding()) out.add(f);
        return out;
    }

    /** Drop one feedback entry by its position in {@link #feedback()}, and unfail what it failed. */
    public synchronized boolean dropFeedback(int index) {
        if (index < 0 || index >= feedback.size()) return false;
        Feedback f = feedback.remove(index);
        for (String id : f.checks) {
            Check c = checks.get(id);
            // only lift the failure this entry caused, not one recorded some other way
            if (c != null && c.verdict == Verdict.FAIL && f.text.equals(c.note)) undecide(id);
        }
        dirty = true;
        return true;
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

    /** Ids read from the file that no longer name a check. Shown once at boot, then left alone. */
    public synchronized List<String> retiredIds() {
        return new ArrayList<>(retired.keySet());
    }

    /**
     * Counts over the given checks: {@code [seen, decided, passed, failed]}.
     *
     * <p>Decided and passed both include the implied pass, so "decided" means "has an answer" rather than
     * "somebody typed something". The gap worth watching is {@code ids.size() - decided}: checks nobody has
     * run at all, which no amount of silence can speak for.
     */
    public synchronized int[] tally(@Nonnull List<String> ids) {
        int seen = 0, decided = 0, passed = 0, failed = 0;
        for (String id : ids) {
            Check c = checks.get(id);
            if (c == null) continue;
            if (c.seen) seen++;
            Verdict v = c.outcome();
            if (v == null) continue;
            decided++;
            if (v == Verdict.PASS) passed++;
            if (v == Verdict.FAIL) failed++;
        }
        return new int[] {seen, decided, passed, failed};
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Document root = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
            Document cs = root.get("checks", Document.class);
            if (cs != null) {
                for (String id : cs.keySet()) {
                    Document d = cs.get(id, Document.class);
                    if (d == null) continue;
                    if (Checks.byId(id) == null) retired.put(id, d);
                    else checks.put(id, Check.fromDocument(d));
                }
            }
            Document old = root.get("retired", Document.class);
            if (old != null) {
                for (String id : old.keySet()) {
                    Document d = old.get(id, Document.class);
                    if (d != null) retired.putIfAbsent(id, d);
                }
            }
            Object fb = root.get("feedback");
            if (fb instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Document d) feedback.add(Feedback.fromDocument(d));
                }
            }
            if (!retired.isEmpty()) dirty = true;   // move them under 'retired' on the next write
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
        for (Map.Entry<String, Check> e : checks.entrySet()) {
            Check c = e.getValue();
            // Only checks with something to say. Reading a check creates it, and /harness todo reads them all,
            // so persisting every entry would fill the file with "seen: false" and make an untouched run look
            // like a tracked one.
            if (!c.seen && c.verdict == null) continue;
            cs.put(e.getKey(), c.toDocument());
        }
        Document root = new Document();
        root.put("serverVersion", serverVersion);
        root.put("build", build);
        root.put("updated", Instant.now().toString());
        root.put("checks", cs);
        if (!feedback.isEmpty()) {
            List<Document> fs = new ArrayList<>();
            for (Feedback f : feedback) fs.add(f.toDocument());
            root.put("feedback", fs);
        }
        if (!retired.isEmpty()) root.put("retired", new Document(new LinkedHashMap<String, Object>(retired)));
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
