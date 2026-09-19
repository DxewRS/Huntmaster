package com.huntmaster;

import java.util.*;

/** Small diagnostic history. Contains no chat, location, items, or other-player information. */
final class EncounterSignalBuffer
{
    static final int HISTORY_TICKS = 16;
    static final int MAX_EVENTS = 64;
    static final class Event
    {
        final int tick;
        final long time;
        final EncounterObservation.SignalKind kind;
        Event(int tick, long time, EncounterObservation.SignalKind kind)
        { this.tick = tick; this.time = time; this.kind = kind; }
    }
    private final List<Event> events = new ArrayList<>();
    private String context;
    void add(String key, int tick, long time, EncounterObservation.SignalKind kind)
    {
        prune(key, tick);
        if (!events.isEmpty())
        {
            Event last = events.get(events.size()-1);
            if (last.tick == tick && last.kind == kind) return;
        }
        if (events.size() == MAX_EVENTS) events.remove(0);
        events.add(new Event(tick, time, kind));
    }
    List<Event> consume(String key, int tick)
    {
        prune(key, tick);
        List<Event> result = new ArrayList<>(events);
        events.clear();
        return result;
    }
    private void prune(String key, int tick)
    {
        if (!Objects.equals(context, key) || (!events.isEmpty() && events.get(events.size()-1).tick > tick)) clear();
        context = key;
        events.removeIf(event -> tick-event.tick > HISTORY_TICKS);
    }
    void clear() { events.clear(); context = null; }
}
