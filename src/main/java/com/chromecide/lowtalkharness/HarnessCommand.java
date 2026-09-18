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
 * {@code /harness} — what is left to test on this server version, and what happened when it was.
 *
 * <p>Verdicts are typed rather than clicked because a HUD cannot take input: {@code CustomHud} carries drawing
 * commands and no event bindings, and there is no inbound HUD event packet. So the corner of the screen can show
 * the score, but saying "that looked wrong" has to come from somewhere else, and a command is the least
 * ceremonious somewhere.
 */
public class HarnessCommand extends AbstractCommandCollection {
    public static final String PERMISSION = "lowtalkharness.use";

    public HarnessCommand(@Nonnull HarnessPlugin plugin) {
        super("harness", "LowTalk test harness: what is tested on this server version");
        this.requirePermission(PERMISSION);
        this.addSubCommand(new Todo(plugin));
        this.addSubCommand(new Hud(plugin));
        this.addSubCommand(new Npc());
        this.addSubCommand(new Show(plugin));
        this.addSubCommand(new Pass(plugin));
        this.addSubCommand(new Fail(plugin));
        this.addSubCommand(new Skip(plugin));
    }

    /**
     * What is outstanding, as a few lines rather than a list.
     *
     * <p>The first version printed every undecided check and filled the chat window at fourteen of them; at the
     * seventy-odd the corridor is worth it would be unreadable. Chat is a poor place for a list, so this answers
     * "how much is left, and where" and takes a filter — a station number, a kind, or an id prefix — for the
     * one slice you are actually standing in front of.
     */
    static class Todo extends CommandBase {
        private final HarnessPlugin plugin;
        private final OptionalArg<String> filterArg =
                withOptionalArg("only", "A station number, a kind such as RESTART, or the start of an id", ArgTypes.STRING);

        Todo(HarnessPlugin plugin) {
            super("todo", "How much is left to test on this server version; add a station, kind or id to narrow it");
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

            List<Checks.Check> ready = new ArrayList<>();
            List<Checks.Check> untouched = new ArrayList<>();
            Map<String, int[]> byGroup = new LinkedHashMap<>();
            for (Checks.Check c : Checks.all()) {
                if (!matches(c, filter)) continue;
                RunRecord.Check state = record.check(c.id());
                if (state.verdict != null) continue;
                (state.seen ? ready : untouched).add(c);
                String group = c.station() != null ? "station " + c.station() : c.kind().name().toLowerCase(java.util.Locale.ROOT);
                int[] n = byGroup.computeIfAbsent(group, k -> new int[2]);
                if (state.seen) n[0]++; else n[1]++;
            }

            int[] t = record.tally(Checks.ids());
            out.accept("Harness on " + record.serverVersion() + ": " + t[1] + "/" + Checks.ids().size()
                    + " decided, " + t[2] + " pass, " + t[3] + " fail.");
            if (ready.isEmpty() && untouched.isEmpty()) {
                out.accept(narrowed ? "Nothing outstanding for '" + filter + "'." : "Everything is decided on this version.");
                return;
            }

            if (narrowed) {
                // asked about one slice, so name the checks in it
                for (Checks.Check c : ready) out.accept("  judge: " + c.id() + " - " + c.title());
                for (Checks.Check c : untouched) out.accept("  run:   " + c.id() + " - " + c.title());
                return;
            }

            out.accept("  " + ready.size() + " run but not judged, " + untouched.size() + " not yet run:");
            for (Map.Entry<String, int[]> e : byGroup.entrySet()) {
                out.accept("    " + e.getKey() + ": " + e.getValue()[0] + " to judge, " + e.getValue()[1] + " to run");
            }
            if (!ready.isEmpty()) {
                out.accept("  next: /harness pass|fail " + ready.get(0).id());
            }
            out.accept("  narrow it: /harness todo <station|kind|id>");
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
            out.accept("  here:   " + (state.seen ? "run" : "not run")
                    + (state.verdict == null ? ", no verdict" : ", " + state.verdict)
                    + (state.note == null ? "" : " - " + state.note));
        }
    }

    /** Shared by pass/fail/skip: the only difference is the verdict recorded. */
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
            if (!record.check(id).seen && verdict == RunRecord.Verdict.PASS) {
                // passing something that never ran is how a record stops meaning anything
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
}
