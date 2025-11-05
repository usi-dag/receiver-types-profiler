package profiler;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.objectweb.asm.*;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.io.BufferedWriter;
import java.io.FileWriter;

import java.util.StringJoiner;


public class RewriterVisitor extends ClassVisitor implements Opcodes {
    static final String ANSI_RESET = "\u001B[0m";
    static final String ANSI_BLACK = "\u001B[30m";
    static final String ANSI_RED = "\u001B[31m";
    static final String ANSI_GREEN = "\u001B[32m";
    static final String ANSI_YELLOW = "\u001B[33m";
    static final String ANSI_BLUE = "\u001B[34m";
    static final String ANSI_PURPLE = "\u001B[35m";
    static final String ANSI_CYAN = "\u001B[36m";
    static final String ANSI_WHITE = "\u001B[37m";

    record NativeMethod(String name, String desc, Integer access, String[] exceptions) {
    };

    private String className;
    private String prefix;
    private ArrayList<NativeMethod> nativeMethods = new ArrayList<>();

    public RewriterVisitor(ClassVisitor cv, String prefix, String className) {
        super(Opcodes.ASM9, cv);
        this.prefix = prefix;
        this.className = className;
    }

    private static BufferedWriter bw;

    static{
         String baseDir = new File("").getAbsolutePath();
         File outputDir = new File("output/");
         if(!outputDir.isDirectory()){
           outputDir.mkdir();
         }

         String outputFileName = "disl.log";
         File outputFile = new File(outputDir, outputFileName);
         try{
           outputFile.createNewFile();
             bw = new BufferedWriter(new FileWriter(outputFile));
         }catch (IOException e){
           System.err.println(e.getMessage());
         }
         Runtime.getRuntime().addShutdownHook(new Thread(() -> {
             try{
                 bw.close();
             }catch(IOException e){
                 
             }
         }
         ));
    }

    public void visitEnd() {
        for (NativeMethod nativeMethod : this.nativeMethods) {
            int maxStack = 0;
            MethodVisitor mv = this.cv.visitMethod(nativeMethod.access - ACC_NATIVE, nativeMethod.name,
                    nativeMethod.desc, null, null);
            mv.visitCode();

            // Begin the try/catch block and corresponding labels
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitTryCatchBlock(l0, l1, l2, null);

            // Inform the agent that we call a native method
            // mv.visitMethodInsn(INVOKESTATIC, "Agent", "java2nativeBegin",
            // "()V");

            // label defining the start of the try/catch block
            mv.visitLabel(l0);

            // if the method is not static first do a ALOAD (for a pointer to
            // the object, i think...)
            if ((nativeMethod.access & Opcodes.ACC_STATIC) == 0) {
                mv.visitVarInsn(ALOAD, maxStack);
                maxStack++;
            }

            // handling the arguments: we have to do different things with the
            // different types of arguments
            /*
             * for the Z,B,C,S,I (respectively boolean, byte, char, short and
             * int) it is the same: ILOAD + increment the maxStack value for the
             * J (long) LLOAD + increment by 2 the maxStack value for the F
             * (float) FLOAD + increment the maxStack value for the D (double)
             * DLOAD + increment by 2 the maxStack value for object and array
             * type (Lobjectname or [type) ALOAD + increment the maxStack value
             */
            int left = nativeMethod.desc.indexOf('(');
            int right = nativeMethod.desc.indexOf(')');
            int args = 0;

            boolean skipNext = false;
            // put arguments on the stack
            // so that the invocation of the method can use them
            while (right - left > 1) {
                skipNext = false;
                int increment = 1;
                int stackIncrement = 1;
                int opcode;
                switch (nativeMethod.desc.charAt(left+1)) {
                    case 'B', 'C', 'I', 'S', 'Z' -> {
                        opcode = 21;
                    }
                    case 'D' -> {
                        stackIncrement = 2;
                        opcode = 24;
                    }
                    case 'F' -> {
                        opcode = 23;
                    }
                    case 'J' -> {
                        opcode = 22;
                        stackIncrement = 2;
                    }
                    case 'L' -> {
                        opcode = 25;
                        // aaaaLObject;bbbb
                        increment = nativeMethod.desc.indexOf(";", left + 1) - left;
                    }
                    case '[' -> {
                        opcode = 25;
                        skipNext = true;
                    }
                    default -> {
                        System.out.println("This should not happen!");
                        System.out.println(String.format("Method: %s/%s", this.className, nativeMethod.name));
                        System.out.println(String.format("Descriptor: %s, %d - %d", nativeMethod.desc, left, right));
                        System.out.println(String.format("Character: %c", nativeMethod.desc.charAt(left)));
                        opcode = -1;
                    }
                }

                mv.visitVarInsn(opcode, maxStack);
                maxStack += stackIncrement;
                args++;
                left += increment;

                // Skip next arg expression if necessary (i.e., if we had an
                // array type)
                if (skipNext) {
                    while (nativeMethod.desc.charAt(left + 1) == '[') {
                        left++;
                    }
                    if (nativeMethod.desc.charAt(left + 1) == 'L') {
                        left = nativeMethod.desc.indexOf(";", left + 1);
                    } else {
                        left++;
                    }
                }
            }


            // Call the prefixed method: INVOKESTATIC for static methods,
            // INVOKESPECIAL in all other cases
            if ((nativeMethod.access & Opcodes.ACC_STATIC) == 0) {
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, this.className, this.prefix + nativeMethod.name,
                        nativeMethod.desc);
            } else {

                mv.visitMethodInsn(Opcodes.INVOKESTATIC, this.className, this.prefix + nativeMethod.name,
                        nativeMethod.desc);
            }

            // Storing the result (when necessary)
            skipNext = true;
            int returnInstr = -1;
            int maxLocals = maxStack;
            // return the value obtained from the method invocation
            switch (nativeMethod.desc.charAt(right + 1)) {
                case 'B', 'C', 'I', 'S', 'Z' -> {
                    maxLocals = maxStack + 1;
                    returnInstr = IRETURN;
                }
                case 'D' -> {
                    maxLocals = maxStack + 2;
                    returnInstr = DRETURN;
                }
                case 'F' -> {
                    maxLocals = maxStack + 1;
                    returnInstr = FRETURN;
                }
                case 'J' -> {
                    maxLocals = maxStack + 2;
                    returnInstr = LRETURN;
                }
                case 'L', '[' -> {
                    maxLocals = maxStack + 1;
                    returnInstr = ARETURN;
                }
                case 'V' -> {
                    returnInstr = RETURN;
                }
                default -> {
                    System.out.println(
                            "This should not happen! (unexpected return type" + nativeMethod.desc + ", " + left + " "
                                    + right + " " + this.className + nativeMethod.name);
                }
            }

            if(returnInstr == -1){
                System.out.println("CHECK HERE");
            }
            
            mv.visitLabel(l1);
            mv.visitInsn(returnInstr);
            mv.visitLabel(l2);
            mv.visitInsn(ATHROW);
            mv.visitMaxs(maxLocals + 2, maxLocals + 2);
            mv.visitEnd();
            System.out.printf("%sWrapper method added:%s [%s] %s%s %s (with %d args)\n", ANSI_GREEN, ANSI_RESET,
                 this.className, this.prefix, nativeMethod.name, nativeMethod.desc, args);
            dumpInfo(nativeMethod);
        }

