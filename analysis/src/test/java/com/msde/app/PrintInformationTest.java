package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PrintInformationTest {

  @Test
  public void testCompilation() {
    Compilation rc = new Compilation(0, "AAAA", "c1");
    String[] info = rc.formatInformation();
    assertEquals(1, info.length);
    assertEquals("Compilation at: 0 [kind = c1, id = AAAA]\n", info[0]);
    Compilation newComp = rc.withTime(1);
    info = newComp.formatInformation();
    assertEquals(1, info.length);
    assertEquals("Compilation at: 1 [kind = c1, id = AAAA]\n", info[0]);
  }

  @Test
  public void testDeCompilation() {
    Decompilation rc = new Decompilation(0, "AAAA", "c1", "b", "c");
    String[] info = rc.formatInformation();
    assertEquals(1, info.length);
    assertEquals("Decompilation at: 0[compile_id = AAAA, compile_kind = c1][trap: reason = b, action = c]\n", info[0]);
    Decompilation newDec = rc.withTime(1);
    info = newDec.formatInformation();
    assertEquals(1, info.length);
    assertEquals("Decompilation at: 1[compile_id = AAAA, compile_kind = c1][trap: reason = b, action = c]\n", info[0]);
    Decompilation dec = new Decompilation(0, "AAAA", "c1", null, null);
    info = dec.formatInformation();
    assertEquals(1, info.length);
    assertEquals("Decompilation at: 0[compile_id = AAAA, compile_kind = c1]\n", info[0]);
  }

  @Test
  public void testSpicyInversion() {
    Map<Long, String> idToName = new HashMap<>();
    idToName.put(0L, "ClassA");
    idToName.put(1L, "ClassB");
    Map<Long, Double> w1 = new HashMap<>();
    w1.put(0L, 0.0);
    w1.put(1L, 1.0);
    Map<Long, Double> w2 = new HashMap<>();
    w2.put(0L, 1.0);
    w2.put(1L, 0.0);
    Inversion i = new Inversion(0, 1, w1, w2, idToName);
    String[] info = i.formatInformation();
    assertEquals(3, info.length);
    assertEquals("windows: 0 - 1\n", info[0]);
    assertEquals("    First Window: ClassA: 0.000000, ClassB: 1.000000\n", info[1]);
    assertEquals("    Second Window: ClassA: 1.000000, ClassB: 0.000000\n", info[2]);
  }

  @Test
  public void testSpicyRatioChange() {
    Map<Long, String> idToName = new HashMap<>();
    idToName.put(0L, "ClassA");
    idToName.put(1L, "ClassB");
    Map<Long, Double> w1 = new HashMap<>();
    w1.put(0L, 0.0);
    w1.put(1L, 1.0);
    Map<Long, Double> w2 = new HashMap<>();
    w2.put(0L, 1.0);
    w2.put(1L, 0.0);
    RatioChange c = new RatioChange(0, w1, w2, idToName);
    String[] info = c.formatInformation();
    assertEquals(3, info.length);
    assertEquals("window before: 0 - window after: 1\n", info[0]);
    assertEquals("    First Window: ClassA: 0.000000, ClassB: 1.000000\n", info[1]);
    assertEquals("    Second Window: ClassA: 1.000000, ClassB: 0.000000\n", info[2]);
  }
}
