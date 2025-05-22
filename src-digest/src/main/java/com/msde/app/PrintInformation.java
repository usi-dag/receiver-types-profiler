package com.msde.app;

import java.util.Map;
import java.util.stream.Collectors;

public interface PrintInformation {
  String[] formatInformation();
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

record Inversion(int window1, int window2, Map<Long, Double> w1, Map<Long, Double> w2, Map<Long, String> idToName) implements PrintInformation {
  @Override
  public String[] formatInformation() {
    String[] result = new String[3];

    result[0] = String.format("windows: %s - %s\n", this.window1, this.window2);
    String firstWindow = w1.entrySet().stream()
            .map(e -> String.format("%s: %f", idToName.get(e.getKey()), e.getValue()))
            .collect(Collectors.joining(", "));
    String secondWindow = w2.entrySet().stream()
            .map(e -> String.format("%s: %f", idToName.get(e.getKey()), e.getValue()))
            .collect(Collectors.joining(", "));
    result[1] = String.format("    First Window: %s\n", firstWindow);
    result[2] = String.format("    Second Window: %s\n", secondWindow);
    return result;
  }
}

record RatioChange(int window, Map<Long, Double> w1, Map<Long, Double> w2, Map<Long, String> idToName) implements PrintInformation {

  @Override
  public String[] formatInformation() {
    String[] result = new String[3];
    String firstWindow = w1.entrySet().stream()
            .map(e -> String.format("%s: %f", idToName.get(e.getKey()), e.getValue()))
            .collect(Collectors.joining(", "));
    String secondWindow = w2.entrySet().stream()
            .map(e -> String.format("%s: %f", idToName.get(e.getKey()), e.getValue()))
            .collect(Collectors.joining(", "));
    result[0] = String.format("window before: %s - window after: %s\n", this.window, this.window + 1);
    result[1] = String.format("    First Window: %s\n", firstWindow);
    result[2] = String.format("    Second Window: %s\n", secondWindow);
    return result;
  }
}

record WindowInformation(int window, Map<Long, Double> classNameToRatio, Map<Long, String> idToName) implements PrintInformation {

  @Override
  public String[] formatInformation() {
    String[] result = new String[2];
    String firstWindow = classNameToRatio.entrySet().stream()
      .map(e -> String.format("%s: %f", idToName.get(e.getKey()), e.getValue()))
      .collect(Collectors.joining(", "));
    result[0] = String.format("window information for window: %s\n", this.window);
    result[1] = String.format("    %s\n", firstWindow);
    return result;
  }
}
