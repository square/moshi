package com.squareup.moshi.kotlin.codegen

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

internal class BridgeGenerator(
  val packageName: String,
  val name: String,
  val targetClassName: String,
  val primaryConstructorDesc: String
) {

  companion object {
    const val MARKER = "kotlin/jvm/internal/DefaultConstructorMarker"
    const val BRIDGE_METHOD_NAME = "constructorBridge"
  }

  fun generateClassBytes(): ByteArray {
    val packageJvmName = packageName.replace(".", "/")
    val targetJvmName = "${packageJvmName}/$targetClassName"
    val bridgeClassJvmName = "${packageJvmName}/$name"

    /*
     * Tease out the initial description, we'll synthesize our own starting from it
     *
     * Given something like this and targeting "package/to/TargetClass"
     *   "(Ljava/lang/Object;)V"
     *
     * We'll save the parameters prefix
     *   "(Ljava/lang/Object;"
     *
     * And formulate the following two components
     * 1. Bridge method signature: "(Ljava/lang/Object;I)Lpackage/to/TargetClass;"
     *   - I for the int mask
     *   - Change return type to the target class
     * 2. Target defaults constructor: "(Ljava/lang/Object;ILkotlin/jvm/internal/DefaultConstructorMarker;)V"
     *   - I for the int mask
     *   - kotlin/jvm/internal/DefaultConstructorMarker for the constructor marker arg (always null at runtime)
    */
    val prefix = primaryConstructorDesc.substringBeforeLast(")")
    val bridgeDesc = "${prefix}I)L$targetJvmName;"
    val defaultsConstructorDesc = "${prefix}IL$MARKER;)V"

    val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES).apply {
      visit(Opcodes.V1_6, Opcodes.ACC_FINAL + Opcodes.ACC_SUPER, bridgeClassJvmName, null, "java/lang/Object", null)
    }

    // Private empty default constructor
    cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null).apply {
      visitCode()
      visitVarInsn(Opcodes.ALOAD, 0) // load "this"
      visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
      visitInsn(Opcodes.RETURN)

      // Note that MAXS are computed, but we still have to call this at the end with throwaway values
      visitMaxs(-1, -1)
      visitEnd()
    }

    // Constructor bridge method
    cw.visitMethod(Opcodes.ACC_STATIC, BRIDGE_METHOD_NAME, bridgeDesc, null, null).apply {
      visitCode()
      visitTypeInsn(Opcodes.NEW, targetJvmName)
      visitInsn(Opcodes.DUP)

      // Load parameters onto the stack
      var counter = 0
      for (argType in Type.getArgumentTypes(bridgeDesc)) {
        visitVarInsn(argType.toLoadInstruction(), counter)
        counter += argType.toStackSize()
      }
      // null for the default constructor marker
      visitInsn(Opcodes.ACONST_NULL)
      visitMethodInsn(Opcodes.INVOKESPECIAL, targetJvmName, "<init>", defaultsConstructorDesc, false)
      visitInsn(Opcodes.ARETURN)

      // Note that MAXS are computed, but we still have to call this at the end with throwaway values
      visitMaxs(-1, -1)
      visitEnd()
    }
    cw.visitEnd()

    return cw.toByteArray()
  }

  private fun Type.toLoadInstruction(): Int {
    return when (this) {
      Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.CHAR_TYPE, Type.SHORT_TYPE, Type.INT_TYPE -> Opcodes.ILOAD
      Type.LONG_TYPE -> Opcodes.LLOAD
      Type.FLOAT_TYPE -> Opcodes.FLOAD
      Type.DOUBLE_TYPE -> Opcodes.DLOAD
      else -> Opcodes.ALOAD
    }
  }

  private fun Type.toStackSize(): Int {
    return when (this) {
      Type.LONG_TYPE, Type.DOUBLE_TYPE -> 2
      else -> 1
    }
  }
}
