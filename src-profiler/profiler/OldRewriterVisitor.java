/**
 * Copyright (c) 2010 Aibek Sarimbekov, Philippe Moret, Walter Binder
 * 
 * Permission to copy, use, modify (only the included source files, modification
 * of the binaries is not permitted) the software is granted provided this
 * copyright notice appears in all copies.
 * 
 * This software is  provided "as is" without express or implied warranty, and
 * with no claim as to its suitability for any purpose.
 */
package profiler;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.ListIterator;

public class OldRewriterVisitor extends ClassVisitor implements Opcodes {

    private String className;
    private String prefix;
    private boolean modified;
    private ArrayList<String> nativeNames;
    private ArrayList<String> nativeDesc;
    private ArrayList<Integer> nativeAcc;
    private ArrayList<String[]> nativeExc;

    public OldRewriterVisitor(ClassVisitor cv, String prefix, String className) {
        super(Opcodes.ASM4, cv);
        nativeNames = new ArrayList<String>();
        nativeDesc = new ArrayList<String>();
        nativeAcc = new ArrayList<Integer>();
        nativeExc = new ArrayList<String[]>();
        this.prefix = prefix;
        this.modified = false;
        this.className = className;
    }

    public boolean hasModifiedClass() {
        return modified;
    }

    public void visitEnd() {
        MethodVisitor mv;
        String name;
        String desc;
        int access;
        String[] exceptions;
        int args;

        ListIterator<String> nameIter = nativeNames.listIterator();
        ListIterator<String> descIter = nativeDesc.listIterator();
        ListIterator<Integer> accIter = nativeAcc.listIterator();
        ListIterator<String[]> excIter = nativeExc.listIterator();

        if (nameIter.hasNext() && descIter.hasNext() && accIter.hasNext())
            modified = true;

        while (nameIter.hasNext() && descIter.hasNext() && accIter.hasNext()) {
            name = nameIter.next();
            desc = descIter.next();
            access = (accIter.next()).intValue();
            exceptions = excIter.next();
            int maxStack = 0;

            mv = cv.visitMethod(access - ACC_NATIVE, name, desc, null, null);
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
            if ((access & ACC_STATIC) == 0) {
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
            int left = desc.indexOf("(");
            int right = desc.indexOf(")");
            args = 0;
            while (right - left > 1) {
                boolean skipNext = false;
                switch (desc.charAt(left + 1)) {
                    case 'Z':
                    case 'B':
                    case 'C':
                    case 'S':
                    case 'I':
                        mv.visitVarInsn(ILOAD, maxStack);
                        maxStack++;
                        args++;
                        left++;
                        break;
                    case 'J':
                        mv.visitVarInsn(LLOAD, maxStack);
                        maxStack += 2;
                        args++;
                        left++;
                        break;
                    case 'F':
                        mv.visitVarInsn(FLOAD, maxStack);
                        maxStack++;
                        args++;
                        left++;
                        break;
                    case 'D':
                        mv.visitVarInsn(DLOAD, maxStack);
                        maxStack += 2;
                        args++;
                        left++;
                        break;
                    case 'L':
                        mv.visitVarInsn(ALOAD, maxStack);
                        maxStack++;
                        args++;
                        left = desc.indexOf(";", left + 1);
                        break;
                    case '[':
                        mv.visitVarInsn(ALOAD, maxStack);
                        maxStack++;
                        args++;
                        left++;
                        skipNext = true;
                        break;
                    default:
                        System.out.println("This should not happen!" + desc + ", "
                                + left + " " + right + " " + className + name);

                }

                // Skip next arg expression if necessary (i.e., if we had an
                // array type)
                if (skipNext) {
                    while (desc.charAt(left + 1) == '[') {
                        left++;
                        assert (left < right);
                    }
                    if (desc.charAt(left + 1) == 'L') {
                        left = desc.indexOf(";", left + 1);
                    } else {
                        left++;
                    }
                    skipNext = false;
                }
            }

            // Call the prefixed method: INVOKESTATIC for static methods,
            // INVOKESPECIAL in all other cases
            if ((access & ACC_STATIC) == 0) {
                mv.visitMethodInsn(INVOKESPECIAL, className, prefix + name,
                        desc);
            } else {
                mv
                        .visitMethodInsn(INVOKESTATIC, className,
                                prefix + name, desc);
            }

            // Storing the result (when necessary)
            int loadReturn = -1;
            int returnInstr = -1;
            int returnPos = -1;
            int maxLocals = maxStack;

            switch (desc.charAt(right + 1)) {
                case 'V': // void method: nothing to do here
                    returnInstr = RETURN;
                    break;
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                case 'I':
                    returnPos = maxStack;
                    maxLocals++;
                    loadReturn = ILOAD;
                    returnInstr = IRETURN;
                    break;
                case 'J':
                    returnPos = maxStack;
                    maxLocals += 2;
                    loadReturn = LLOAD;
                    returnInstr = LRETURN;
                    break;
                case 'F':
                    returnPos = maxStack;
                    maxLocals++;
                    loadReturn = FLOAD;
                    returnInstr = FRETURN;
                    break;
                case 'D':
                    returnPos = maxStack;
                    maxLocals += 2;
                    loadReturn = DLOAD;
                    returnInstr = DRETURN;
                    break;
                case 'L':
                case '[':
                    returnPos = maxStack;
                    maxLocals++;
                    loadReturn = ALOAD;
                    returnInstr = ARETURN;
                    break;

                default:
                    System.out
                            .println("This should not happen! (unexpected return type"
                                    + desc
                                    + ", "
                                    + left
                                    + " "
                                    + right
                                    + " "
                                    + className + name);

            }
            mv.visitLabel(l1);

            maxStack = maxLocals;
            assert (returnInstr != -1);
            mv.visitInsn(returnInstr);

            mv.visitLabel(l2);
            mv.visitInsn(ATHROW);
            mv.visitMaxs(maxStack + 2, maxLocals + 2);
            mv.visitEnd();
            System.out.println("wrapper method added :" + prefix + name + " "
                    + desc + " (with " + args + " args)");
        }
        cv.visitEnd();

    }

    /*
     * here we have to prefix only the native methods and to remember them
     * together with their descriptions (and whether it is static or not ?)
     */
    public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {

        if ((access & Opcodes.ACC_NATIVE) != 0
        // && (!JarRewriter.exclude.contains(className+"."+name))
        ) {
            if (!name.startsWith(prefix)) {
                System.out.println("native method found: " + name + " "
                        + desc + " " + access + " " + signature + " "
                        + exceptions);
                nativeNames.add(name);
                nativeDesc.add(desc);
                nativeAcc.add(access);
                nativeExc.add(exceptions);
                name = prefix + name;
            } else {
                System.out
                        .println("WARNING: found an already prefixed method");
            }
        }
        // else if (((access & Opcodes.ACC_NATIVE) != 0)
        // && (JarRewriter.exclude.contains(className+"."+name)))
        // {
        // System.out.println("ignored method:" + className + "." + name);
        // }

        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if (mv == null)
            return null;
        return mv;
    }
}
