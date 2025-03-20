package profiler;

import ch.usi.dag.disl.annotation.GuardMethod;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;

public class Guard{
    @GuardMethod
    public static boolean isThread(ClassStaticContext csc, MethodStaticContext mc) {
      if(mc.isMethodStatic() || mc.isMethodConstructor() || mc.isMethodPrivate() || mc.isMethodFinal()){
        return false;
      }
      return true;
    }

}
