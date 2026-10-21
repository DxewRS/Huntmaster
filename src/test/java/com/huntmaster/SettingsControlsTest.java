package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsControlsTest
{
    @Test public void existingClientsKeepTheirOverlayBehaviorAndOptInActivities()
    {
        BosscapeSettings settings=new BosscapeSettings(){};
        assertTrue(settings.enableRecruitmentAlerts());
        assertEquals(HuntOverlayVisibility.AFTER_CREDITED_KILL,settings.huntOverlayVisibility());
        assertTrue(settings.showAssignmentProgressOverlay());
        assertFalse(settings.notifyTfa());
    }

    @Test public void activeHuntModeNeedsNoNewLocalCreditAndStopsOnDisconnectOrCompletion()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Player",1000);
        JsonObject response=new JsonObject();response.addProperty("success",true);response.addProperty("rsn","Player");
        response.addProperty("sequence",1);response.addProperty("status","solo");
        JsonObject task=new JsonObject();task.addProperty("id",UUID.randomUUID().toString());task.addProperty("boss","Vardorvis");
        task.addProperty("requiredKC",16);task.addProperty("progress",5);response.add("assignment",task);
        assertTrue(state.accept(response,1100,null,0));
        assertNull(state.overlay(1100,true));
        assertEquals("5/16 KC: Vardorvis",state.overlay(1100,true,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
        assertNull(state.overlay(1100,false,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
        state.connection("Huntmaster unavailable");assertNull(state.overlay(1200,true,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
        response.addProperty("sequence",2);task.addProperty("progress",16);state.accept(response,1300,null,0);
        assertNull(state.overlay(1300,true,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
        state.reset(null,1400);assertNull(state.overlay(1400,true,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
    }
}
