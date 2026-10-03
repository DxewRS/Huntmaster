package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Bounded data only: the server can extend known capture windows, never select client reads. */
final class CollectionPolicy
{
    private Map<String, Integer> windows = Collections.emptyMap();
    private long expiresAt;
    private boolean supported;
    private boolean updateRequired;
    private String revision="packaged-default";
    String revision(long now) { return now<expiresAt?revision:"packaged-default"; }
    boolean updateRequired() { return updateRequired; }
    void accept(JsonObject value, long now)
    {
        windows = Collections.emptyMap(); expiresAt = 0; supported = false;updateRequired=false;revision="packaged-default";
        try
        {
            if(value!=null && value.has("minimumVersion") && value.get("minimumVersion").getAsInt()>2){updateRequired=true;return;}
            if (value == null || !"2".equals(value.get("version").getAsString())) return;
            supported = true;
            long expiry = value.get("expiresAt").getAsLong();
            if (expiry <= now || expiry > now + 86_400_000L) return;
            JsonObject entries = value.getAsJsonObject("windows");
            if (entries == null || entries.size() > 64) return;
            Map<String, Integer> parsed = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : entries.entrySet())
            {
                if (!java.util.Arrays.asList(SupportedEncounters.NAMES).contains(entry.getKey())) return;
                String raw = entry.getValue().getAsString();
                if (!raw.matches("[0-9]{1,3}")) return;
                int ticks = Integer.parseInt(raw);
                if (ticks < 10 || ticks > 128) return;
                parsed.put(entry.getKey(), ticks);
            }
            windows = parsed; expiresAt = expiry;
            if(value.has("revision") && value.get("revision").getAsString().matches("[a-zA-Z0-9_.-]{1,40}"))revision=value.get("revision").getAsString();
        }
        catch (RuntimeException ignored) { windows = Collections.emptyMap(); }
    }
    boolean supported() { return supported; }
    Map<String, Integer> windows(long now) { return now < expiresAt ? windows : Collections.emptyMap(); }
}
