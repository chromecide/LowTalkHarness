package com.chromecide.lowtalkharness;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code /harness} — what is left to test on this server version, and what went wrong when it was tested.
 *
 * <p>Typed rather than clicked because a HUD cannot take input: {@code CustomHud} carries drawing commands and
 * no event bindings, and there is no inbound HUD event packet. So the corner of the screen can show the score,
 * but saying "that looked wrong" has to come from somewhere else, and a command is the least ceremonious
 * somewhere.
 *
 * <p>The load is deliberately one-sided. Running a check is free — walking the tester's tree records it — and
 * a check that ran and drew no comment is a pass. The only thing anyone has to type is {@code /harness feedback}
 * followed by what went wrong, in whatever words come to mind. That is the one keystroke in the whole harness
 * that carries information, so it is the one the design spends its budget on.
 */
public class HarnessCommand extends AbstractCommandCollection {
    public static final String PERMISSION = "lowtalkharness.use";

    public HarnessCommand(@Nonnull HarnessPlugin plugin) {
        super("harness", "LowTalk test harness: what is tested on this server version");
        this.requirePermission(PERMISSION);
        this.addSubCommand(new Todo(plugin));
        this.addSubCommand(new Hud(plugin));
        this.addSubCommand(new Npc());
        this.addSubCommand(new Reset(plugin));
        this.addSubCommand(new Show(plugin));
        this.addSubCommand(new Pass(plugin));
        this.addSubCommand(new Fail(plugin));
        this.addSubCommand(new Skip(plugin));
        this.addSubCommand(new FeedbackCommand(plugin));
        this.addSubCommand(new Undo(plugin));
        this.addSubCommand(new Report(plugin));
        this.addSubCommand(new Names());
    }

    /**
     * What is outstanding, as a few lines rather than a list.
     *
     * <p>The first version printed every undecided check and filled the chat window at fourteen of them; at the
     * seventy-odd the corridor is worth it would be unreadable. Chat is a poor place for a list, so this answers
     * "how much is left, and where" and takes a filter — a station number, a kind, or an id prefix — for the
     * one slice you are actually standing in front of.
     *
     * <p>Outstanding now means one of two things: never run, or run and complained about. A check that ran
     * quietly is finished, so it is not here. That change alone took the list on pre.3 from forty-odd lines to
     * the twenty-one nobody has walked yet, which is the number that was always the point.
     */
    static class Todo extends CommandBase {
        private final HarnessPlugin plugin;
        private final OptionalArg<String> filterArg =
                withOptionalArg("only", "A station number, a kind such as RESTART, or the start of an id", ArgTypes.STRING);

        Todo(HarnessPlugin plugin) {
            super("todo", "What is left to run on this server version; add a station, kind or id to narrow it");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        /** True when a check belongs to the slice the player asked about. */
        private static boolean matches(Checks.Check c, String filter) {
            String f = filter.trim().toLowerCase(java.util.Locale.ROOT);
            if (f.isEmpty()) return true;
            if (c.station() != null && f.equals(String.valueOf(c.station()))) return true;
            if (c.kind().name().toLowerCase(java.util.Locale.ROOT).equals(f)) return true;
            return c.id().toLowerCase(java.util.Locale.ROOT).startsWith(f);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            RunRecord record = plugin.record();
            boolean narrowed = filterArg.provided(context);
            String filter = narrowed ? filterArg.get(context) : "";

            List<Checks.Check> failed = new ArrayList<>();
            List<Checks.Check> untouched = new ArrayList<>();
            Map<String, Integer> byGroup = new LinkedHashMap<>();
            for (Checks.Check c : Checks.all()) {
                if (!matches(c, filter)) continue;
                RunRecord.Check state = record.check(c.id());
                if (state.verdict == RunRecord.Verdict.FAIL) {
                    failed.add(c);
                    continue;
                }
                if (state.outcome() != null) continue;      // ran quietly, or skipped: nothing left to do
                untouched.add(c);
                String group = c.station() != null ? "station " + c.station() : c.kind().name().toLowerCase(java.util.Locale.ROOT);
                byGroup.merge(group, 1, Integer::sum);
            }

            int[] t = record.tally(Checks.ids());
            int left = Checks.ids().size() - t[1];
            out.accept("Harness on " + record.serverVersion() + ": " + t[2] + " pass, " + t[3] + " fail, "
                    + left + " of " + Checks.ids().size() + " not run.");
            if (failed.isEmpty() && untouched.isEmpty()) {
                out.accept(narrowed ? "Nothing outstanding for '" + filter + "'." : "Everything here has been run and nothing was reported.");
                return;
            }

            for (Checks.Check c : failed) {
                RunRecord.Check state = record.check(c.id());
                out.accept("  FAIL  " + c.id() + (state.note == null ? "" : " - " + state.note));
            }

            if (narrowed) {
                // asked about one slice, so name the checks in it
                for (Checks.Check c : untouched) out.accept("  run   " + c.id() + " - " + c.title());
                return;
            }

            if (!untouched.isEmpty()) {
                out.accept("  " + untouched.size() + " still to run:");
                for (Map.Entry<String, Integer> e : byGroup.entrySet()) {
                    out.accept("    " + e.getKey() + ": " + e.getValue());
                }
                out.accept("  narrow it: /harness todo <station|kind|id>");
            }
            int notes = record.feedback().size();
            if (notes > 0) out.accept("  " + notes + " reported: /harness report");
        }
    }

