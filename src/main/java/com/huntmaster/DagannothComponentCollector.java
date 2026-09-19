package com.huntmaster;

import java.util.Objects;
import java.util.function.Consumer;

/** Separate counter state and bounded signal windows for each king; diagnostic only. */
final class DagannothComponentCollector
{
    private static final String[] NAMES = {"Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"};
    private static final String[] KEYS = {"rex", "prime", "supreme"};
    private final BossDetector[] detectors = new BossDetector[3];
    private final EncounterCapture[] captures = new EncounterCapture[3];
    private String scope;
    private int lastTick = -1;

    DagannothComponentCollector(Consumer<EncounterObservation.Snapshot> sink)
    {
        for (int i = 0; i < 3; i++)
        {
            detectors[i] = new BossDetector(BossDefinition.dagannothComponent(KEYS[i]));
            captures[i] = new EncounterCapture(sink);
        }
    }
    private void prepare(String rsn, String assignment, int tick)
    {
        String next = rsn + ":" + assignment;
        if (!Objects.equals(next, scope)) interrupt(tick, EncounterObservation.Reason.ASSIGNMENT_CHANGED);
        advance(tick);
        scope = next;
    }
    boolean counter(String message, String rsn, String assignment, int tick, long now)
    {
        for (int i = 0; i < 3; i++)
        {
            Integer total = GenericKcRouter.parse(message, NAMES[i]);
            if (total == null) continue;
            prepare(rsn, assignment, tick);
            Integer previous = detectors[i].getLastKc();
            captures[i].counter(detectors[i], rsn, assignment, tick, now,
                EncounterObservation.CounterSource.KC_MESSAGE, previous, total);
            if (previous != null && (long) total - previous != 1)
                captures[i].uncertain(EncounterObservation.Outcome.AMBIGUOUS);
            detectors[i].setLastKc(total);
            return true;
        }
        return false;
    }
    boolean npc(String name, boolean loot, String rsn, String assignment, int tick, long now)
    {
        for (int i = 0; i < 3; i++)
        {
            if (!NAMES[i].equalsIgnoreCase(name)) continue;
            prepare(rsn, assignment, tick);
            if (loot) captures[i].loot(detectors[i], rsn, assignment, tick, now);
            else captures[i].primary(detectors[i], rsn, assignment, tick, now, EncounterObservation.SignalKind.DEATH);
            return true;
        }
        return false;
    }
    void advance(int tick)
    {
        if (lastTick > tick) interrupt(tick, EncounterObservation.Reason.CLOCK_RESET);
        lastTick = tick;
        for (EncounterCapture capture : captures) capture.advance(tick);
    }
    void interrupt(int tick, EncounterObservation.Reason reason)
    {
        for (int i = 0; i < 3; i++)
        {
            captures[i].interrupt(tick, reason);
            detectors[i].setLastKc(null);
        }
        scope = null;
        lastTick = -1;
    }
    void clear()
    {
        for (int i = 0; i < 3; i++) { captures[i].clear(); detectors[i].setLastKc(null); }
        scope = null;
        lastTick = -1;
    }
}
