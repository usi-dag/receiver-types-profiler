package profiler;

import ch.usi.dag.disl.annotation.GuardMethod;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;

public class Guard{
    @GuardMethod
    public static boolean isThread(ClassStaticContext csc) {
      if(csc.getInternalName().contains("kafka")){
        // System.out.println(csc.getInternalName());
      }
      return csc.getInternalName().contains("dummy");
    }

}
