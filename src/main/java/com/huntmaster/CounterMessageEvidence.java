package com.huntmaster;

import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;
import java.util.Locale;

/** Allow game-generated counter notifications, never player conversations. */
final class CounterMessageEvidence
{
    private CounterMessageEvidence() { }
    static boolean accepts(ChatMessageType type)
    {
        return type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
            || type == ChatMessageType.FRIENDSCHATNOTIFICATION;
    }
    static boolean related(String message, String boss)
    {
        if (message == null || boss == null || message.length() > 1024) return false;
        String text = Text.removeTags(message).toLowerCase(Locale.ROOT);
        String name = boss.toLowerCase(Locale.ROOT);
        return text.startsWith("your ") && text.contains("count")
            && (text.contains(name) || name.startsWith("the ") && text.contains(name.substring(4)));
    }
}
