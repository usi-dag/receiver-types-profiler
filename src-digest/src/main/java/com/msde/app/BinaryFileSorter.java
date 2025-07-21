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
        final int bufferSize = 144*1024*1024;
        final int bytesPerDataPoint = 24;
        byte[] bytes = new byte[bufferSize];
        int len;
        outer: while ((len = in.read(bytes)) != -1) {
          for (int i = 0; i < len; i += bytesPerDataPoint) {
            long compileId = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i + 8)).getLong();
            long packedLong = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i+8, i+16)).getLong();
            long callsiteId = packedLong >> 32;
            long classNameId = packedLong & 0xffffffffL;
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
      assert l.size() == 0;
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
