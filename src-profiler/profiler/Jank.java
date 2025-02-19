package profiler;

import ch.usi.dag.disl.annotation.GuardMethod;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;

public class Jank{
    @GuardMethod
    public static boolean isThread(ClassStaticContext csc, MethodStaticContext mc) {
      if(!mc.isMethodStatic()){
        return false;
      }
      // System.out.println(":"+mc.thisMethodName()+":");
      // if(mc.thisMethodName().contains("main")){
      // System.out.println(":"+mc.thisMethodName()+":");
      //   System.out.println("ALPACA" + mc.thisMethodName() instanceof String);
      //   System.out.println("ALPACA" + mc.thisMethodName().equals("main"));
      // }
      return mc.thisMethodName().equals("main");
    }

}
