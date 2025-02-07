package profiler;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;


public class Profiler{
    record CallInformation(String className, int time){}

    private final static long beginning;

    static {
      beginning = System.nanoTime()/1000;
    }
  
    private final static ConcurrentHashMap<String, ConcurrentLinkedDeque<CallInformation>> callSiteToVirtual= new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String, ConcurrentLinkedDeque<CallInformation>> callSiteToInterface= new ConcurrentHashMap<>();

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        callSiteToVirtual.forEach((k,v) -> {
          // System.out.println("Call site: "+ k + " " + v.toString());
        });
        Profiler.toJson(callSiteToVirtual, "virtual");

        callSiteToInterface.forEach((k, v) -> {
          // System.out.println("Call site: " + k + " " + v.toString());
        });
        Profiler.toJson(callSiteToInterface, "interface");
      }));
    }

    public static void addVirtualCall(String callSite, Object obj){
      if(obj == null || callSite == null){
        return;
      }else{
        long time = System.nanoTime();
        int timeDiff = (int) (time - Profiler.beginning)/1000;
        String targetClassName = obj.getClass().getName();
        callSiteToVirtual.computeIfAbsent(callSite, (k) -> new ConcurrentLinkedDeque<>()).add(new CallInformation(targetClassName, timeDiff));
        
      }
    }
    
    public static void addInterfaceCall(String callSite, Object obj){
      if(obj == null || callSite == null){
        return;
      }
      long time = System.nanoTime();
      int timeDiff = (int) (time - Profiler.beginning)/1000;
      String targetClassName = obj.getClass().getName();
      callSiteToInterface.computeIfAbsent(callSite, (k) -> new ConcurrentLinkedDeque<>()).add(new CallInformation(targetClassName, timeDiff));
    }

    private static void toJson(ConcurrentHashMap<String, ConcurrentLinkedDeque<CallInformation>> callSiteToInfo, String suffix){
            String baseDir = new File("").getAbsolutePath();
            
            File outputDir = new File("output/");
            if(!outputDir.isDirectory()){
              outputDir.mkdir();
            }
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yy-HH-mm-ss");
            Date date = new Date();
            String formattedDate = dateFormat.format(date);
            String result = callSiteToInfo.entrySet().stream().map((e) -> {
              String callSite = e.getKey();
              String info = "\"" + callSite + "\"";
              Map<String, List<Integer>> res = e.getValue().stream()
                .collect(Collectors.groupingBy(i -> "\"" + i.className+ "\"", Collectors.mapping(i -> i.time, Collectors.toList())));
              String b = res.entrySet().stream().map(i-> i.getKey() + ": " + i.getValue().toString())
                .collect(Collectors.joining(", "));
              return info + ": " + "{" +b + "}";
            }).collect(Collectors.joining(", \n"));
            String outputFileName = "output_" +suffix+ "_" + formattedDate + ".json";
            File outputFile = new File(outputDir, outputFileName);
            try{
              outputFile.createNewFile();
            }catch (Exception e){
            }
            try{
              Files.writeString(outputFile.toPath(), "{\n", StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), "\"beginning\": " + Profiler.beginning + ",\n", StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), result, StandardOpenOption.APPEND);
              Files.writeString(outputFile.toPath(), "}", StandardOpenOption.APPEND);
            }catch(Exception e){
              
            }
    }


}
