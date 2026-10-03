package com.huntmaster;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChestRunStateTest {
 @Test public void readsAllFlagsAndPartialMasks() {
  assertEquals(63,ChestRunState.read("Barrows Brothers",id->1));
  assertEquals(7,ChestRunState.read("Moons of Peril",id->1));
  assertEquals(0,ChestRunState.read("Moons of Peril",id->0));
  assertEquals(-1,ChestRunState.read("Vorkath",id->1));
 }
 @Test public void resetSnapshotIsRecentScopedAndSingleUse() {
  ChestRunState state=new ChestRunState();state.sample("a",63,10);
  assertArrayEquals(new int[]{63,1},state.consume("a",0,11));
  assertArrayEquals(new int[]{0,0},state.consume("a",0,11));
  state.sample("a",63,10);assertArrayEquals(new int[]{0,0},state.consume("a",0,12));
  state.sample("a",63,10);assertArrayEquals(new int[]{0,0},state.consume("b",0,11));
  state.sample("a",63,10);assertArrayEquals(new int[]{3,0},state.consume("a",3,11));
 }
 @Test public void logoutClearsCacheAndFreshLoginUsesGameFlags() {
  ChestRunState state=new ChestRunState();state.sample("a",7,10);state.clear();
  assertArrayEquals(new int[]{0,0},state.consume("a",0,11));
  assertArrayEquals(new int[]{7,0},state.consume("a",7,500));
 }
}
