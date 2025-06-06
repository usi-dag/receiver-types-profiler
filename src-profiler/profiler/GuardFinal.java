package profiler;

import ch.usi.dag.disl.annotation.GuardMethod;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;

public class GuardFinal{
    @GuardMethod
    public static boolean isThread(ClassStaticContext csc, MethodStaticContext mc) {
      if(!csc.isFinal()){
        return false;
      }
      if(mc.isMethodStatic() || mc.isMethodConstructor() || mc.isMethodPrivate() || mc.isMethodInitializer()){
        return false;
      }
      return true;
    }

}
