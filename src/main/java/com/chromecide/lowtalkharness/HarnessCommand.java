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
import java.util.List;
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
        this.addSubCommand(new Show(plugin));
        this.addSubCommand(new Pass(plugin));
        this.addSubCommand(new Fail(plugin));
        this.addSubCommand(new Skip(plugin));
    }

    /** Everything not yet decided on this version, the exercised ones first: those are ready to judge. */
    static class Todo extends CommandBase {
        private final HarnessPlugin plugin;

        Todo(HarnessPlugin plugin) {
            super("todo", "What still needs testing or judging on this server version");
            this.plugin = plugin;
            this.requirePermission(PERMISSION);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            Consumer<String> out = line -> context.sendMessage(Message.raw(line));
            RunRecord record = plugin.record();
            List<String> ready = new ArrayList<>();
            List<String> untouched = new ArrayList<>();
            for (Checks.Check c : Checks.all()) {
                RunRecord.Check state = record.check(c.id());
                if (state.verdict != null) continue;
                (state.seen ? ready : untouched).add(
                        "  " + c.id() + "  [" + c.kind() + (c.station() == null ? "" : " s" + c.station()) + "]  " + c.title());
            }
            int[] t = record.tally(Checks.ids());
            out.accept("Harness on " + record.serverVersion() + ": " + t[1] + " of " + Checks.ids().size()
                    + " decided (" + t[2] + " pass, " + t[3] + " fail).");
            if (!ready.isEmpty()) {
                out.accept("Run but not judged - say /harness pass|fail <id>:");
                ready.forEach(out);
            }
            if (!untouched.isEmpty()) {
                out.accept("Not yet run:");
                untouched.forEach(out);
            }
            if (ready.isEmpty() && untouched.isEmpty()) out.accept("Everything is decided on this version.");
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