    /** The corner panel, on or off. A player command because a HUD belongs to a player, not the console. */
    static class Hud extends com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand {
        private final HarnessPlugin plugin;

        Hud(HarnessPlugin plugin) {
            super("hud", "Show or hide the corner panel of what is left to test");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                               @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entity,
                               @Nonnull com.hypixel.hytale.server.core.universe.PlayerRef player,
                               @Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
            boolean on = plugin.toggleHud(player, entity);
            context.sendMessage(Message.raw(on ? "Harness panel on." : "Harness panel off."));
        }
    }

    /** Put the tester NPC in front of you. The tree that runs the checks is bound to it. */
    static class Npc extends com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand {
        Npc() {
            super("npc", "Spawn the harness tester NPC where you are standing");
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                               @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entity,
                               @Nonnull com.hypixel.hytale.server.core.universe.PlayerRef player,
                               @Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
            var transform = store.getComponent(entity,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) {
                context.sendMessage(Message.raw("Could not work out where you are standing."));
                return;
            }
            HarnessNpc.spawnFacing(store, transform.getPosition(), line -> context.sendMessage(Message.raw(line)));
        }
    }

    /** One check in full: what to do, what should happen, and how it went here. */
    static class Show extends CommandBase {
        private final HarnessPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("check", "Check id", ArgTypes.STRING);

        Show(HarnessPlugin plugin) {
            super("show", "What a check asks for, and how it went on this version");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            Checks.Check c = Checks.byId(idArg.get(context));
            if (c == null) {
                out.accept("No check called '" + idArg.get(context) + "'. /harness todo lists them.");
                return;
            }
            RunRecord.Check state = plugin.record().check(c.id());
            out.accept(c.id() + " [" + c.kind() + "] " + c.title());
            for (int i = 0; i < c.steps().size(); i++) out.accept("  " + (i + 1) + ". " + c.steps().get(i));
            out.accept("  expect: " + c.expected());
            out.accept("  covers: " + String.join(", ", c.covers()));
            RunRecord.Verdict outcome = state.outcome();
            out.accept("  here:   " + (outcome == null ? "not run on " + plugin.record().serverVersion()
                    : outcome + (state.impliedPass() ? " (ran, nothing reported)" : "")
                            + (state.note == null ? "" : " - " + state.note)));
        }
    }

    /**
     * Forget a check, so the tester offers it again.
     *
     * <p>The tree hides an option once its check has run, which is what makes walking it possible — but it also
     * means a check cannot be repeated to look at something twice. This puts one back.
     */
    static class Reset extends CommandBase {
        private final HarnessPlugin plugin;
        private final RequiredArg<String> idArg = withRequiredArg("check", "Check id", ArgTypes.STRING);

        Reset(HarnessPlugin plugin) {
            super("reset", "Forget a check on this version so it can be run again");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            if (Checks.byId(id) == null) {
                context.sendMessage(Message.raw("No check called '" + id + "'. /harness todo lists them."));
                return;
            }
            boolean had = plugin.record().clear(id);
            plugin.record().flush();
            plugin.refreshHuds();
            context.sendMessage(Message.raw(had
                    ? id + " forgotten on " + plugin.record().serverVersion() + "; the tester will offer it again."
                    : id + " had nothing recorded on this version."));
        }
    }

