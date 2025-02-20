package profiler;


import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.annotation.After;
import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.marker.BasicBlockMarker;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;
import profiler.CustomContext;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.staticcontext.BasicBlockStaticContext;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.staticcontext.InstructionStaticContext;
import ch.usi.dag.disl.annotation.ThreadLocal;


public class Instrumentation {
   @ThreadLocal
   static long callsite = -1; 


   @ThreadLocal
   static long addr = 0;

   @ThreadLocal
   static int i = 0;

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
      Object obj = dc.getThis();
      if(addr == 0){
         // 8 is the size of a long
         addr = Profiler.getNewAddress();
      }
      if(i == 3*512*1024){
        // buffer is full flush it
        // System.out.println("buffer is full");
        // System.out.println(mc.getUniqueInternalName());
        Profiler.saveBufferInformation(addr, i);
        i = 0;
      }
      Profiler.putBytes(addr, i, callsite, obj);
      i+=3;
      // Profiler.addVirtualCall(callsite, obj);
      callsite = -1;
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
