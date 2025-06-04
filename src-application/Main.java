
public class Main {
  public static void main(String[] args) {
    Main.induceMismatch();
  }

  public static void induceMismatch() {
    for (int i = 0; i < 200000; i++) {
      M1 el = getEl(i);
      int res = el.computeSomeStuff(100);
      System.out.println(res);
    }
  }

  public static M1 getEl(int i) {
    // this should apply before compilation
    if (i < 100000) {
      if (i%10 > 7) {
        return new M2();
      }
      return new M1();
    }
    // this should apply after compilation
    if (i%10 > 3) {
      return new M2();
    }
    return new M1();
  }
}

class M1 {
  public int computeSomeStuff(int arg) {
    return arg * 100;
  }
}

class M2 extends M1 {
  public int computeSomeStuff(int arg) {
    return arg * 67;
  }
}

class A {
  public void insane_name() {
    System.out.println("I am A");
  }
}


class C extends A {
  public void insane_name() {
    System.out.println("I am C");
  }
}
