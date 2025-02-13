package profiler;

import ch.usi.dag.disl.annotation.GuardMethod;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;

public class Guard{
    @GuardMethod
    public static boolean isThread(ClassStaticContext csc, MethodStaticContext mc) {
      if(mc.isMethodStatic()){
        return false;
      }
      if(csc.getInternalName().contains("kafka")){
        // System.out.println(csc.getInternalName());
      }
      // return csc.getInternalName().contains("kafka");
      return true;
    }

}