    /**
     * Shared by pass/fail/skip: the only difference is the verdict recorded.
     *
     * <p>These are the override, not the normal path. Running a check is what records a pass; {@code pass} here
     * is for taking one back — clearing a failure after looking again, or after a complaint turned out to be
     * about something else.
     */
    abstract static class Decide extends CommandBase {
        private final HarnessPlugin plugin;
        private final RunRecord.Verdict verdict;
        private final RequiredArg<String> idArg = withRequiredArg("check", "Check id", ArgTypes.STRING);
        private final OptionalArg<String> noteArg = withOptionalArg("note", "What you saw", ArgTypes.GREEDY_STRING);

        Decide(String name, String description, HarnessPlugin plugin, RunRecord.Verdict verdict) {
            super(name, description);
            this.plugin = plugin;
            this.verdict = verdict;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            if (Checks.byId(id) == null) {
                context.sendMessage(Message.raw("No check called '" + id + "'. /harness todo lists them."));
                return;
            }
            RunRecord record = plugin.record();
            // Passing something that never ran is how a record stops meaning anything — but only where a run
            // would have been noticed. A check with nothing to watch can never be marked seen, so refusing to
            // pass it would leave the hand-walked ones (the editor, block binding, anything out in the world)
            // able to fail and never able to pass, which is the opposite of what the rule is for.
            if (!record.check(id).seen && verdict == RunRecord.Verdict.PASS && Checks.isWatched(id)) {
                context.sendMessage(Message.raw("'" + id + "' has not been run on this server version yet. "
                        + "Run it first, or /harness skip " + id + " if it does not apply here."));
                return;
            }
            record.decide(id, verdict, noteArg.provided(context) ? noteArg.get(context) : null);
            record.flush();
            plugin.refreshHuds();
            context.sendMessage(Message.raw(id + ": " + verdict + " recorded on " + record.serverVersion() + "."));
        }
    }

    static class Pass extends Decide {
        Pass(HarnessPlugin p) { super("pass", "Record that a check looked right on this version", p, RunRecord.Verdict.PASS); }
    }

    static class Fail extends Decide {
        Fail(HarnessPlugin p) { super("fail", "Record that a check did not look right, with a note", p, RunRecord.Verdict.FAIL); }
    }

    static class Skip extends Decide {
        Skip(HarnessPlugin p) { super("skip", "Record that a check does not apply on this version", p, RunRecord.Verdict.SKIP); }
    }

    /**
     * {@code /harness feedback <what went wrong>} — the only thing a tester has to type.
     *
     * <p>Free text, no id, no syntax. The complaint is recorded against wherever the player last was, which is
     * almost always what it is about: you watch the wrong title draw, close the page, and say so. That fails
     * the check, because a pass is what silence means and this is not silence.
     *
     * <p>Starting the text with a check id aims it somewhere else — {@code /harness feedback title.goblin the
     * banner never drew} — for when you have walked on before writing it down. Feedback that matches no check
     * at all is kept anyway, unattached, and read out with the rest at the end: a complaint the harness cannot
     * file is still a complaint, and losing it because it did not fit the model would be the whole mistake.
     */
    static class FeedbackCommand extends com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand {
        private final HarnessPlugin plugin;
        private final RequiredArg<String> textArg =
                withRequiredArg("what", "What went wrong, in your own words", ArgTypes.GREEDY_STRING);

        FeedbackCommand(HarnessPlugin plugin) {
            super("feedback", "Say what went wrong; it fails the check you are on and is kept for the end");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                               @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entity,
                               @Nonnull com.hypixel.hytale.server.core.universe.PlayerRef player,
                               @Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            String text = textArg.get(context).trim();
            if (text.isEmpty()) {
                out.accept("Say what went wrong: /harness feedback the goblin title never drew");
                return;
            }

            // an id in front aims it by hand; otherwise it lands on wherever they last were
            List<String> about = new ArrayList<>();
            String dialogue = null, node = null;
            int space = text.indexOf(' ');
            String head = space < 0 ? text : text.substring(0, space);
            Checks.Check named = Checks.byId(head);
            if (named != null && space > 0) {
                about.add(named.id());
                text = text.substring(space + 1).trim();
            } else {
                HarnessPlugin.Where where = plugin.whereIs(player.getUuid());
                if (where != null) {
                    dialogue = where.dialogueId();
                    node = where.node();
                    for (Checks.Check c : Checks.forPassage(dialogue, node)) about.add(c.id());
                }
            }

            RunRecord record = plugin.record();
            RunRecord.Feedback f = record.addFeedback(text, player.getUuid().toString(), dialogue, node, about);
            record.flush();
            plugin.refreshHuds();

            if (about.isEmpty()) {
                out.accept("Noted, with nothing to pin it on: \"" + f.text + "\"");
                out.accept("  It is kept for the end. /harness feedback <check id> <what happened> aims one at a check.");
            } else {
                out.accept("Noted against " + String.join(", ", about) + ": \"" + f.text + "\"");
            }
            out.accept("  " + record.feedback().size() + " so far. /harness report reads them back, /harness undo takes this one off.");
        }
    }

