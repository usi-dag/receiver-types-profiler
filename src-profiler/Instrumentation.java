// package ex1;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.marker.BodyMarker;

public class Instrumentation {

    @Before(marker = BodyMarker.class)
    static void beforeEveryMethod() {
      System.err.println("Instrumentation: A new method has been executed.");
    }

}
