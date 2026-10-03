package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssignmentDashboardStateTest
{
    private final String id=UUID.randomUUID().toString();
    private JsonObject response(String assignment,int progress,long sequence)
    {
        JsonObject body=new JsonObject();body.addProperty("success",true);body.addProperty("rsn","Example");body.addProperty("sequence",sequence);body.addProperty("status",assignment==null?"no_assignment":"solo");
        if(assignment==null)body.add("assignment",com.google.gson.JsonNull.INSTANCE);
        else {JsonObject a=new JsonObject();a.addProperty("id",assignment);a.addProperty("boss","Royal Titans");a.addProperty("progress",progress);a.addProperty("requiredKC",21);a.addProperty("rewardPoints",169);a.addProperty("classification","mass");body.add("assignment",a);}return body;
    }
    @Test public void serverStateDrivesSessionOverlayAndTimeout()
    {
        AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);
        s.accept(response(id,6,1),1100,null,0);assertNull(s.overlay(1100,true));
        s.accept(response(id,7,2),1200,id,1150);assertEquals("7/21 KC: Royal Titans",s.overlay(1200,true));
        assertNull(s.overlay(1200,false));
        assertNull(s.overlay(1200+AssignmentDashboardState.INACTIVITY_MS,true));
        s.accept(response(id,8,3),1300000,id,1299999);assertEquals("8/21 KC: Royal Titans",s.overlay(1300000,true));
        s.accept(response(id,8,4),1305000,null,0);
        assertNull(s.overlay(1300000+AssignmentDashboardState.INACTIVITY_MS,true));
        s.accept(response(null,0,5),2500001,null,0);assertNull(s.overlay(2500001,true));
        String other=UUID.randomUUID().toString();s.accept(response(other,0,6),2500002,null,0);assertNull(s.overlay(2500002,true));
        s.reset("Example",2600000);s.accept(response(other,1,7),2600001,other,2599999);assertNull(s.overlay(2600001,true));
    }
    @Test public void replacementsStaleResponsesAndOtherPlayersCannotChangeCredit()
    {
        AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);
        s.accept(response(id,7,5),1100,null,0);
        assertFalse(s.accept(response(id,6,4),1200,id,1050));assertEquals("7/21 KC: Royal Titans",s.overlay(1200,true));
        JsonObject wrong=response(id,9,6);wrong.addProperty("rsn","Other");assertFalse(s.accept(wrong,1300,id,1200));assertEquals(7,s.view().assignment.progress);
        String replacement=UUID.randomUUID().toString();s.accept(response(replacement,0,7),1400,id,1300);assertNull(s.overlay(1400,true));
        s.connection("Huntmaster unavailable");assertFalse(s.view().connected);
        s.reset(null,1500);assertNull(s.view().assignment);assertNull(s.overlay(1500,true));
    }
    @Test public void otherGroupMembersAndUnsupportedOldFieldsDoNotCreateLocalCredit()
    {
        AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);
        JsonObject r=response(id,6,1);r.addProperty("status","group");r.getAsJsonObject("assignment").remove("rewardPoints");
        assertTrue(s.accept(r,1100,null,0));assertNull(s.view().assignment.reward);
        r=response(id,7,2);r.addProperty("status","group");s.accept(r,1200,null,0);assertNull(s.overlay(1200,true));
        s.accept(response(id,21,3),1300,id,1250);assertNull(s.overlay(1300,true));
    }
    @Test public void panelResourcesAndActivationAreValid() throws Exception
    {
        assertNotNull(HuntmasterPlugin.class.getResource("panel_icon.png"));
        javax.swing.SwingUtilities.invokeAndWait(()->{
            HuntmasterPanel p=new HuntmasterPanel();AssignmentDashboardState s=new AssignmentDashboardState();
            p.update(s.view());p.onActivate();p.onDeactivate();s.reset("Example",1000);s.accept(response(id,7,1),1100,null,0);p.update(s.view());p.onActivate();p.onDeactivate();
            assertTrue(p.getComponentCount()>5);
            java.util.List<String> buttons=new java.util.ArrayList<>();
            for(java.awt.Component child:p.getComponents()) if(child instanceof javax.swing.JButton)buttons.add(((javax.swing.JButton)child).getText());
            assertEquals(java.util.Arrays.asList("Open Huntmaster in Discord","Copy diagnostics"),buttons);
        });
    }
    @Test public void repeatedAcknowledgementDoesNotRestartOverlayTimer()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Example",1000);
        state.accept(response(id,7,1),1200,id,1100);
        long expired=1200+AssignmentDashboardState.INACTIVITY_MS;
        state.accept(response(id,7,2),expired,id,1100);
        assertNull(state.overlay(expired,true));
        state.accept(response(id,7,1),expired+1,id,1100);
        assertNull(state.overlay(expired+1,true));
        state.accept(response(id,8,3),expired+2,id,expired+1);
        assertEquals("8/21 KC: Royal Titans",state.overlay(expired+2,true));
    }

    private JsonObject completion(long sequence)
    {
        JsonObject done=response(null,0,sequence),card=response(id,21,sequence).getAsJsonObject("assignment");
        card.addProperty("rewardPoints",15);card.addProperty("classification","solo");done.add("completedAssignment",card);return done;
    }
    @Test public void confirmedCompletionRetainsActualCardButNeverOverlay() throws Exception
    {
        AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);
        s.accept(response(id,20,1),1100,id,1050);assertNotNull(s.overlay(1100,true));
        s.accept(completion(2),1200,id,1150);
        assertNull(s.view().assignment);assertNull(s.overlay(1200,true));
        assertEquals("Royal Titans",s.view().completed.boss);assertEquals("solo",s.view().completed.classification);
        assertEquals(21,s.view().completed.progress);assertEquals(21,s.view().completed.required);assertEquals(Long.valueOf(15),s.view().completed.reward);
        s.accept(completion(3),1300,null,0);assertNotNull(s.view().completed);
        javax.swing.SwingUtilities.invokeAndWait(()->{
            HuntmasterPanel panel=new HuntmasterPanel();panel.update(s.view());panel.onActivate();
            String text=HuntmasterPanelTest.visibleText(panel);
            assertTrue(text.contains("TASK COMPLETED"));assertTrue(text.contains("You do not have a current Huntmaster assignment."));assertTrue(text.contains("15 pts"));
        });
        String next=UUID.randomUUID().toString();s.accept(response(next,0,4),1400,null,0);assertNull(s.view().completed);assertNull(s.overlay(1400,true));
        s.accept(response(next,1,5),1500,next,1450);assertNotNull(s.overlay(1500,true));
    }
    @Test public void removalCancellationAndReplacementAreNotCompletion()
    {
        for(boolean oldHistory:new boolean[]{false,true}){
            AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);s.accept(response(id,20,1),1100,null,0);
            JsonObject removed=oldHistory?completion(2):response(null,0,2);
            if(oldHistory)removed.getAsJsonObject("completedAssignment").addProperty("id",UUID.randomUUID().toString());
            s.accept(removed,1200,null,0);assertNull(s.view().completed);
        }
        AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);s.accept(response(id,20,1),1100,null,0);
        s.accept(response(UUID.randomUUID().toString(),0,2),1200,null,0);assertNull(s.view().completed);
    }
    @Test public void lifecycleClearsCompletionAndOldHistoryCannotRestoreIt()
    {
        for(String account:new String[]{null,"Other","Example"}){
            AssignmentDashboardState s=new AssignmentDashboardState();s.reset("Example",1000);s.accept(response(id,20,1),1100,null,0);s.accept(completion(2),1200,null,0);
            s.reset(account,1300);assertNull(s.view().completed);s.accept(completion(3),1400,null,0);assertNull(s.view().completed);
        }
        AssignmentDashboardState restarted=new AssignmentDashboardState();restarted.reset("Example",1500);restarted.accept(completion(4),1600,null,0);assertNull(restarted.view().completed);
        AssignmentDashboardState hop=new AssignmentDashboardState();hop.reset("Example",1000);hop.accept(response(id,20,1),1100,null,0);hop.accept(completion(2),1200,null,0);
        hop.clearCompleted();hop.accept(completion(3),1300,null,0);assertNull(hop.view().completed);
        hop.accept(response(id,20,4),1400,null,0);hop.clearCompleted();assertNotNull(hop.view().assignment);
        hop.accept(completion(5),1500,null,0);assertNull(hop.view().completed);
    }
}
