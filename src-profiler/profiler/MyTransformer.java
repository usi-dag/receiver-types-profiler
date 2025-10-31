package profiler;

import ch.usi.dag.disl.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import java.util.List;
import org.objectweb.asm.*;
import java.util.StringJoiner;

public class MyTransformer implements Transformer {

    public byte[] transform(byte[] classFileBytes) throws Exception {
        ClassReader classReader = new ClassReader(classFileBytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String className = classReader.getClassName();

        // List skippedClasses = List.of("java/lang/Object", "java/lang/constant/ConstantDesc", "java/lang/invoke/TypeDescriptor$OfField", "java/lang/invoke/TypeDescriptor", "java/lang/Cloneable",
        // "java/lang/Error", "java/lang/Exception", "java/lang/RuntimeException",
        // "java/lang/ReflectiveOperationException", "java/lang/Record",
        // "java/lang/NoClassDefFoundError", "java/lang/LinkageError",
        // "java/lang/ref/WeakReference", "java/io/DefaultFileSystem",
        // "java/lang/BaseVirtualThread", "java/util/Dictionary",
        // "java/lang/annotation/Annotation", "java/lang/invoke/ResolvedMethodName",
        // "java/lang/AssertionStatusDirectives", "java/lang/Terminator",
        // "java/lang/Void");


        // StringJoiner j = new StringJoiner(" ").setEmptyValue("(package-private)");
        // for(int remaining = classReader.getAccess(), bit; remaining != 0; remaining -= bit) {
        //     bit = Integer.lowestOneBit(remaining);
        //     switch(bit)
        //     {
        //         case Opcodes.ACC_PUBLIC:       j.add("public"); break;
        //         case Opcodes.ACC_PRIVATE:      j.add("private"); break;
        //         case Opcodes.ACC_PROTECTED:    j.add("protected"); break;
        //         case Opcodes.ACC_STATIC:       j.add("static"); break;
        //         case Opcodes.ACC_FINAL:        j.add("final"); break;
        //         case Opcodes.ACC_SYNCHRONIZED: j.add("synchronzied"); break;
        //         case Opcodes.ACC_BRIDGE:       j.add("(bridge)"); break;
        //         case Opcodes.ACC_VARARGS:      j.add("(varargs)"); break;
        //         case Opcodes.ACC_NATIVE:       j.add("native"); break;
        //         case Opcodes.ACC_ABSTRACT:     j.add("abstract"); break;
        //         case Opcodes.ACC_STRICT:       j.add("strictfp"); break;
        //         case Opcodes.ACC_SYNTHETIC:    j.add("synthetic"); break;
        //         case Opcodes.ACC_INTERFACE:    j.add("interface"); break;
        //     }
        // }
        // String decoded = j.toString();


        // if((classReader.getAccess() & Opcodes.ACC_INTERFACE) != 0){
        //     return classFileBytes;
        // }


        // if(skippedClasses.contains(className) || className.startsWith("java/lang/reflect") || className.startsWith("jdk/internal/reflect") || className.startsWith("jdk/internal")){
        //     return classFileBytes;
        // }

        // if(className.contains("Exception") || className.contains("Error")){
        //     return classFileBytes;
        // }

        // if (className.contains("$")) {
        //     return classFileBytes;
        // }

        RewriterVisitor classVisitor = new RewriterVisitor(classWriter, "_cool_prefix_" , className);
        classReader.accept(classVisitor, 0);
        var a = classWriter.toByteArray();
        return a;

    }
}
