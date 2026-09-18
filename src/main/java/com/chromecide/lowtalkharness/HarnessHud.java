package com.chromecide.lowtalkharness;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A panel in the corner saying what is left to test, and where.
 *
 * <p>A HUD rather than a page because the two are different systems: a player has one custom page at a time, so
 * a checklist page would close the dialogue it is meant to be about. HUDs live in their own keyed, z-ordered map
 * and sit over whatever else is on screen, which is the only way to watch progress while walking the corridor.
 *
 * <p>It shows nothing you could click. {@code CustomHud} carries drawing commands and no event bindings, and
 * there is no inbound HUD packet, so verdicts are typed with {@code /harness}. What this is for is the question
 * you have while standing in front of a station: what here has not been tried yet.
 *
 * <p>Contextual on purpose. The first version of {@code /harness todo} listed every outstanding check and filled
 * the chat window; the need is almost never the whole list, it is the station in front of you.
 */
public final class HarnessHud extends CustomUIHud {

    public static final String KEY = "LowTalkHarness";

    private final HarnessPlugin plugin;
    /** The dialogue the player is in, which decides what the panel is about. Null when they are not talking. */
    @Nullable private volatile String dialogueId;

    public HarnessHud(@Nonnull HarnessPlugin plugin, @Nonnull PlayerRef playerRef) {
        super(playerRef, KEY);
        this.plugin = plugin;
    }

    /** Point the panel at a dialogue, or at nothing. Redraws only when it actually changed. */
    public void setDialogue(@Nullable String id) {
        if (java.util.Objects.equals(dialogueId, id)) return;
        dialogueId = id;
        refresh();
    }

    /** Redraw with whatever the record now says. */
    public void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        build(cmd);
        update(true, cmd);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.appendInline(null, document());
    }

    /**
     * The panel source. Built as text the way the game builds its own HUDs ({@code SpectatingHud} does the same),
     * because a HUD is drawn once per change and has no controls to address by name.
     */
    private String document() {
        RunRecord record = plugin.record();
        List<Checks.Check> here = scope();
        int[] t = record.tally(here.stream().map(Checks.Check::id).toList());
        int outstanding = here.size() - t[1];

        StringBuilder rows = new StringBuilder();
        rows.append(label(title(here), 13, "#8fd0ff", true));
        rows.append(label(t[1] + "/" + here.size() + " decided"
                + (t[3] > 0 ? "   " + t[3] + " failed" : ""), 12, t[3] > 0 ? "#ff9d8a" : "#96a9be", false));

        // The checks still to do here, a few at a time: a panel that scrolls off the screen helps nobody.
        int shown = 0;
        for (Checks.Check c : here) {
            RunRecord.Check state = record.check(c.id());
            if (state.verdict != null) continue;
            if (shown == MAX_ROWS) {
                rows.append(label("...and " + (outstanding - shown) + " more", 11, "#6f8296", false));
                break;
            }
            // ASCII only. The bullet drew fine but the white bullet came out as "?", which reads as
            // something being wrong rather than as a check nobody has run yet.
            rows.append(label((state.seen ? "> judge  " : "  run    ") + c.id(),
                    11, state.seen ? "#ffd479" : "#96a9be", false));
            shown++;
        }
        if (outstanding == 0) rows.append(label("all decided here", 11, "#9fd18a", false));

        return "Group {\n"
                + "  Anchor: (Right: 16, Top: 80, Width: 230);\n"
                + "  LayoutMode: Top;\n"
                + "  Background: (Color: #0a0f17(0.72));\n"
                + "  Padding: (Left: 10, Right: 10, Top: 8, Bottom: 8);\n"
                + rows
                + "}\n";
    }

    private static final int MAX_ROWS = 6;

    /** One line of the panel. */
    private static String label(String text, int size, String colour, boolean bold) {
        return "  Label {\n"
                + "    Anchor: (Height: " + (size + 6) + ");\n"
                + "    Text: \"" + escape(text) + "\";\n"
                + "    Style: (FontSize: " + size + ", TextColor: " + colour
                + (bold ? ", RenderBold: true" : "") + ", VerticalAlignment: Center);\n"
                + "  }\n";
    }

    /** The UI source is a document, so a stray quote or newline in a check id would break the panel. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /** The checks this panel is about: the station being talked to, or everything when not in a dialogue. */
    private List<Checks.Check> scope() {
        String id = dialogueId;
        if (id == null) return Checks.all();
        List<Checks.Check> out = new ArrayList<>();
        for (Checks.Check c : Checks.all()) {
            if (c.autoSeen() != null && c.autoSeen().startsWith(id + "/")) out.add(c);
        }
        return out.isEmpty() ? Checks.all() : out;
    }

    private String title(List<Checks.Check> here) {
        String id = dialogueId;
        if (id == null || here.size() == Checks.all().size()) return "LowTalk harness  " + plugin.record().serverVersion();
        return id + "  " + plugin.record().serverVersion();
    }
}
