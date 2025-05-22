package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AppTest {
  @Test
  public void testExtractMethodDescriptor() {
    String callsite = "30 java/io/PrintStream.write([BII)V implWrite([BII)V";
    String compilerLogMethodDescriptor = "java.io.PrintStream write ([BII)V";
    assertEquals(compilerLogMethodDescriptor, App.extractMethodDescriptor(callsite));
  }

  @Test
  public void testCompareRanking() {
    String[] k1 = new String[] { "A", "B", "C", "D" };
    String[] k2 = new String[] { "A", "B", "C", "D" };
    assertTrue(App.compareRanking(k1, k2));
    String[] k3 = new String[] { "A", "B", "D", "C" };
    assertFalse(App.compareRanking(k1, k3));

    /**
     * w1 = [A - 0.4 B - 0.4 C - 0.1 D - 0.1]
     * w1 = [A - 0.4 B - 0.4 C - 0.0 D - 0.2]
     **/
    String[] k4 = new String[] { "A", "B", "D" };
    assertFalse(App.compareRanking(k1, k4));

    /**
     * w1 = [A - 0.4 B - 0.4 C - 0.1 D - 0.1]
     * w1 = [A - 0.5 B - 0.5 C - 0.0 D - 0.0]
     **/
    String[] k5 = new String[] { "A", "B"};
    assertTrue(App.compareRanking(k1, k5));
  }

  @Test
  public void testCompareRanking2() {
    /**
     * w1 = [A - 0.6 B - 0.4]
     * w1 = [A - 0.4 B - 0.6]
     **/
    String[] k5 = new String[] { "A", "B" };
    String[] k6 = new String[] { "B", "A" };
    assertFalse(App.compareRanking(k5, k6));

    /**
     * w1 = [A - 0.6 B - 0.4]
     * w1 = [A - 1.0 B - 0.0]
     **/
    String[] k7 = new String[] { "A", "B" };
    String[] k8 = new String[] { "A" };
    assertTrue(App.compareRanking(k7, k8));

    /**
     * w1 = [A - 0.6 B - 0.4]
     * w1 = [A - 0.0 B - 1.0]
     **/
    String[] k9 = new String[] { "A", "B" };
    String[] k10 = new String[] { "B" };
    assertFalse(App.compareRanking(k9, k10));
  }
}
