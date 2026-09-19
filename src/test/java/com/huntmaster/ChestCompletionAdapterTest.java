package com.huntmaster;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChestCompletionAdapterTest
{
    @Test public void rewardInterfacesCannotCrossAssignments()
    {
        assertEquals(InventoryID.TRAIL_REWARDINV,ChestCompletionAdapter.rewardInventory("Barrows Brothers",InterfaceID.BARROWS_REWARD));
        assertEquals(InventoryID.PMOON_REWARDINV,ChestCompletionAdapter.rewardInventory("Moons of Peril",InterfaceID.PMOON_REWARD));
        assertEquals(-1,ChestCompletionAdapter.rewardInventory("Moons of Peril",InterfaceID.BARROWS_REWARD));
        assertEquals(-1,ChestCompletionAdapter.rewardInventory("Barrows Brothers",InterfaceID.PMOON_REWARD));
        assertEquals(-1,ChestCompletionAdapter.rewardInventory("Scurrius",InterfaceID.BARROWS_REWARD));
    }
}
