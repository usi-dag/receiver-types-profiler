package com.msde.app;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.List;

public class BinaryFileSorter {

  public static Optional<File> createSortedFile(File binaryFile) {
    /* Outputs a file in which each line is a quadruplet of longs separated by spaces.
    The file is sorted by time (fourth column) and grouped by callsite id (first column) and compileid (second column).
    */
    try {
      LongList l = new LongList();
      File parent = binaryFile.getParentFile();
      String callsiteFileNumber = binaryFile.getName().replace("callsite_", "").replace(".txt", "");
      File workingFile = new File(parent, String.format("tmp_%s.txt", callsiteFileNumber));
      File sortedFile = new File(parent, String.format("sorted_%s.txt", callsiteFileNumber));
      FileWriter writer = new FileWriter(workingFile);
      BufferedWriter bWriter = new BufferedWriter(writer);
      try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(binaryFile.toPath().toString()))) {
        byte[] bytes = new byte[144 * 1024 * 1024];
        int len;
        outer: while ((len = in.read(bytes)) != -1) {
          for (int i = 0; i < len; i += 24) {
            long compileId = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + 8)).getLong();
            byte[] cs = new byte[8];
            cs[4] = bytes[i + 8];
            cs[5] = bytes[i + 8 + 1];
            cs[6] = bytes[i + 8 + 2];
            cs[7] = bytes[i + 8 + 3];
            long callsiteId = ByteBuffer.wrap(cs).getLong();
            cs[4] = bytes[i +8 + 4];
            cs[5] = bytes[i +8 + 5];
            cs[6] = bytes[i +8 + 6];
            cs[7] = bytes[i +8 + 7];
            long classNameId = ByteBuffer.wrap(cs).getLong();
            long timeDiff = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i + 16, i + 24)).getLong();
            if (compileId == 0 && callsiteId == 0 && classNameId == 0 && timeDiff == 0) {
              break outer;
            }
            l.add(callsiteId);
            l.add(compileId);
            l.add(classNameId);
            l.add(timeDiff);
          }
          List<String> quadruplets = IntStream.range(0, l.size() / 4)
              .mapToObj(i -> String.format("%d %d %d %d\n", l.get(4 * i), l.get(4 * i + 1), l.get(4 * i + 2), l.get(4 * i + 3)))
              .toList();
          quadruplets.forEach((e) -> {
            try {
              bWriter.write(e);
            } catch (IOException ex) {
              System.err.println(ex);
            }
          });
          l.clear();
        }
      }
      List<String> quadruplets = IntStream.range(0, l.size() / 4)
          .mapToObj(i -> String.format("%d %d %d %d\n", l.get(4 * i), l.get(4 * i + 1), l.get(4 * i + 2), l.get(4 * i + 3)))
          .toList();
      quadruplets.forEach((e) -> {
        try {
          bWriter.write(e);
        } catch (IOException ex) {
          System.err.println(ex);
        }
      });
      bWriter.close();
      ProcessBuilder pb = new ProcessBuilder("sort", "-nk1", "-nk2" , "-nk4", workingFile.getPath(), "-o", sortedFile.getPath());
      Process process = pb.start();
      int exitCode =  process.waitFor();
      workingFile.delete();
      return Optional.of(sortedFile);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    } catch(InterruptedException e){
      System.err.println(e.getMessage());
    }
    return Optional.empty();
  }

}
