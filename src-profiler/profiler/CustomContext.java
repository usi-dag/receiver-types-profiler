package profiler;

import ch.usi.dag.disl.staticcontext.InstructionStaticContext;
import ch.usi.dag.disl.util.JavaNames;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.transform.Source;

public class CustomContext extends InstructionStaticContext{
    private static long id = 0;
    private static Map<String, Long> csToId= new HashMap<>();

    public static Map<String, Long> getMapping(){
        return csToId;
    }

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         // System.out.println("Cs is: ");
         // System.out.println(csToId);
         String baseDir = new File("").getAbsolutePath();
            
         File outputDir = new File("output/");
         if(!outputDir.isDirectory()){
           outputDir.mkdir();
         }

         String outputFileName = "callsite_to_id.csv";
         File outputFile = new File(outputDir, outputFileName);
         try{
           outputFile.createNewFile();
         }catch (IOException e){
           System.err.println(e.getMessage());
         }

         try{
           for(Entry<String, Long> el: csToId.entrySet()){
             Files.writeString(outputFile.toPath(), el.getKey() +","+ el.getValue()+"\n", StandardOpenOption.APPEND);
           }
           
         }catch(IOException e){
           System.err.println(e.getMessage());
         }
        }
        ));
    }

  
    
    public long getTargetId(){
        int index = this.getIndex();
        MethodNode mn = staticContextData.getMethodNode();
        ClassNode cn = staticContextData.getClassNode();
        String name = JavaNames.methodUniqueName(cn.name, mn.name, mn.desc);
        String callsite = index + " " + name;
        InsnList list = staticContextData.getMethodNode().instructions;
        AbstractInsnNode[] instructions = Arrays.stream(list.toArray()).filter(n -> {
          // n.getOpcode() == -1
          switch(n){
            case LabelNode node -> {return false;}
            case FrameNode node -> {return false;}
            case LineNumberNode node -> {return false;}
            default -> {
            return true;
          }
          }
        }).toArray(AbstractInsnNode[]::new);
        AbstractInsnNode node =instructions[index];
        switch(node){
            case MethodInsnNode n -> {
              Type type = Type.getMethodType(n.desc);
              int argSize = type.getArgumentsAndReturnSizes() >> 2;
              callsite += " "+n.name+n.desc;
            }
            default -> {}
        }
        if(!csToId.containsKey(callsite)){
            csToId.put(callsite, id++);
        }
        return csToId.get(callsite);
    }


    public int getTargetName(){
        InsnList list = staticContextData.getMethodNode().instructions;
        AbstractInsnNode[] instructions = Arrays.stream(list.toArray()).filter(n -> {
          switch(n){
            case LabelNode node -> {return false;}
            case FrameNode node -> {return false;}
            default -> {
            return true;
          }
          }
        }).toArray(AbstractInsnNode[]::new);
        int index = this.getIndex();
        AbstractInsnNode node =instructions[index];
        // System.out.println(this.getIndex() + " " + this.getOpcode() +" " + node.getClass().getName());
        // System.out.println(instructions.length);
        switch(node){
            case MethodInsnNode n -> {
              Type type = Type.getMethodType(n.desc);
              int argSize = type.getArgumentsAndReturnSizes() >> 2;
              return argSize;
            }
            default -> {}
        }
        return -1;
    }

    public String getType(){
        InsnList list = staticContextData.getMethodNode().instructions;
        int index = this.getIndex();
        AbstractInsnNode node = list.get(index);
        switch(node){
            case TypeInsnNode n -> {
                return n.desc;
            }
            case MultiANewArrayInsnNode n -> {
                return n.desc;
            }
            default -> {}
        }
        return "";
    }


}