        this.cv.visitEnd();
    }

    private void dumpInfo(NativeMethod nativeMethod){
        try{
            StringJoiner j = new StringJoiner(" ").setEmptyValue("(package-private)");
             for(int remaining = nativeMethod.access, bit; remaining != 0; remaining -= bit) {
                 bit = Integer.lowestOneBit(remaining);
                 switch(bit)
                 {
                     case Opcodes.ACC_PUBLIC:       j.add("public"); break;
                     case Opcodes.ACC_PRIVATE:      j.add("private"); break;
                     case Opcodes.ACC_PROTECTED:    j.add("protected"); break;
                     case Opcodes.ACC_STATIC:       j.add("static"); break;
                     case Opcodes.ACC_FINAL:        j.add("final"); break;
                     case Opcodes.ACC_SYNCHRONIZED: j.add("synchronzied"); break;
                     case Opcodes.ACC_BRIDGE:       j.add("(bridge)"); break;
                     case Opcodes.ACC_VARARGS:      j.add("(varargs)"); break;
                     case Opcodes.ACC_NATIVE:       j.add("native"); break;
                     case Opcodes.ACC_ABSTRACT:     j.add("abstract"); break;
                     case Opcodes.ACC_STRICT:       j.add("strictfp"); break;
                     case Opcodes.ACC_SYNTHETIC:    j.add("synthetic"); break;
                     case Opcodes.ACC_INTERFACE:    j.add("interface"); break;
                 }
             }
             String decoded = j.toString();
            RewriterVisitor.bw.write(String.format("[%s] [%s] %s%s %s\n",
                decoded, this.className, this.prefix, nativeMethod.name, nativeMethod.desc));
        }catch(IOException e){
            
        }
    }


    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        List skippedClasses = List.of( "java/lang/Class", "jdk/internal/loader/BootLoader",
         "jdk/internal/misc/CDS", "jdk/internal/reflect/Reflection", "java/lang/invoke/VarHandle",
        "java/lang/ref/Finalizer", "java/lang/Runtime");
        List skippedMethods = List.of("getExtendedNPEMessage", "registerNatives", "getMaxLaneCount", "longBitsToDouble",
            "doubleToRawLongBits", "floatToRawIntBits", "intBitsToFloat",  "platformProperties", "vmProperties",
          "findBuiltinLib", "getStackAccessControlContext", "getInheritedAccessControlContext",
        "invokeExact", "findEntry0", "fillInStackTrace");
        // System.out.println(String.format("CLASSNAME IS: %s", this.className));
        // System.out.println(String.format("Contained?: %b", skippedClasses.contains(this.className)));
        if ((access & Opcodes.ACC_NATIVE) != 0 && !skippedClasses.contains(this.className)) {
            if (!name.startsWith(this.prefix)) {
                if(skippedMethods.contains(name)){
                    return this.cv.visitMethod(access, name, desc, signature, exceptions);
                }
                if(this.className.equals("jdk/internal/misc/VM") && name.equals("initialize")){
                    return this.cv.visitMethod(access, name, desc, signature, exceptions);
                }
                // System.out.println(String.format("%snative method found: %s %s %s", ANSI_BLUE, name, desc, ANSI_RESET));
                // System.out.println(String.format("%sAccess: %d Signature: %s Exceptions: %s%s", ANSI_BLUE, access, signature, String.valueOf(exceptions), ANSI_RESET));
                this.nativeMethods.add(new NativeMethod(name, desc, access, exceptions));
                name = this.prefix + name;
            } else {
                System.out.println("WARNING: found an already prefixed method");
            }
        }
        return this.cv.visitMethod(access, name, desc, signature, exceptions);
    }
}
