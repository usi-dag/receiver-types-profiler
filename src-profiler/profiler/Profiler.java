package profiler;

import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Map.Entry;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
// import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
// import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;


public class Profiler{
    record CallInformation(String className, int time){}

    private final static long beginning;
    private static long id = 0;
    private final static ConcurrentHashMap<String, Long> classNameToId = new ConcurrentHashMap<>();
    private static AtomicInteger nextAvailableFileNumber = new AtomicInteger();

    static {
      beginning = System.nanoTime();
    }
  
    // private final static Long2ReferenceMap<ConcurrentLinkedDeque<Long>> test = new Long2ReferenceArrayMap<>();
    private final static ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> callSiteToVirtual= new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> callSiteToInterface= new ConcurrentHashMap<>();

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("ShutdownHook");
        Profiler.saveClassNameMapping();
        callSiteToVirtual.forEach((k,v) -> {
          // System.out.println("Call site: "+ k + " " + v.toString());
        });
        System.out.println("Before dumping virtual");
        Profiler.toJson(callSiteToVirtual, "virtual");
        System.out.println("After dumping virtual");

        callSiteToInterface.forEach((k, v) -> {
          // System.out.println("Call site: " + k + " " + v.toString());
        });        
        System.out.println("Before dumping interface");
        Profiler.toJson(callSiteToInterface, "interface");
        System.out.println("After dumping interface");
        // for(Long2ReferenceMap.Entry<ConcurrentLinkedDeque<Long>> el: test.long2ReferenceEntrySet()){
        //     System.out.println(el.getKey()+" " + el.getValue());
        // }
      }));
    }

    public static void saveBufferInformation(long addr, int maxIndex){
      String baseDir = new File("").getAbsolutePath();
            
      File outputDir = new File("output/");
      if(!outputDir.isDirectory()){
        outputDir.mkdir();
      }
      int fileNumber = nextAvailableFileNumber.getAndIncrement();
      String outputFileName = "partial_"+fileNumber + ".txt";
      File outputFile = new File(outputDir, outputFileName);
      try{
        outputFile.createNewFile();
      }catch (IOException e){
        System.err.println(e.getMessage());
      }
      try{
        StringBuilder toPrint = new StringBuilder();
        for(int i = 0; i< maxIndex; i++){
          long cs = Profiler.getUnsafeInstance().getLong(addr+i*8);
          long cnid = Profiler.getUnsafeInstance().getLong(addr+(i+1)*8);
          long tdiff = Profiler.getUnsafeInstance().getLong(addr+(i+2)*8);
          toPrint.append(cs + " " + cnid + " " + tdiff + "\n");
          // Files.writeString(outputFile.toPath(), cs + " " + cnid + " " + tdiff + "\n", StandardOpenOption.APPEND);
          
        }
        Files.writeString(outputFile.toPath(), toPrint.toString(), StandardOpenOption.APPEND);
      }catch(IOException e){
        System.err.println(e.getMessage());
      }
      
    }

    public static void putBytes(long addr, int index, long callsite, Object obj){
      long time = System.nanoTime();
      long timeDiff = (time - Profiler.beginning)/1000;
      String targetClassName = obj.getClass().getName();
      long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);
      
      Profiler.getUnsafeInstance().putLong(addr + index*8, callsite);
      Profiler.getUnsafeInstance().putLong(addr + (index+1)*8, tid);
      Profiler.getUnsafeInstance().putLong(addr + (index+2)*8, timeDiff);
    }

    public static Unsafe getUnsafeInstance() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Unsafe u = (Unsafe) f.get(null);
            return u;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            return  null;
        }
    }

    public static long getNewAddress(){
      return Profiler.getUnsafeInstance().allocateMemory(3*512*1024*8);
    }
  
    public static long getClassMapping(Object obj){
      return classNameToId.computeIfAbsent(obj.getClass().getName(), k -> id++);
    }

    public static void addVirtualCall(long callsite, Object obj){
      // if(obj == null || callsite == -1){
      //   return;
      // }else{
        long time = System.nanoTime();
        Long timeDiff = (time - Profiler.beginning)/1000;
        String targetClassName = obj.getClass().getName();
        long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);
        // if(!test.containsKey(callsite)){
        //     test.put(callsite, new ConcurrentLinkedDeque<>());
        // }
        // test.get(callsite).add(tid);
        // test.get(callsite).add(timeDiff);
        callSiteToVirtual.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(tid);
        callSiteToVirtual.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(timeDiff);
        
      // }
    }
    
    public static void addInterfaceCall(long callsite, Object obj){
      if(obj == null ){
        return;
      }
      long time = System.nanoTime();
      long timeDiff = (time - Profiler.beginning)/1000;
      String targetClassName = obj.getClass().getName();
      long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);
      callSiteToInterface.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(tid);
      callSiteToInterface.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(timeDiff);
    }


    private static void saveClassNameMapping(){
      File outputDir = new File("output/");
      if(!outputDir.isDirectory()){
        outputDir.mkdir();
      }
      SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yy_HH_mm");
      Date date = new Date();
      String formattedDate = dateFormat.format(date);
      String outputFileName = "classNameMapping_" +  formattedDate + ".csv";
      File outputFile = new File(outputDir, outputFileName);
      try{
        outputFile.createNewFile();
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

    private static void toJson(ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> callSiteToInfo, String suffix){
            String baseDir = new File("").getAbsolutePath();
            
            File outputDir = new File("output/");
            if(!outputDir.isDirectory()){
              outputDir.mkdir();
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy-HH-mm-ss");
            Date date = new Date();
            String formattedDate = dateFormat.format(date);
            String result = callSiteToInfo.entrySet().stream().filter(e -> e.getKey() != -1).filter(e -> e.getValue().size() < 100000).map((e) -> {
              Long callSite = e.getKey();
              String info = "\"" + callSite + "\"";
              List<Long> callInfo = e.getValue().stream().toList();
              return info + ": " + callInfo.toString();
            }).collect(Collectors.joining(", \n"));
            String outputFileName = "output_" +suffix+ "_" + formattedDate + ".json";
            File outputFile = new File(outputDir, outputFileName);
            try{
              outputFile.createNewFile();
            }catch (IOException e){
              System.err.println(e.getMessage());
            }
            try{
              Files.writeString(outputFile.toPath(), "{\n", StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), "\"beginning\": " + Profiler.beginning/1000 + ",\n", StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), result, StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), "}", StandardOpenOption.APPEND);
            }catch(IOException e){
              System.err.println(e.getMessage());
            }
    }


}
