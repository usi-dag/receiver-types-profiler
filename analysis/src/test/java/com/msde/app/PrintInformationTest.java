package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PrintInformationTest {
  @Test
  public void testRatioChange() {
    RatioChange rc = new RatioChange(0, "SomeClass", 0, 1, 1);
    String[] info = rc.formatInformation();
    assertEquals(1, info.length);
    assertEquals("SomeClass - window before: 0 - window after: 1 - diff: 1.0 - before: 0.0 - after: 1.0\n", info[0]);
  }

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
  public void testInversion() {
    Inversion i = new Inversion(0, 1, "ClassA", "ClassB", 0.0, 1.0, 1.0, 0.0);
    String[] info = i.formatInformation();
    assertEquals(3, info.length);
    assertEquals("ClassA - ClassB - windows: 0 - 1\n", info[0]);
    assertEquals("First Window: ClassA - ClassB, 0.0 - 1.0\n", info[1]);
    assertEquals("Second Window: ClassA - ClassB, 1.0 - 0.0\n", info[2]);
  }

  @Test
  public void testSpicyInversion() {
    Map<String, Double> w1 = new HashMap<>();
    w1.put("ClassA", 0.0);
    w1.put("ClassB", 1.0);
    Map<String, Double> w2 = new HashMap<>();
    w2.put("ClassA", 1.0);
    w2.put("ClassB", 0.0);
    SpicyInversion i = new SpicyInversion(0, 1, w1, w2);
    String[] info = i.formatInformation();
    assertEquals(3, info.length);
    assertEquals("windows: 0 - 1\n", info[0]);
    assertEquals("    First Window: ClassA: 0.000000, ClassB: 1.000000\n", info[1]);
    assertEquals("    Second Window: ClassA: 1.000000, ClassB: 0.000000\n", info[2]);
  }

  @Test
  public void testSpicyRatioChange() {
    Map<String, Double> w1 = new HashMap<>();
    w1.put("ClassA", 0.0);
    w1.put("ClassB", 1.0);
    Map<String, Double> w2 = new HashMap<>();
    w2.put("ClassA", 1.0);
    w2.put("ClassB", 0.0);
    SpicyRatioChange c = new SpicyRatioChange(0, w1, w2);
    String[] info = c.formatInformation();
    assertEquals(3, info.length);
    assertEquals("window before: 0 - window after: 1\n", info[0]);
    assertEquals("    First Window: ClassA: 0.000000, ClassB: 1.000000\n", info[1]);
    assertEquals("    Second Window: ClassA: 1.000000, ClassB: 0.000000\n", info[2]);
  }
}
