package extractor;

import ch.usi.dag.disl.annotation.Before;

import java.nio.MappedByteBuffer;

import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.annotation.ThreadLocal;


public class Instrumentation {

   @Before(marker=BodyMarker.class, scope="*", guard=CustomGuard.class)
   static void beforeEveryMethod(DynamicContext dc, MethodStaticContext mc){
      Object o = dc.getThis();
      // System.out.println(mc.thisMethodDescriptor());
      Profiler.putInfo(o, mc.thisMethodDescriptor());
   }
}
