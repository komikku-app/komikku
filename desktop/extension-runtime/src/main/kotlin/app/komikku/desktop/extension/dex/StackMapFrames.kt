package app.komikku.desktop.extension.dex

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

/**
 * Adds the stack map frames dex2jar leaves out. dex2jar writes Java 8 class files (version 52) without a
 * `StackMapTable`, which the JVM's verifier rejects for every method with a branch ("Expecting a stackmap frame at
 * branch target"); dex2jar's own `computeFrames` option resolves common superclasses only among the classes of the
 * dex and takes `java/lang/Object` for everything else, which gives frames the verifier rejects as soon as a merged
 * value is used as a host type (an extension's `HttpSource`, `Response`, …).
 *
 * Every class is re-written by ASM with [ClassWriter.COMPUTE_FRAMES]. Common superclasses are resolved
 * from the class files themselves, never by loading a class: first among the jar's classes (an extension's class
 * loader is child-first), then on [classPath] (the compat layer, source-api, the network stack and the JDK).
 */
internal class StackMapFrames(private val jarClasses: Map<String, ClassInfo>, private val classPath: ClassLoader) {

    data class ClassInfo(val superName: String?, val isInterface: Boolean)

    private val hostClasses = HashMap<String, ClassInfo?>()

    /** [bytes] with frames computed from scratch. */
    fun rewrite(bytes: ByteArray): ByteArray {
        val writer = object : ClassWriter(COMPUTE_FRAMES) {
            override fun getCommonSuperClass(type1: String, type2: String): String = commonSuperClass(type1, type2)
        }
        ClassReader(bytes).accept(writer, ClassReader.SKIP_FRAMES)
        return writer.toByteArray()
    }

    /**
     * The nearest class both [type1] and [type2] extend. `java/lang/Object` when either is an interface (the verifier
     * treats interface types as `Object`) or unknown (a class missing from the host; such a class fails to link anyway).
     */
    fun commonSuperClass(type1: String, type2: String): String {
        if (type1 == type2) return type1
        val info1 = info(type1) ?: return OBJECT
        val info2 = info(type2) ?: return OBJECT
        if (info1.isInterface || info2.isInterface) return OBJECT
        val supers1 = generateSequence(type1) { info(it)?.superName }.toSet()
        return generateSequence(type2) { info(it)?.superName }.firstOrNull { it in supers1 } ?: OBJECT
    }

    private fun info(internalName: String): ClassInfo? =
        jarClasses[internalName] ?: hostClasses.getOrPut(internalName) { readHostClass(internalName) }

    private fun readHostClass(internalName: String): ClassInfo? = try {
        classPath.getResourceAsStream("$internalName.class")?.use { stream -> ClassReader(stream).toInfo() }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val OBJECT = "java/lang/Object"

        private fun ClassReader.toInfo() = ClassInfo(superName, access and Opcodes.ACC_INTERFACE != 0)

        /**
         * Computes the frames of every class in [classes] (internal name → class file; replaced in place). Returns
         * the classes whose frames could not be computed (`name: reason`); they are left unchanged.
         */
        fun addTo(classes: MutableMap<String, ByteArray>, classPath: ClassLoader): List<String> {
            val infos = classes.mapValues { (_, bytes) -> ClassReader(bytes).toInfo() }
            val frames = StackMapFrames(infos, classPath)
            val failed = mutableListOf<String>()
            for (name in classes.keys.toList()) {
                try {
                    classes[name] = frames.rewrite(classes.getValue(name))
                } catch (e: Exception) {
                    failed += "$name: $e"
                }
            }
            return failed
        }
    }
}
