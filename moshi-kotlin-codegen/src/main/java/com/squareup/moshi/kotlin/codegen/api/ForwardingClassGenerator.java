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

import com.google.common.collect.ImmutableList;
import com.google.devtools.ksp.symbol.*;
import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.ksp.KsClassDeclarationsKt;
import kotlin.NotImplementedError;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static com.squareup.kotlinpoet.TypeNames.DOUBLE;
import static com.squareup.kotlinpoet.TypeNames.FLOAT;
import static com.squareup.kotlinpoet.TypeNames.LONG;
import static com.squareup.kotlinpoet.TypeNames.*;
import static java.util.stream.Collectors.joining;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;

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
   * @param classToConstruct the type whose constructor will be invoked ({@code ConstructMe} in the
   *     example above)
   * @param constructorParameters the erased types of the constructor parameters, which will also be
   *     the types of the generated {@code of} method. We require the types to be erased so as not
   *     to require an instance of the {@code Types} interface to erase them here. Having to deal
   *     with generics would complicate things unnecessarily.
   * @return a byte array making up the new class file
   */
  static byte[] makeConstructorForwarder(
      String forwardingClassName,
      KSClassDeclaration classToConstruct,
      ImmutableList<KSType> constructorParameters) {

    ClassWriter classWriter = new ClassWriter(COMPUTE_MAXS);
    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER,
        internalName(forwardingClassName),
        null,
        "java/lang/Object",
        null);
    classWriter.visitSource(forwardingClassName, null);

    // Generate the `of` method.
    // TODO(emcmanus): cleaner generics. If we're constructing Foo<T extends Number> then we should
    // generate a generic signature for the `of` method, as if the Java declaration were this:
    //   static <T extends Number> Foo<T> of(...)
    // Currently we just generate:
    //   static Foo of(...)
    // which returns the raw Foo type.
    String parameterSignature =
        constructorParameters.stream()
            .map(ForwardingClassGenerator::signatureEncoding)
            .collect(joining(""));
    String internalClassToConstruct = internalName(KsClassDeclarationsKt.toClassName(classToConstruct));
    String ofMethodSignature = "(" + parameterSignature + ")L" + internalClassToConstruct + ";";
    MethodVisitor ofMethodVisitor =
        classWriter.visitMethod(ACC_STATIC, "of", ofMethodSignature, null, null);
    ofMethodVisitor.visitCode();

    // The remaining instructions are basically what ASMifier generates for a class like the
    // `Forwarder` class in the example above.
    ofMethodVisitor.visitTypeInsn(NEW, internalClassToConstruct);
    ofMethodVisitor.visitInsn(DUP);

    int local = 0;
    for (KSType type : constructorParameters) {
      ofMethodVisitor.visitVarInsn(loadInstruction(type), local);
      local += localSize(type);
    }
    String constructorToCallSignature = "(" + parameterSignature + ")V";
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
  private static int loadInstruction(KSType type) {
    if (type.isMarkedNullable()) {
      // Always using a boxed type
      return ALOAD;
    }
    ClassName rawType = rawType(type);
    if (rawType.equals(ARRAY)) {
      return ALOAD;
    } else if (rawType.equals(LONG)) {
      return LLOAD;
    } else if (rawType.equals(FLOAT)) {
      return FLOAD;
    } else if (rawType.equals(DOUBLE)) {
      return DLOAD;
    } else if (rawType.equals(BYTE)) {
      // These are all represented as int local variables.
      return ILOAD;
    } else if (rawType.equals(SHORT)) {
      return ILOAD;
    } else if (rawType.equals(INT)) {
      return ILOAD;
    } else if (rawType.equals(CHAR)) {
      return ILOAD;
    } else if (rawType.equals(BOOLEAN)) {
      return ILOAD;
    } else {
      // Declared type
      return ALOAD;
    }
  }

  /**
   * The size in the local variable array of a value of the given type. A quirk of the JVM means
   * that long and double variables each take up two consecutive slots in the local variable array.
   * (The first n local variables are the parameters, so we need to know their sizes when iterating
   * over them.)
   */
  private static int localSize(KSType type) {
    // Nullable always uses the boxed type
    if (type.isMarkedNullable()) return 1;
    ClassName rawType = rawType(type);
    if (rawType.equals(LONG) || rawType.equals(DOUBLE)) {
      return 2;
    } else {
      return 1;
    }
  }

  private static String internalName(String className) {
    return className.replace('.', '/');
  }

  /**
   * Given a class like {@code foo.bar.Outer.Inner}, produces a string like {@code
   * "foo/bar/Outer$Inner"}, which is the way the class is referenced in the JVM.
   */
  private static String internalName(ClassName className) {
    return className.reflectionName();
  }

  private static ClassName rawType(KSType type) {
    // TODO handle other KSTypes
    return KsClassDeclarationsKt.toClassName((KSClassDeclaration) type.getDeclaration());
  }

  private static String signatureEncoding(KSType type) {
    KSDeclaration declaration = type.getDeclaration();
    if (declaration instanceof KSClassDeclaration) {
      ClassName rawType = KsClassDeclarationsKt.toClassName((KSClassDeclaration) declaration);
      if (rawType.equals(ARRAY)) {
        KSType componentType = type.getArguments().get(0).getType().resolve();
        return "[" + signatureEncoding(componentType);
      } else if (rawType.equals(BYTE)) {
        return "B";
      } else if (rawType.equals(SHORT)) {
        return "S";
      } else if (rawType.equals(INT)) {
        return "I";
      } else if (rawType.equals(LONG)) {
        return "J";
      } else if (rawType.equals(FLOAT)) {
        return "F";
      } else if (rawType.equals(DOUBLE)) {
        return "D";
      } else if (rawType.equals(CHAR)) {
        return "C";
      } else if (rawType.equals(BOOLEAN)) {
        return "Z";
      } else {
        // Declared type
        return "L" + internalName(rawType) + ";";
      }
    } else if (declaration instanceof KSTypeAlias) {
      // TODO
      throw new NotImplementedError("Type alias not supported");
    } else if (declaration instanceof KSTypeParameter) {
      // TODO
      throw new NotImplementedError("Type parameter not supported");
    } else {
      throw new IllegalArgumentException("Unexpected type " + type);
    }
  }

  private ForwardingClassGenerator() {}
}
