package app.komikku.desktop.extension.testing

import com.android.dx.command.dexer.DxContext
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import com.android.dx.command.dexer.Main as Dx

/**
 * Generates tiny extension classes with ASM (Java 8 class files, which AOSP `dx` can convert), jars
 * and dex files for the extension pipeline tests.
 */
object FixtureClasses {

    private const val SOURCE = "eu/kanade/tachiyomi/source/Source"

    /**
     * A class implementing `eu.kanade.tachiyomi.source.Source` with constant `id`, `name`, `lang`
     * and `supportsLatest = false`. The suspend members are left abstract (never called by the
     * loader); it only needs to be instantiated and identified.
     */
    fun source(internalName: String, id: Long, name: String, lang: String = "en"): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", arrayOf(SOURCE))
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "getId", "()J", null, null).apply {
            visitCode()
            visitLdcInsn(id)
            visitInsn(Opcodes.LRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        mapOf("getName" to name, "getLang" to lang).forEach { (method, value) ->
            writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()Ljava/lang/String;", null, null).apply {
                visitCode()
                visitLdcInsn(value)
                visitInsn(Opcodes.ARETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC, "getSupportsLatest", "()Z", null, null).apply {
            visitCode()
            visitInsn(Opcodes.ICONST_0)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    /**
     * An empty public class [internalName] (with a no-argument constructor) whose static `touch()`
     * calls `owner.method` with descriptor [descriptor] statically: used to reference a missing
     * symbol for the link checker.
     */
    fun classCalling(internalName: String, owner: String, method: String, descriptor: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, internalName, null, "java/lang/Object", null)
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "touch", "()V", null, null).apply {
            visitCode()
            visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, descriptor, false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    /** An empty public class [internalName] with a no-argument constructor. */
    fun emptyClass(internalName: String, superName: String = "java/lang/Object"): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, internalName, null, superName, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    /**
     * Classes under [packagePath] whose methods need stack map frames, like real (R8-built) extensions: `Base`, its
     * subclasses `First` / `Second` and `Branches`, whose static `fromHost(Z)I` merges an `Integer` and a `Long` and
     * calls `Number.intValue()` on the result (common superclass on the host class path) and `fromExtension(Z)I`
     * merges a `First` and a `Second` and calls `Base.value()` (common superclass among the extension's classes).
     * Also `Singleton extends Base`, built like a Kotlin `object` after R8 ([inlinedConstructorSingleton]). Written
     * without frames (dx drops them anyway); dex2jar's output of them fails verification unless frames are computed with
     * the right common superclasses and the inlined constructor is replaced.
     */
    fun branching(packagePath: String): Map<String, ByteArray> {
        val base = "$packagePath/Base"
        val first = "$packagePath/First"
        val second = "$packagePath/Second"
        val branches = "$packagePath/Branches"
        val baseClass = ClassWriter(ClassWriter.COMPUTE_MAXS).apply {
            visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, base, null, "java/lang/Object", null)
            constructor("java/lang/Object")
            visitMethod(Opcodes.ACC_PUBLIC, "value", "()I", null, null).apply {
                visitCode()
                visitInsn(Opcodes.ICONST_3)
                visitInsn(Opcodes.IRETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
            visitEnd()
        }.toByteArray()
        val branchesClass = ClassWriter(ClassWriter.COMPUTE_MAXS).apply {
            visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER, branches, null, "java/lang/Object", null)
            constructor("java/lang/Object")
            branchingMethod("fromHost", "java/lang/Number", "intValue") { choice ->
                if (choice) {
                    visitInsn(Opcodes.ICONST_1)
                    visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
                } else {
                    visitInsn(Opcodes.LCONST_1)
                    visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false)
                }
            }
            branchingMethod("fromExtension", base, "value") { choice ->
                val type = if (choice) first else second
                visitTypeInsn(Opcodes.NEW, type)
                visitInsn(Opcodes.DUP)
                visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false)
            }
            visitEnd()
        }.toByteArray()
        return mapOf(
            base to baseClass,
            first to emptyClass(first, base),
            second to emptyClass(second, base),
            branches to branchesClass,
            "$packagePath/Singleton" to inlinedConstructorSingleton("$packagePath/Singleton", base),
        )
    }

    /**
     * A Kotlin `object` after R8 inlined its constructors: `class <name> extends <superName>`, whose static initialiser
     * creates `INSTANCE` with `new <name>` constructed by `Object.<init>` directly (invalid on the JVM, accepted by
     * ART and by dx), and whose static `value()I` returns `INSTANCE.value()` (`superName` declares `value()I`).
     */
    private fun inlinedConstructorSingleton(name: String, superName: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER, name, null, superName, null)
        writer.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "INSTANCE", "L$name;", null, null).visitEnd()
        writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitCode()
            visitTypeInsn(Opcodes.NEW, name)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitFieldInsn(Opcodes.PUTSTATIC, name, "INSTANCE", "L$name;")
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "instanceValue", "()I", null, null).apply {
            visitCode()
            visitFieldInsn(Opcodes.GETSTATIC, name, "INSTANCE", "L$name;")
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, superName, "value", "()I", false)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    /** `static int <name>(boolean)`: `(flag ? <push true> : <push false>).<call>()` through a local variable. */
    private fun ClassWriter.branchingMethod(
        name: String,
        owner: String,
        call: String,
        push: MethodVisitor.(Boolean) -> Unit,
    ) {
        visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, "(Z)I", null, null).apply {
            visitCode()
            val otherwise = Label()
            val merge = Label()
            visitVarInsn(Opcodes.ILOAD, 0)
            visitJumpInsn(Opcodes.IFEQ, otherwise)
            push(true)
            visitVarInsn(Opcodes.ASTORE, 1)
            visitJumpInsn(Opcodes.GOTO, merge)
            visitLabel(otherwise)
            push(false)
            visitVarInsn(Opcodes.ASTORE, 1)
            visitLabel(merge)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, call, "()I", false)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun ClassWriter.constructor(superName: String) {
        visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    /** Writes a jar with [classes] (internal name → class file bytes). */
    fun jar(file: File, classes: Map<String, ByteArray>) {
        JarOutputStream(file.outputStream()).use { jar ->
            classes.forEach { (name, bytes) ->
                jar.putNextEntry(JarEntry("$name.class"))
                jar.write(bytes)
                jar.closeEntry()
            }
        }
    }

    /** Converts the class files of [jar] to a `classes.dex` with AOSP dx (bundled by dex2jar). */
    fun dex(jar: File, workDir: File): ByteArray {
        val output = File(workDir, "classes.dex")
        val arguments = Dx.Arguments(DxContext()).apply {
            parseFlags(arrayOf("--output=${output.absolutePath}", "--min-sdk-version=26", jar.absolutePath))
            fileNames = arrayOf(jar.absolutePath)
            makeOptionsObjects()
        }
        val exitCode = Dx(DxContext()).runDx(arguments)
        check(exitCode == 0 && output.isFile) { "dx failed with exit code $exitCode" }
        return output.readBytes()
    }
}
