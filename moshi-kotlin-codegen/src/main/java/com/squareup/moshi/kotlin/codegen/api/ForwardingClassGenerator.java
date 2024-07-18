/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.moshi.kotlin.codegen.api;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.V1_8;

/**
 * Generates a class that invokes the constructor of another class.
 *
 * <p>The point here is that the constructor might be synthetic, in which case it can't be called
 * directly from Java source code. Say we want to call the constructor {@code ConstructMe(int,
 * String, long)} with parameters {@code 1, "2", 3L}. If the constructor is synthetic, then Java
 * source code can't just do {@code new ConstructMe(1, "2", 3L)}. So this class allows you to
 * generate a class file, say {@code Forwarder}, that is basically what you would get if you could
 * compile this:
 *
 * <pre>
 * final class Forwarder {
 *   private Forwarder() {}
 *
 *   static ConstructMe of(int a, String b, long c) {
 *     return new ConstructMe(a, b, c);
 *   }
 * }
 * </pre>
 *
 * <p>Because the class file is assembled directly, rather than being produced by the Java compiler,
 * it <i>can</i> call the synthetic constructor. Then regular Java source code can do {@code
 * Forwarder.of(1, "2", 3L)} to call the constructor.
 */
final class ForwardingClassGenerator {
  /**
   * Assembles a class with a static method {@code of} that calls the constructor of another class
   * with the same parameters.
   *
   * <p>It would be simpler if we could just pass in an {@code ExecutableElement} representing the
   * constructor, but if it is synthetic then it won't be visible to the {@code javax.lang.model}
   * APIs. So we have to pass the constructed type and the constructor parameter types separately.
   *
   * @param forwardingClassName the fully-qualified name of the class to generate
   * @param targetClass the internal name of the type whose constructor will be invoked ({@code ConstructMe} in the
   *     example above)
   * @param parameterDescriptor the jvm parameters descriptor of the target class constructor to invoke. Should include the bit fields and default constructor marker.
   * @param creatorSignature the jvm signature (not descriptor) of the to-be-generated "of" creator method. Should include generics information.
   * @return a byte array making up the new class file.
   */
  static byte[] makeConstructorForwarder(
      String forwardingClassName,
      String targetClass,
      String parameterDescriptor,
      String creatorSignature
      ) {

    ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS);
    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER,
        internalName(forwardingClassName),
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource(forwardingClassName, null);

    String internalClassToConstruct = internalName(targetClass);
    String ofMethodDescriptor =  "(" + parameterDescriptor + ")L" + internalClassToConstruct + ";";
    MethodVisitor ofMethodVisitor =
        classWriter.visitMethod(
          ACC_STATIC,
          /* name */ "of",
          /* descriptor */ ofMethodDescriptor,
          /* signature */ creatorSignature,
          null
        );

    // Tell Kotlin we're always returning non-null and avoid a platform type warning.
    AnnotationVisitor av = ofMethodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", true);
    av.visitEnd();

    ofMethodVisitor.visitCode();

    // The remaining instructions are basically what ASMifier generates for a class like the
    // `Forwarder` class in the example above.
    ofMethodVisitor.visitTypeInsn(NEW, internalClassToConstruct);
    ofMethodVisitor.visitInsn(DUP);

    String constructorToCallSignature = "(" + parameterDescriptor + ")V";
    Type[] paramTypes = Type.getArgumentTypes(constructorToCallSignature);
    int local = 0;
    for (Type type : paramTypes) {
      ofMethodVisitor.visitVarInsn(loadInstruction(type), local);
      local += localSize(type);
    }
    ofMethodVisitor.visitMethodInsn(
        INVOKESPECIAL,
        internalClassToConstruct,
        "<init>",
        constructorToCallSignature,
        /* isInterface= */ false);

    ofMethodVisitor.visitInsn(ARETURN);
    ofMethodVisitor.visitMaxs(0, 0);
    ofMethodVisitor.visitEnd();
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  /** The bytecode instruction that copies a parameter of the given type onto the JVM stack. */
  private static int loadInstruction(Type type) {
    switch (type.getSort()) {
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
        return ILOAD;
      case Type.FLOAT:
        return FLOAD;
      case Type.LONG:
        return LLOAD;
      case Type.DOUBLE:
        return DLOAD;
      case Type.ARRAY:
      case Type.OBJECT:
        return ALOAD;
      default:
        throw new IllegalArgumentException("Unexpected type " + type);
    }
  }

  /**
   * The size in the local variable array of a value of the given type. A quirk of the JVM means
   * that long and double variables each take up two consecutive slots in the local variable array.
   * (The first n local variables are the parameters, so we need to know their sizes when iterating
   * over them.)
   */
  private static int localSize(Type type) {
    switch (type.getSort()) {
      case Type.LONG:
      case Type.DOUBLE:
        return 2;
      default:
        return 1;
    }
  }

  private static String internalName(String className) {
    return className.replace('.', '/');
  }

  private ForwardingClassGenerator() {}
}
