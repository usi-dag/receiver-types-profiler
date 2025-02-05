package profiler;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.staticcontext.BasicBlockStaticContext;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.staticcontext.InstructionStaticContext;

public class Instrumentation {

    @Before(marker=BytecodeMarker.class, args="invokevirtual", scope="Main.*")
    static void beforeEveryInvokeVirtual(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
      String callSite = isc.getIndex() + " " + mc.getUniqueInternalName();
      Object obj = dc.getStackValue(0, Object.class);
      Profiler.addVirtualCall(callSite, obj);
      
    }

    @Before(marker=BytecodeMarker.class, args="invokeinterface", scope="Main.*")
    static void beforeInvokeInterface(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
       System.out.println("Instrumented interface call: " + isc.getIndex() + " " + mc.getUniqueInternalName()); 
       String callSite = isc.getIndex() + " " + mc.getUniqueInternalName();
       Object obj = dc.getStackValue(0, Object.class);
       Profiler.addInterfaceCall(callSite, obj);

    }


    @Before(marker=BytecodeMarker.class, args="invokespecial", scope="Main.*")
    static void beforeSpecial(InstructionStaticContext isc, MethodStaticContext mc, DynamicContext dc){
       System.out.println("Instrumented special call: " + isc.getIndex() + " " + mc.getUniqueInternalName()); 

    }
}
