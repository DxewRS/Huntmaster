package com.huntmaster;

/** Session/assignment-scoped sampled total. Initial reads are baselines, never credit. */
final class AssignedTotalTracker
{
    private String scope;
    private Integer previous;
    private int lastTick = -1;
    Integer changed(String nextScope, int total, int tick)
    {
        if (!nextScope.equals(scope) || tick < lastTick) { scope = nextScope; previous = null; }
        lastTick = tick;
        Integer old = previous;
        previous = total;
        return old != null && old != total ? old : null;
    }
    void clear() { scope = null; previous = null; lastTick = -1; }
}
