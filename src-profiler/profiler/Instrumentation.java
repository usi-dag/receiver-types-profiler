package profiler;

import java.lang.annotation.Target;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.marker.BodyMarker;
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
   static boolean isVirtual = false;

    @Before(marker=BytecodeMarker.class, args="invokevirtual", scope="Main.*")
    static void beforeEveryInvokeVirtual(CustomContext cc){
      long id = cc.getTargetId();
      callsite = id;
      System.out.println("Check here: " +id);
      isVirtual = true;
      // System.out.println("Callsite is: " + callSite);
    }

   @Before(marker=BodyMarker.class, scope="*", guard=Guard.class)
   static void beforeEveryMethod(DynamicContext dc, MethodStaticContext mc){
      // System.out.println(mc.getUniqueInternalName());
      if(callsite == -1){
         return;
      }else{
         // System.out.println("IDK " + mc.getUniqueInternalName());
         Object obj = dc.getThis();
         if(isVirtual){
            // TODO: change to pass the obj class name
            Profiler.addVirtualCall(callsite, obj);
         }else{
            Profiler.addInterfaceCall(callsite, obj);
         }
         callsite = -1;
      }
   }

    @Before(marker=BytecodeMarker.class, args="invokeinterface", scope="Main.*")
    static void beforeInvokeInterface(CustomContext cc){
      long id = cc.getTargetId();
      callsite = id;
      isVirtual = false;
      // System.out.println("Callsite is: " + callSite);
    }


    // @Before(marker=BytecodeMarker.class, args="invokespecial", guard=Guard.class)
    // static void beforeSpecial(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
    //    System.out.println("Instrumented special call: " + isc.getIndex() + " " + mc.getUniqueInternalName()); 

    // }
}
