package profiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;


public class Profiler{
    record CallInformation(String className, int time){}

    private final static long beginning;
    private static long id = 0;
    private final static ConcurrentHashMap<String, Long> classNameToId = new ConcurrentHashMap<>();

    static {
      beginning = System.nanoTime();
    }
  
    private final static ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> callSiteToVirtual= new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> callSiteToInterface= new ConcurrentHashMap<>();

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("ShutdownHook");
        callSiteToVirtual.forEach((k,v) -> {
          // System.out.println("Call site: "+ k + " " + v.toString());
        });
        Profiler.toJson(callSiteToVirtual, "virtual");

        callSiteToInterface.forEach((k, v) -> {
          // System.out.println("Call site: " + k + " " + v.toString());
        });
        Profiler.toJson(callSiteToInterface, "interface");
        Profiler.saveClassNameMapping();
      }));
    }

    public static void addVirtualCall(long callsite, Object obj){
      if(obj == null){
        return;
      }else{
        long time = System.nanoTime();
        Long timeDiff = (time - Profiler.beginning)/1000;
        String targetClassName = obj.getClass().getName();
        long tid = classNameToId.computeIfAbsent(targetClassName, (k) -> id++);
        callSiteToVirtual.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(tid);
        callSiteToVirtual.computeIfAbsent(callsite, (k) -> new ConcurrentLinkedDeque<>()).add(timeDiff);
        
      }
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
            String result = callSiteToInfo.entrySet().stream().map((e) -> {
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
