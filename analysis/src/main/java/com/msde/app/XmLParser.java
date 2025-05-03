package com.msde.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


class XmlParser {
  private List<String> content;

  public XmlParser(File compilerLog) {
    try {
        content = Files.lines(compilerLog.toPath()).filter(e -> e.contains("task ") ||
          e.contains("make_not_entrant") ||
          e.contains("uncommon_trap") ||
          e.contains("hotspot_log") ||
          e.startsWith("<nmethod ")
        )
        .map(l -> l.replace("&lt;", "<").replace("&gt;", ">"))
        .collect(Collectors.toList());
    } catch (IOException e) {
        e.printStackTrace();
    }
  }

  public Long getVmStartTime(){
    try{
      String timeStamp = this.content.get(0).split("time_ms='")[1].replace("'>", "");
      return Long.decode(timeStamp);
    } catch(NumberFormatException e){
      System.err.println(e.getMessage());
    }
    return 0L;
  }

  
  public List<Compilation> findCompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";

      List<Compilation> comps = this.content.stream().filter(l -> l.startsWith("<task ") && l.contains(methodDescriptor))
      .map(l -> {
        String compileId = l.split("compile_id='")[1].split("'")[0];
        String stamp = l.split("stamp='")[1].split("'")[0];
        stamp = stamp.replace(".", "");
        String kind = null;
        if(l.contains("compile_kind")){
          kind = l.split("compile_kind='")[1].split("'")[0];
        }
        return new Compilation(Long.decode(stamp), compileId, kind);
      }).toList();

      List<Compilation> cc = this.content.stream()
              .filter(l -> l.startsWith("<nmethod ") && l.contains(methodDescriptor))
              .map(l -> {
                String compileId = l.split("compile_id='")[1].split("'")[0];
                String stamp = l.split("stamp='")[1].split("'")[0];
                stamp = stamp.replace(".", "");
                String compiler=l.split("compiler='")[1].split("'")[0];
                return new Compilation(Long.decode(stamp), compileId, compiler);
              }).collect(Collectors.toCollection(ArrayList::new));

      List<String> ids = cc.stream().map(Compilation::id).collect(Collectors.toCollection(ArrayList::new));
      comps = comps.stream().filter(c-> !ids.contains(c.id())).toList();
      cc.addAll(comps);
      return cc;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }
    
  public List<Decompilation> findDecompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";

      List<String> compIds = this.content.stream().filter(l -> l.startsWith("<task ") && l.contains(methodDescriptor))
      .map(l -> l.split("compile_id='")[1].split("'")[0]).toList();
      List<Decompilation> docompilations = new ArrayList<>();
      
      for(String compileId: compIds){
        List<String> nonEntrant = this.content.stream().filter(l ->
           l.startsWith("<make_not_entrant") &&
           l.contains(String.format("compile_id='%s'", compileId))).toList();
        List<String> traps = this.content.stream().filter(l ->
           l.startsWith("<uncommon_trap") &&
           l.contains(String.format("compile_id='%s'", compileId))).toList();
        for(int i=0; i<nonEntrant.size(); i++){
          var attributes = nonEntrant.get(i);
          String value = null;
          if(attributes.contains("stamp=")){
            String stamp = attributes.split("stamp='")[1].split("'")[0];
            value = stamp.replace(".", "");
          }
          String kind = null;
          if(attributes.contains("compile_kind=")){
            kind = attributes.split("compile_kind='")[1].split("'")[0];
          }
          String reason = null;
          String action = null;
          if(i<traps.size()){
            var trapAttr = traps.get(i);
            if(trapAttr.contains("reason=")){   
              reason = trapAttr.split("reason='")[1].split("'")[0];
            }
            if(trapAttr.contains("action=")){   
              action = trapAttr.split("action='")[1].split("'")[0];
            }
          }
          Decompilation dec = new Decompilation(Long.decode(value), compileId, kind, reason, action);
          docompilations.add(dec);          
        }
      }
      return docompilations;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }

}
