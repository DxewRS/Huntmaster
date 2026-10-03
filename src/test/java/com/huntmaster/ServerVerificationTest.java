package com.huntmaster;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServerVerificationTest
{
 private JsonObject response(String assignment,int failures,String notice){JsonObject r=new JsonObject();r.addProperty("success",true);r.addProperty("assignmentId",assignment);r.addProperty("failures",failures);r.addProperty("paused",failures>=3);r.addProperty("noticeId",notice);r.addProperty("message",notice==null?null:"Huntmaster: Check your kill.");return r;}
 @Test public void onlyBotDecisionsPauseAndNoticesAreDeduplicated(){
  ServerVerification state=new ServerVerification();String assignment=UUID.randomUUID().toString();
  ServerVerification.Poll poll=state.begin("Player",assignment,0);assertEquals(0,state.failures());
  assertTrue(state.complete(poll,response(assignment,3,"one"),assignment));assertEquals(3,state.failures());assertNotNull(state.takeNotice());assertNull(state.takeNotice());
  poll=state.begin("Player",assignment,10000);assertTrue(state.complete(poll,response(assignment,3,"one"),assignment));assertNull(state.takeNotice());
  poll=state.begin("Player",assignment,20000);assertTrue(state.complete(poll,response(assignment,0,null),assignment));assertEquals(0,state.failures());
 }
 @Test public void staleCallbacksAndMalformedResponsesCannotChangeState(){
  ServerVerification state=new ServerVerification();ServerVerification.Poll old=state.begin("Player","old",0);state.reset();
  ServerVerification.Poll current=state.begin("Other","new",1);assertFalse(state.isCurrent(old));assertTrue(state.isCurrent(current));assertFalse(state.complete(old,response("old",3,"old"),"new"));
  assertFalse(state.complete(current,response("wrong",3,"x"),"new"));assertEquals(0,state.failures());
  current=state.begin("Other","new",10001);JsonObject invalid=response("new",1,"x");invalid.addProperty("paused",true);assertFalse(state.complete(current,invalid,"new"));assertEquals(0,state.failures());
 }
 @Test public void malformedCountsAndChatControlsAreRejected(){
  ServerVerification state=new ServerVerification();
  ServerVerification.Poll poll=state.begin("Player","task",0);
  JsonObject invalid=response("task",0,"x");invalid.addProperty("failures",0.5);
  assertFalse(state.complete(poll,invalid,"task"));assertEquals(0,state.failures());
  poll=state.begin("Player","task",10000);invalid=response("task",3,"x");invalid.addProperty("message","one\nforged line");
  assertFalse(state.complete(poll,invalid,"task"));assertEquals(0,state.failures());assertNull(state.takeNotice());
 }
 @Test public void observationPayloadContainsSignalsAndNoLocalVerdict(){
  EncounterObservation r=new EncounterObservation(UUID.randomUUID(),UUID.randomUUID(),"Player","Phosani's Nightmare",BossDetectorType.STANDARD_NPC,"v1",1000000,100,20);
  r.recordPrimary(EncounterObservation.SignalKind.DEATH,100);r.recordCounter(110,EncounterObservation.CounterSource.KC_MESSAGE,114,115);
  r.closeIfExpired(120);
  JsonObject body=EncounterReportCodec.encodeObservation(r.snapshot());assertEquals("server_observation",body.get("trackingMode").getAsString());assertEquals("unresolved",body.get("outcome").getAsString());assertFalse(body.has("creditEventId"));assertFalse(body.has("verificationMethod"));assertEquals(2,body.getAsJsonArray("signals").size());
 }
}