    /** Take the last piece of feedback back, for the one typed into the wrong window or about the wrong thing. */
    static class Undo extends CommandBase {
        private final HarnessPlugin plugin;

        Undo(HarnessPlugin plugin) {
            super("undo", "Remove the most recent piece of feedback, and the failure it caused");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            RunRecord record = plugin.record();
            List<RunRecord.Feedback> all = record.feedback();
            if (all.isEmpty()) {
                context.sendMessage(Message.raw("Nothing has been reported on " + record.serverVersion() + " yet."));
                return;
            }
            RunRecord.Feedback last = all.get(all.size() - 1);
            record.dropFeedback(all.size() - 1);
            record.flush();
            plugin.refreshHuds();
            context.sendMessage(Message.raw("Removed: \"" + last.text + "\""
                    + (last.checks.isEmpty() ? "" : " (" + String.join(", ", last.checks) + " back to how they ran)")));
        }
    }

    /**
     * The end of a run, read out: what passed, what did not, and everything anyone said about it.
     *
     * <p>This is what the feedback is accumulated for. The record on disk is the durable version, but a run
     * ends with someone standing in a test world wanting to know whether it went well, and that answer should
     * not require leaving the game to read a JSON file.
     */
    static class Report extends CommandBase {
        private final HarnessPlugin plugin;

        Report(HarnessPlugin plugin) {
            super("report", "Everything reported on this server version, with the pass and fail counts");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            RunRecord record = plugin.record();
            List<String> ids = Checks.ids();
            int[] t = record.tally(ids);
            int skipped = 0;
            for (String id : ids) if (record.check(id).verdict == RunRecord.Verdict.SKIP) skipped++;

            out.accept("LowTalk harness, " + record.serverVersion() + ":");
            out.accept("  " + t[2] + " pass, " + t[3] + " fail, " + skipped + " skipped, "
                    + (ids.size() - t[1]) + " never run, of " + ids.size() + ".");

            for (String id : ids) {
                RunRecord.Check c = record.check(id);
                if (c.verdict != RunRecord.Verdict.FAIL) continue;
                out.accept("  FAIL  " + id + (c.note == null ? "" : " - " + c.note));
            }

            List<RunRecord.Feedback> notes = record.feedback();
            if (notes.isEmpty()) {
                out.accept("  Nothing reported.");
            } else {
                out.accept("  Reported (" + notes.size() + "):");
                for (int i = 0; i < notes.size(); i++) out.accept("   " + (i + 1) + ". " + notes.get(i).line());
            }
            out.accept("  Full record: " + record.file());
        }
    }

    /**
     * {@code /harness names} — what the NPCs around you are called, in all three places a name is kept.
     *
     * <p>For the one question a person standing in front of an NPC cannot answer: after a restart put its old
     * name back, was the new name never saved, or saved and not re-applied? Run it before a bounce and after
     * one and the difference says which, and therefore whether the fix belongs in LowTalk or in a workaround
     * for the game's load path.
     */
    static class Names extends com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand {
        private final OptionalArg<Integer> radiusArg =
                withOptionalArg("radius", "How far to look, in blocks (default 8)", ArgTypes.INTEGER);

        Names() {
            super("names", "What the NPCs near you are called: nameplate, persisted and live");
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                               @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entity,
                               @Nonnull com.hypixel.hytale.server.core.universe.PlayerRef player,
                               @Nonnull com.hypixel.hytale.server.core.universe.world.World world) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            var transform = store.getComponent(entity,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform == null) {
                out.accept("Could not work out where you are standing.");
                return;
            }
            double radius = radiusArg.provided(context) ? radiusArg.get(context) : 8;
            var found = NameProbe.near(store, transform.getPosition(), radius);
            if (found.isEmpty()) {
                out.accept("No NPCs within " + (int) radius + " blocks.");
                return;
            }
            out.accept(found.size() + " NPC(s) within " + (int) radius + " blocks:");
            // the log as well as the chat: this is evidence, and chat scrolls away
            for (NameProbe.Found f : found) {
                out.accept("  " + f.line());
                HarnessPlugin.get().getLogger().at(java.util.logging.Level.INFO).log("[harness] name: %s", f.line());
            }
        }
    }
}
