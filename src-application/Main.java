

public class Main{
  public static void main(String[] args) {
    // warmup
    for(int i =0; i< 1000; i++){
      // m();
    }

    // method call
    for(int i = 0; i < 1; i++){
      m();
    }
  }
   public static void m(){
    var a = new A();
    a.m2(0, 0);
    a.m();
    A.staticMethod();
    // if(true){return;}
    var b = new B();
    var c = new C();
    var d = new D();
    TestInterface in = new B();

    final var MAX_ITERATION = 100;
    for(int i = 0; i < MAX_ITERATION; i++){
      if(i<30){
        a.m();
      } else if(i<40){
        b.m();
      } else if(i<60){
        c.m();
      } else {
        d.m();
      }

      if(i%20 ==0){
        in.im();
      }
      if(i%20 == 19){
        a = new C();
      }
    }
     
   }
}

/**
 * TestInterface
 */
interface TestInterface {
  default void sdm(){
    System.out.println("Interface");
  }

  void im();
  
}

class A {
  public void m(){
    System.out.println("I am A");
  }

  public void m2(int a, long b){
    System.out.println("aaaa");
  }

  public static void staticMethod(){
    System.out.println("STATIC");
  }
}

class B extends A implements TestInterface {
  @Override
  public void im(){
    System.out.println("Hello from interface method");
  }
  
}

class C extends A{

}

class D{
  public void m(){
    System.out.println("I am D");
  }
}
