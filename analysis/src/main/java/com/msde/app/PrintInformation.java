package com.msde.app;

public interface PrintInformation {
  public String[] formatInformation();
}

record RatioChange(int window, String className, double before,
    double after, double diff) implements PrintInformation {

  @Override
  public String[] formatInformation() {
    String[] result = new String[1];
    result[0] = String.format("%s - window before: %s - window after: %s - diff: %s - before: %s - after: %s\n",
        this.className, this.window, this.window + 1, this.diff, this.before, this.after);
    return result;
  }
}

record Compilation(long time, String id, String kind) implements PrintInformation {
  @Override
  public String[] formatInformation() {
    String[] result = new String[1];
    result[0] = String.format("Compilation at: %s [kind = %s, id = %s]\n", this.time, this.kind, this.id);
    return result;
  }

  public Compilation withTime(long newTime) {
    return new Compilation(newTime, this.id, this.kind);
  }
}

record Decompilation(long time, String id, String kind, String reason, String action)
    implements PrintInformation {
  @Override
  public String[] formatInformation() {
    String[] result = new String[1];
    String trap = "";
    if (reason != null || action != null) {
      trap = String.format("[trap: reason = %s, action = %s]", reason, action);
    }
    result[0] = String.format("Decompilation at: %s[compile_id = %s, compile_kind = %s]%s\n", this.time, this.id,
        this.kind, trap);
    return result;
  }

  public Decompilation withTime(long newTime){
    return new Decompilation(newTime, id, kind, reason, action);
  }
}

record Inversion(int window1, int window2, String className1, String className2, Double valKey1Window1,
    Double valKey2Window1, Double valKey1Window2, Double valKey2Window2) implements PrintInformation {
  @Override
  public String[] formatInformation() {
    String[] result = new String[3];

    result[0] = String.format("%s - %s - windows: %s - %s\n", this.className1, this.className2, this.window1,
        this.window2);
    result[1] = String.format("First Window: %s - %s, %s - %s\n", this.className1, this.className2, this.valKey1Window1,
        this.valKey2Window1);
    result[2] = String.format("Second Window: %s - %s, %s - %s\n", this.className1, this.className2,
        this.valKey1Window2, this.valKey2Window2);
    return result;
  }
}
