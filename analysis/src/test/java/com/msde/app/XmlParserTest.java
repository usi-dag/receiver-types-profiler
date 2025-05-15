package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

public class XmlParserTest {
  @Test
  public void testConstructor() {
    File cf = new File("/home/ubuntu/receiver-types-profiler/analysis/compiler_log.xml");
    XmlParser xp = new XmlParser(cf);
    assertEquals(xp.getVmStartTime(), 1747217577473L);
  }

  @Test
  public void testCompilations() {
    File cf = new File("/home/ubuntu/receiver-types-profiler/analysis/compiler_log.xml");
    XmlParser xp = new XmlParser(cf);
    List<Compilation> compilations = xp.findCompilationStamps("Main getEl (I)LM1;");
    assertEquals(4, compilations.size());
    assertEquals("Compilation[time=2058, id=536, kind=c1]", compilations.get(0).toString());
    assertEquals("Compilation[time=2247, id=630, kind=c2]", compilations.get(1).toString());
    assertEquals("Compilation[time=2943, id=705, kind=c1]", compilations.get(2).toString());
    assertEquals("Compilation[time=2985, id=709, kind=c2]", compilations.get(3).toString());
  }

  @Test
  public void testDeCompilations() {
    File cf = new File("/home/ubuntu/receiver-types-profiler/analysis/compiler_log.xml");
    XmlParser xp = new XmlParser(cf);
    List<Decompilation> decompilations = xp.findDecompilationStamps("Main getEl (I)LM1;");
    assertEquals(3, decompilations.size());
    assertEquals("Decompilation[time=2247, id=536, kind=null, reason=null, action=null]",
        decompilations.get(0).toString());
    assertEquals("Decompilation[time=2942, id=630, kind=null, reason=unstable_if, action=reinterpret]",
        decompilations.get(1).toString());
    assertEquals("Decompilation[time=2985, id=705, kind=null, reason=null, action=null]",
        decompilations.get(2).toString());
  }
}
