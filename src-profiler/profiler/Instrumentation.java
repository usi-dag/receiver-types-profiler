package profiler;


import ch.usi.dag.disl.annotation.Before;

import java.nio.MappedByteBuffer;

import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import profiler.CustomContext;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.dynamiccontext.DynamicContext;
import ch.usi.dag.disl.annotation.ThreadLocal;


public class Instrumentation {
   @ThreadLocal
   static long callsite = -1; 

   // 'i' is initialized to the Profile.length to remove an if statement
   // from the instrumentation.
   @ThreadLocal
   static int i = 3*512*1024;

   @ThreadLocal
   static MappedByteBuffer mb;

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
     // NOTE: ClassStaticContext getName() and obj.getClass().getName() do not return the same result.
     // ClassStaticContext ofter returns the super type of an object, likely the declared type in the source code.
     // EX:
     //  obj.getClass().getName()
     //  jdk.internal.loader.ClassLoaders$AppClassLoader
     //  ClassStaticContext.getName()
     //  java.lang.ClassLoader
     // 
     
      if(callsite != -1){
        Object obj = dc.getThis();
        if(i == Profiler.length){
          // file is full
          mb = Profiler.getMemoryMappedFile();
          i = 0;
        }
        i = Profiler.putInfo(mb, i, callsite, obj);
      }
      // NOTE: Putting this assignment inside the if statement above breaks everything.
      callsite = -1;
   }

  @Before(marker=BodyMarker.class, scope="*", guard=GuardFinal.class)
   static void beforeEveryMethodFinal(DynamicContext dc, MethodStaticContext mc, ClassStaticContext csc){
     // NOTE: ClassStaticContext getName() and obj.getClass().getName() do not return the same result.
     // ClassStaticContext ofter returns the super type of an object, likely the declared type in the source code.
     // EX:
     //  obj.getClass().getName()
     //  jdk.internal.loader.ClassLoaders$AppClassLoader
     //  ClassStaticContext.getName()
     //  java.lang.ClassLoader
     // 
     
      if(callsite != -1){
        String name = csc.getName();
        System.out.println(mc.getUniqueInternalName());
        if(i == Profiler.length){
          // file is full
          mb = Profiler.getMemoryMappedFile();
          i = 0;
        }
        i = Profiler.putInfo(mb, i, callsite, name);
      }
      // NOTE: Putting this assignment inside the if statement above breaks everything.
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
