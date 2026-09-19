package com.huntmaster;

import java.util.Locale;
import java.util.regex.*;
import net.runelite.client.util.Text;

/** Conservative counterpart to RuneLite Chat Commands' shared personal-counter parser. */
final class GenericKcRouter
{
    private static final Pattern COUNTER = Pattern.compile(
        "^Your (?:completion count for |subdued |completed )?(.+?) (?:kill |harvest |completion |success )?(?:count )?is: ?([0-9][0-9,]*)(?:\\.|$|\\s)", Pattern.CASE_INSENSITIVE);
    static boolean eligible(String assignedBoss)
    {
        if (assignedBoss == null || assignedBoss.trim().isEmpty()) return false;
        String name = assignedBoss.toLowerCase(Locale.ROOT);
        return !name.contains("chambers of xeric") && !name.contains("theatre of blood")
            && !name.contains("tombs of amascut");
    }
    static Integer parse(String message, String assignedBoss)
    {
        if (!eligible(assignedBoss) || message == null || message.length() > 1024) return null;
        Matcher match = COUNTER.matcher(Text.removeTags(message));
        if (!match.find() || !BossCounterAliases.matches(assignedBoss, match.group(1).trim())) return null;
        try { return Integer.valueOf(match.group(2).replace(",", "")); }
        catch (NumberFormatException ex) { return null; }
    }
}
