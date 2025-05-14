package extractor;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.Map.Entry;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class Profiler{
    private final static ConcurrentHashMap<String, Long> classNameToId = new ConcurrentHashMap<>();
    private static long id = 0;



    private final static File outputDir;

    static {
     // ensure output folder exist
      outputDir = new File("output/");
      if(!outputDir.isDirectory()){
        var res = outputDir.mkdir();
      }

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        Profiler.saveClassNameMapping();
      }));
    }

    public static int putInfo(Object obj, String aaa){
      String targetClassName = obj.getClass().getName();
      long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);
      return 3;
    }

    private static void saveClassNameMapping(){
      String outputFileName = "extracted.csv";
      File outputFile = new File(outputDir, outputFileName);
      try{
        var res = outputFile.createNewFile();
      }catch(IOException e){
        System.err.println(e.getMessage());
      }
      try{
        for(Entry<String, Long> el: classNameToId.entrySet()){
          Files.writeString(outputFile.toPath(), el.getKey()+ ","+el.getValue()+"\n", StandardOpenOption.APPEND);
        }
      }catch(IOException e){
        System.err.println(e.getMessage());
      }

    }
}
