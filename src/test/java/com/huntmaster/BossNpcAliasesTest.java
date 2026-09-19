package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class BossNpcAliasesTest
{
    @Test public void supportingIdentitiesStayWithinTheirAssignedEncounter()
    {
        assertTrue(BossNpcAliases.matches("Grotesque Guardians", "Dusk"));
        assertTrue(BossNpcAliases.matches("Grotesque Guardians", "Dawn"));
        assertTrue(BossNpcAliases.matches("Royal Titans", "Branda the Fire Queen"));
        assertTrue(BossNpcAliases.matches("Royal Titans", "Eldric the Ice King"));
        assertFalse(BossNpcAliases.matches("Royal Titans", "Dusk"));
        assertFalse(BossNpcAliases.matches("Grotesque Guardians", "Gargoyle"));
        assertFalse(BossNpcAliases.matches("The Nightmare", "Phosani's Nightmare"));
        assertFalse(BossNpcAliases.matches("Barrows Brothers", "Dharok the Wretched"));
        assertFalse(BossNpcAliases.matches("Moons of Peril", "Blood Moon"));
        assertTrue(GenericKcRouter.parse("Your Grotesque Guardians kill count is: 42.", "Grotesque Guardians") == 42);
        assertNull(GenericKcRouter.parse("Your Dusk kill count is: 42.", "Grotesque Guardians"));
    }
}
