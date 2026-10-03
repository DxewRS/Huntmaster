package com.huntmaster;

import net.runelite.api.ChatMessageType;

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
        return GenericKcRouter.identifies(message, boss);
    }
}
