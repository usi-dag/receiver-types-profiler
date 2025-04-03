package profiler;


import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.annotation.After;

import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;

import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;
import profiler.CustomContext;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.marker.BasicBlockMarker;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.annotation.ThreadLocal;


public class Instrumentation {
   @ThreadLocal
   static long callsite = -1; 

   // 'i' is initialized to the Profile.length to remove an if statement
   // from the instrumentation.
   @ThreadLocal
   static int i = 3*512*1024 + 1;

   @ThreadLocal
   static MappedByteBuffer mb;

   @ThreadLocal
   static long id = 0;

   @ThreadLocal
   static Map<String, Long> ctoi;

   @ThreadLocal
   static long cid = 0;
   

   @Before(marker=BytecodeMarker.class, args="invokevirtual", scope="*")
   static void beforeEveryInvokeVirtual(CustomContext cc, MethodStaticContext mc){
   long id = cc.getTargetId();
   callsite = id;
   // System.out.println("Check here: " +id);
   // isVirtual = true;
   // System.out.println("VIRTUAL: "+mc.getUniqueInternalName());
   // System.out.println("Callsite is: " + callsite);
   }

   @Before(marker=BodyMarker.class, scope="*", guard=Guard.class)
   static void beforeEveryMethod(DynamicContext dc, MethodStaticContext mc){
      if(id == 0){
        id = Profiler.getId();
        ctoi = new HashMap<>();
      }
      Object obj = dc.getThis();
      if(i == Profiler.length + 1){
        // file is full
        mb = Profiler.getMemoryMappedFile();
        mb.putLong(id);
        i = 1;
      }
      cid = Profiler.jank(ctoi, obj, cid);
      i = Profiler.putInfo(mb, i, callsite, obj, ctoi);
      callsite = -1;
   }

    @After(marker = BasicBlockMarker.class,
  	   scope="void run()")
  	   //guard=IsThreadGuard.class)
      static void onThreadExit(DynamicContext dc) {
        if (dc.getThis() instanceof Thread) {  
  	    Profiler.registerThreadExit(ctoi);
      }
    }

    @Before(marker=BytecodeMarker.class, args="invokeinterface", scope="*")
    static void beforeInvokeInterface(CustomContext cc, MethodStaticContext mc){
      long id = cc.getTargetId();
      callsite = id;
      // isVirtual = false;
      // System.out.println("INTERFACE: " + mc.getUniqueInternalName());
      // System.out.println("Callsite is: " + callSite);
    }


    // @Before(marker=BytecodeMarker.class, args="invokespecial", guard=Guard.class)
    // static void beforeSpecial(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
    //    System.out.println("SPECIAL " + isc.getIndex() + " " + mc.getUniqueInternalName()); 
    // }

    // @Before(marker=BytecodeMarker.class, args="invokedynamic", guard=Guard.class)
    // static void beforeDynamic(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
    //    System.out.println("DYMAMIC " + isc.getIndex() + " " + mc.getUniqueInternalName()); 
    // }

    // @Before(marker=BytecodeMarker.class, args="invokestatic", guard=Guard.class)
    // static void beforeStatic(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
    //    System.out.println("STATIC " + isc.getIndex() + " " + mc.getUniqueInternalName()); 
    // }

}
