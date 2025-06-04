import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;

public class Utility {
  record Args(File binaryFile, File callsiteId, File classId, File inputFolder) {
  }

  private static Args parseArgs(String[] args) {
    Args parsedArgs = new Args(null,
        new File("/home/ubuntu/receiver-types-profiler/output/callsite_to_id_21_03_25_11_25.csv"),
        new File("/home/ubuntu/receiver-types-profiler/output/classNameMapping_21_03_25_11_25.csv"),
        null
      );
    int i = 0;
    if(args.length < 1){
        System.out.println("usage: [--help] [--callsite callsitefile] [--id file] [--id-class classnameid] [--input-folder folder]");
        System.out.println("       --input-folder and --callsite are not compatible.");
        System.exit(0);
    }
    while (i < args.length) {
      String current = args[i++];
      switch (current) {
        case "--callsite", "-c" -> {
          File callsite = new File(args[i++]);
          if(parsedArgs.inputFolder != null){
            System.out.println("usage: [--help] [--callsite callsitefile] [--id file] [--id-class classnameid] [--input-folder folder]");
            System.out.println("       --input-folder and --callsite are not compatible.");
            System.exit(0);
          }
          parsedArgs = new Args(callsite, parsedArgs.callsiteId, parsedArgs.classId, parsedArgs.inputFolder );
        }
        case "--id", "-i" -> {
          File callsiteId = new File(args[i++]);
          parsedArgs = new Args(parsedArgs.binaryFile, callsiteId, parsedArgs.classId, parsedArgs.inputFolder);

        }
        case "--id-class", "-k" -> {
          File classId= new File(args[i++]);
          parsedArgs = new Args(parsedArgs.binaryFile, parsedArgs.callsiteId, classId, parsedArgs.inputFolder);

        }
        case "--input-folder", "-f" -> {
          File inputFolder = new File(args[i++]);
          if(parsedArgs.binaryFile!=null){
            System.out.println("usage: [--help] [--callsite callsitefile] [--id file] [--id-class classnameid] [--input-folder folder]");
            System.out.println("       --input-folder and --callsite are not compatible.");
            System.exit(0);
            
          }
          parsedArgs = new Args(parsedArgs.binaryFile, parsedArgs.callsiteId, parsedArgs.classId, inputFolder);
        }
        default -> {
          System.out.println("usage: [--help] [--callsite callsitefile] [--id file] [--id-class classnameid] [--input-folder folder]");
          System.out.println("       --input-folder and --callsite are not compatible.");
          System.exit(0);
        }
      }

    }

    return parsedArgs;
  }

  public static void main(String[] args) {
    Args arguments = parseArgs(args);
    Map<Long, String> idToCallsite = parseCsvMapping(arguments.callsiteId);
    Map<Long, String> idToClassname= parseCsvMapping(arguments.classId);
    if(arguments.binaryFile!=null){
      File output = new File("readable.txt");
      try{
        output.createNewFile();
      }catch(IOException e){
        System.err.println(e.getMessage());
      }
      binaryToReadable(arguments.binaryFile, idToCallsite, idToClassname, output);
    } else {
        File[] callsites = arguments.inputFolder.listFiles((el) -> el.getName().startsWith("callsite"));
        assert callsites != null;
        File outputFolder = new File("readable");
        if(!outputFolder.exists()){
          outputFolder.mkdir();
        }
        for(File callsiteFile: callsites){
          String callsiteFileNumber = callsiteFile.getName().replace("callsite_", "").replace(".txt", "");
          File output = new File(outputFolder, String.format("readable_%s.txt", callsiteFileNumber));
          try{
            output.createNewFile();
          }catch(IOException e){
            System.err.println(e.getMessage());
          }
          binaryToReadable(callsiteFile, idToCallsite, idToClassname, output);
        }
    }
  }

  private static Map<Long, String> parseCsvMapping(File inputFile) {
    try {
      List<String> lines = Files.readAllLines(inputFile.toPath());
      return lines.stream().map(l -> l.split(","))
          .collect(Collectors.toMap(el -> Long.valueOf(el[1]), el -> el[0]));

    } catch (IOException e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  private static void binaryToReadable(File callsite, Map<Long, String> idToCallsite, Map<Long, String> idToClassname, File output) {
    try {
      int readBytes = 0;
      List<String> info = new ArrayList<>();
      try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(callsite.toString()))) {
        byte[] bytes = new byte[128 * 1024 * 1024];
        int len;
        outer: while ((len = in.read(bytes)) != -1) {
          for (int i = 0; i < len; i += 16) {
            byte[] cs = new byte[8];
            cs[4] = bytes[i];
            cs[5] = bytes[i + 1];
            cs[6] = bytes[i + 2];
            cs[7] = bytes[i + 3];
            long callsiteId = ByteBuffer.wrap(cs).getLong();
            cs[4] = bytes[i + 4];
            cs[5] = bytes[i + 5];
            cs[6] = bytes[i + 6];
            cs[7] = bytes[i + 7];
            long classNameId = ByteBuffer.wrap(cs).getLong();
            long timeDiff = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i + 8, i + 16)).getLong();
            if (callsiteId == 0 && classNameId == 0 && timeDiff == 0) {
              break outer;
            }
            info.add(idToCallsite.get(callsiteId));
            info.add(idToClassname.get(classNameId));
            info.add(String.valueOf(timeDiff));
            readBytes += 16;
            if (readBytes >= 128 * 1024 * 1024) {
              StringBuilder sb = new StringBuilder();
              for (int j = 0; j < info.size(); j += 3) {
                sb.append(String.format("%s %s %s\n", info.get(j), info.get(j + 1), info.get(j + 2)));
              }
              Files.writeString(output.toPath(), sb, StandardOpenOption.APPEND);
            }
          }
        }
      }
      StringBuilder sb = new StringBuilder();
      for (int j = 0; j < info.size(); j += 3) {
        sb.append(String.format("%s %s %s\n", info.get(j), info.get(j + 1), info.get(j + 2)));
      }
      Files.writeString(output.toPath(), sb, StandardOpenOption.APPEND);
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }
}
