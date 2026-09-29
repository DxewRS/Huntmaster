package com.huntmaster;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Client-thread state. Never persists a cursor: reconnects start at the live edge. */
final class RecruitmentNotifications
{
    static final class Poll
    {
        final long generation;
        final long startedAt;
        final String rsn;
        final String cursor;
        Poll(long generation, long startedAt, String rsn, String cursor)
        { this.generation = generation; this.startedAt = startedAt; this.rsn = rsn; this.cursor = cursor; }
    }
    private long generation;
    private String rsn;
    private String cursor;
    private long lastSuccess;
    private Poll pending;
    private Set<String> selected = Collections.emptySet();
    private final LinkedHashSet<String> seen = new LinkedHashSet<>();

    void reset()
    {
        generation++;
        rsn = null;
        cursor = null;
        pending = null;
        lastSuccess = 0;
        selected = Collections.emptySet();
        seen.clear();
    }

    Poll begin(String account, Set<String> choices, long now)
    {
        if (account == null || choices.isEmpty()) { reset(); return null; }
        if (!account.equals(rsn) || !choices.equals(selected) || lastSuccess != 0 && now - lastSuccess > 15000
            || pending != null && now - pending.startedAt > 10000) reset();
        rsn = account;
        selected = new HashSet<>(choices);
        if (pending != null) return null;
        pending = new Poll(generation, now, rsn, cursor);
        return pending;
    }

    List<String> complete(Poll poll, JsonObject body, long now)
    {
        if (poll != pending || poll.generation != generation) return Collections.emptyList();
        pending = null;
        if (body == null || now - poll.startedAt > 10000) { reset(); return Collections.emptyList(); }
        try
        {
            if (!body.get("success").getAsBoolean()) throw new IllegalArgumentException();
            String next = body.get("cursor").getAsString();
            if (!next.matches("[0-9]{1,16}:[0-9]{1,16}") || body.getAsJsonArray("events").size() > 100)
                throw new IllegalArgumentException();
            List<String> messages = new ArrayList<>();
            if (poll.cursor != null)
            {
                for (JsonElement item : body.getAsJsonArray("events"))
                {
                    JsonObject event = item.getAsJsonObject();
                    String id = event.get("id").getAsString();
                    if (!id.matches("[0-9]{1,16}") || seen.contains(id)) continue;
                    Set<String> names = new LinkedHashSet<>();
                    for (JsonElement activityId : event.getAsJsonArray("activities"))
                    {
                        RecruitmentActivity activity = RecruitmentActivity.fromId(activityId.getAsString());
                        if (activity != null && selected.contains(activity.id)) names.add(activity.displayName);
                    }
                    seen.add(id);
                    while (seen.size() > 256) seen.remove(seen.iterator().next());
                    if (!names.isEmpty()) messages.add("Huntmaster: Someone is looking for members for "
                        + String.join(", ", names) + ". Join through Bosscape Discord.");
                }
            }
            cursor = next;
            lastSuccess = now;
            return messages;
        }
        catch (RuntimeException ignored) { reset(); return Collections.emptyList(); }
    }
}
