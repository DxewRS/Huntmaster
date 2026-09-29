package com.huntmaster;
import org.junit.Test;
import static org.junit.Assert.*;
public class VerificationReliabilityStateTest
{
 @Test public void onlyRestoredBotDecisionsChangePauseState(){
  VerificationReliabilityState state=new VerificationReliabilityState();assertFalse(state.isPaused());
  state.restore(2);assertFalse(state.isPaused());state.restore(3);assertTrue(state.isPaused());
  state.restore(0);assertFalse(state.isPaused());state.restore(-1);assertEquals(0,state.getFailures());
 }
}
