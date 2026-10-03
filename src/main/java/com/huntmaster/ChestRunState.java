package com.huntmaster;

import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/** Short-lived raw game-state snapshot. The server decides whether it qualifies. */
final class ChestRunState
{
 private static final int[] BARROWS = {VarbitID.BARROWS_KILLED_AHRIM, VarbitID.BARROWS_KILLED_DHAROK,
  VarbitID.BARROWS_KILLED_GUTHAN, VarbitID.BARROWS_KILLED_KARIL, VarbitID.BARROWS_KILLED_TORAG, VarbitID.BARROWS_KILLED_VERAC};
 private static final int[] MOONS = {VarbitID.PMOON_BOSS_BLOOD_DEAD, VarbitID.PMOON_BOSS_BLUE_DEAD, VarbitID.PMOON_BOSS_ECLIPSE_DEAD};
 private String context;
 private int mask;
 private int tick = -1;
 static int read(String boss, IntUnaryOperator read)
 {
  int[] ids;
  if ("Barrows Brothers".equals(boss)) ids = BARROWS;
  else if ("Moons of Peril".equals(boss)) ids = MOONS;
  else return -1;
  int result = 0;
  for (int i = 0; i < ids.length; i++) if (read.applyAsInt(ids[i]) > 0) result |= 1 << i;
  return result;
 }
 void sample(String key, int current, int now) { context = key; mask = current; tick = now; }
 int[] consume(String key, int current, int now)
 {
  int age = now - tick;
  int[] result = current >= 0 ? new int[] {current, 0} : null;
  // Allow the immediately preceding game tick when chest collection has just reset the flags.
  if (current == 0 && key.equals(context) && mask > 0 && age >= 0 && age <= 1) result = new int[] {mask, age};
  clear();
  return result;
 }
 void clear() { context = null; mask = 0; tick = -1; }
}
