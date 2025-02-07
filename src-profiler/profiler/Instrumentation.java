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
   static String callSite = ""; 

   @ThreadLocal
   static boolean isVirtual = false;

    @Before(marker=BytecodeMarker.class, args="invokevirtual", scope="Main.*")
    static void beforeEveryInvokeVirtual(DynamicContext dc, InstructionStaticContext isc, MethodStaticContext mc, CustomContext cc){
      String cs = isc.getIndex() + " " + mc.getUniqueInternalName();
      callSite = cs;
      isVirtual = true;
      System.out.println("Callsite is: " + callSite);
    }

   @Before(marker=BodyMarker.class, scope="*")
   static void beforeEveryMethod(DynamicContext dc, MethodStaticContext mc){
      if(callSite == ""){
         return;
      }else{
         System.out.println("IDK " + mc.getUniqueInternalName());
         Object obj = dc.getThis();
         if(isVirtual){
            Profiler.addVirtualCall(callSite, obj);
         }else{
            Profiler.addInterfaceCall(callSite, obj);
         }
         callSite = "";
      }
   }

    @Before(marker=BytecodeMarker.class, args="invokeinterface", scope="Main.*")
    static void beforeInvokeInterface(InstructionStaticContext isc, MethodStaticContext mc, CustomContext cc){
      String cs = isc.getIndex() + " " + mc.getUniqueInternalName();
      callSite = cs;
      isVirtual = false;
      System.out.println("Callsite is: " + callSite);
    }


    // @Before(marker=BytecodeMarker.class, args="invokespecial", scope="Main.*")
    // static void beforeSpecial(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
    //    System.out.println("Instrumented special call: " + isc.getIndex() + " " + mc.getUniqueInternalName()); 

    // }
}
