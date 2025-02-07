package profiler;

import ch.usi.dag.disl.staticcontext.InstructionStaticContext;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import java.util.Arrays;

import javax.xml.transform.Source;

public class CustomContext extends InstructionStaticContext{

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
