package com.huntmaster;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Immutable settings snapshot, invalidated by configuration/profile events and startup. */
final class RecruitmentSelections
{
    private Set<String> cached;

    synchronized void invalidate() { cached = null; }

    synchronized Set<String> get(Function<String, String> read)
    {
        if (cached == null)
        {
            Set<String> selected = new HashSet<>();
            for (RecruitmentActivity activity : RecruitmentActivity.values())
                if (Boolean.parseBoolean(read.apply(activity.configKey()))) selected.add(activity.id);
            cached = Collections.unmodifiableSet(selected);
        }
        return cached;
    }
}
