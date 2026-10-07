package com.huntmaster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;

/** Parsed game notification, not a credit decision. Never retains message text. */
final class DoomCompletionEvidence
{
    static final String BOSS = "Doom of Mokhaiotl";
    static final String DETECTOR_VERSION = "doom-completion-observation-v1";
    // The game displays 9+ as "8+ (actual level)". Do not mistake the 8 for the level.
    private static final String TIME = "(?:[0-9]{1,3}:)?[0-9]{1,2}:[0-5][0-9](?:\\.[0-9]{1,2})?";
    private static final Pattern MESSAGE = Pattern.compile(
        "^Delve level: ([1-8]|8\\+ \\(([1-9][0-9]{0,9})\\)) duration: "
        + TIME + "(?: \\(new personal best\\)|\\. Personal best: " + TIME + ")?\\.?$",
        Pattern.CASE_INSENSITIVE);

    final int completedLevel;
    final boolean extended;
    final String source;
    final int tickOffset;

    private DoomCompletionEvidence(int level, boolean extended, String source, int tickOffset)
    {
        this.completedLevel = level;
        this.extended = extended;
        this.source = source;
        this.tickOffset = tickOffset;
    }

    static DoomCompletionEvidence parse(ChatMessageType type, String message)
    {
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM) return null;
        if (message == null || message.length() > 512) return null;
        Matcher match = MESSAGE.matcher(Text.removeTags(message));
        if (!match.matches()) return null;
        try
        {
            boolean extended = match.group(2) != null;
            int level = Integer.parseInt(extended ? match.group(2) : match.group(1));
            if (extended && level < 9) return null;
            return new DoomCompletionEvidence(level, extended, type.name(), 0);
        }
        catch (NumberFormatException ignored) { return null; }
    }

    DoomCompletionEvidence at(int offset)
    {
        return new DoomCompletionEvidence(completedLevel, extended, source, offset);
    }
}
