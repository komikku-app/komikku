package app.komikku.desktop.extension.testing

import com.android.dx.command.dexer.DxContext
import org.objectweb.asm.ClassWriter
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
