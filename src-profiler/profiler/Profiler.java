package profiler;


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
    private final static long beginning;
    private final static long start;
    private static long id = 0;
    public final static long length = 3*512*1024;
    private final static ConcurrentHashMap<String, Long> classNameToId = new ConcurrentHashMap<>();
    private static final AtomicInteger nextAvailableFileNumber = new AtomicInteger();

    static {
      beginning = System.nanoTime();
      start = System.currentTimeMillis();
    }

    private final static File outputDir;

    static {
     // ensure output folder exist
      outputDir = new File("output/");
      if(!outputDir.isDirectory()){
        var res = outputDir.mkdir();
      }

      // shutdownhook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("ShutdownHook");
        Profiler.saveClassNameMapping();
        Profiler.saveStartTime();
      }));
    }

    // memory mapped file
    public static MappedByteBuffer getMemoryMappedFile(){
      int fileNumber = nextAvailableFileNumber.getAndIncrement();
      String outputFileName = "partial_"+fileNumber + ".txt";
      File outputFile = new File(outputDir, outputFileName);

      try{
        RandomAccessFile ra = new RandomAccessFile(outputFile, "rw");
        return ra.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, length*8);
      } catch(IOException e){
        System.err.println(e.getMessage());
      }
      return null;
    }

    public static int putInfo(MappedByteBuffer mb, int index, long callsite, Object obj){
      // if(callsite == -1){
      //   return index;
      // }
      long time = System.nanoTime();
      long timeDiff = (time - Profiler.beginning)/1000;
      String targetClassName = obj.getClass().getName();
      long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);

      long val = ((callsite) << 32) | (tid & 0xffffffffL);
      mb.putLong(val);
      // mb.putInt((int) (callsite & 0xFFFFFFFFL));
      // mb.putInt((int) (tid & 0xFFFFFFFFL));
      mb.putLong(timeDiff);
      // long a = 1l;
      // long b = 2l;
      // long c = 3l;

      // mb.putInt((int) (a & 0xFFFFFFFFL));
      // mb.putInt((int) (b & 0xFFFFFFFFL));
      // mb.putLong(c);

      return index+3;

    }

    public static int putInfo(MappedByteBuffer mb, int index, long callsite, String name){
      long time = System.nanoTime();
      long timeDiff = (time - Profiler.beginning)/1000;
      long tid = classNameToId.computeIfAbsent(name, (k) -> id++);

      long val = ((callsite) << 32) | (tid & 0xffffffffL);
      mb.putLong(val);
      mb.putLong(timeDiff);
      return index+3;
    }

    private static void saveClassNameMapping(){
      SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yy_HH_mm");
      Date date = new Date();
      String formattedDate = dateFormat.format(date);
      String outputFileName = "classNameMapping_" +  formattedDate + ".csv";
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

    private static void saveStartTime(){
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy-HH-mm-ss");
            Date date = new Date();
            String formattedDate = dateFormat.format(date);
            String outputFileName = "start_time_" + formattedDate + ".txt";
            File outputFile = new File(outputDir, outputFileName);
            try{
              var res = outputFile.createNewFile();
            }catch (IOException e){
              System.err.println(e.getMessage());
            }
            try{
              Files.writeString(outputFile.toPath(), String.format("%s\n", Profiler.beginning/1000), StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), ""+Profiler.start, StandardOpenOption.APPEND);
            }catch(IOException e){
              System.err.println(e.getMessage());
            }
    }


}
