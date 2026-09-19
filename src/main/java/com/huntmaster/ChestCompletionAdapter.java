package com.huntmaster;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;

/** Reward-interface identity, not individual boss deaths. */
final class ChestCompletionAdapter
{
    static int rewardInventory(String boss, int interfaceId)
    {
        if ("Barrows Brothers".equalsIgnoreCase(boss) && interfaceId == InterfaceID.BARROWS_REWARD)
            return InventoryID.TRAIL_REWARDINV;
        if ("Moons of Peril".equalsIgnoreCase(boss) && interfaceId == InterfaceID.PMOON_REWARD)
            return InventoryID.PMOON_REWARDINV;
        return -1;
    }
}
