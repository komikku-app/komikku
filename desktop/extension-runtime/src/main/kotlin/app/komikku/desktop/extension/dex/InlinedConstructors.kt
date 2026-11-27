package app.komikku.desktop.extension.dex

import app.komikku.desktop.extension.dex.DexTranslator.InlinedConstructor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

/**
 * Adds the constructors the calls re-targeted by [DexTranslator] need. For `new T` constructed by `S.<init>(args)`
 * (R8 inlined the constructors between `T` and its superclass `S`), every class `C` from `T` up to `S`'s direct
 * subclass gets a public synthetic `C.<init>(args, S, Void)` that passes `args` on: to the next class's marked
 * constructor, and from `S`'s direct subclass to `S.<init>(args)`. The JVM requires a `new T` to be initialised by a
 * constructor of `T` and each constructor to call one of its direct superclass, so the chain does on the JVM exactly
 * what the dex code did on ART: run `S.<init>(args)` and nothing else.
 */
internal object InlinedConstructors {

    /**
     * Adds the constructors for [calls] to [classes] (internal name → class file; changed classes are replaced).
     * Returns the calls whose chain leaves the extension's classes before reaching the superclass (`type: reason`).
     */
    fun addTo(classes: MutableMap<String, ByteArray>, calls: Set<InlinedConstructor>): List<String> {
        if (calls.isEmpty()) return emptyList()
        val nodes = HashMap<String, ClassNode>()
        fun node(name: String): ClassNode? = nodes[name] ?: classes[name]?.let { bytes ->
            ClassNode().also { ClassReader(bytes).accept(it, 0) }.also { nodes[name] = it }
        }
        val changed = LinkedHashSet<String>()
        val failed = mutableListOf<String>()
        for (call in calls) {
            val chain = mutableListOf<ClassNode>()
            var current = call.type
            while (current != call.superclass) {
                val clazz = node(current) ?: break
                chain += clazz
                current = clazz.superName ?: break
            }
            if (current != call.superclass) {
                failed += "${call.type}: ${call.superclass}.<init>${call.descriptor} is not reachable through the extension's classes"
                continue
            }
            val marked = call.markedDescriptor
            for ((index, clazz) in chain.withIndex()) {
                if (clazz.methods.any { it.name == "<init>" && it.desc == marked }) continue
                clazz.methods.add(constructor(clazz.superName, marked, call.descriptor, toNext = index < chain.lastIndex))
                changed += clazz.name
            }
        }
        for (name in changed) {
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            nodes.getValue(name).accept(writer)
            classes[name] = writer.toByteArray()
        }
        return failed
    }

    /**
     * `<init>[marked]`: `super.<init>[marked](args, null, null)` when [toNext], else `super.<init>[descriptor](args)`.
     */
    private fun constructor(superName: String, marked: String, descriptor: String, toNext: Boolean): MethodNode {
        val method = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC, "<init>", marked, null, null)
        val code = method.instructions
        code.add(VarInsnNode(Opcodes.ALOAD, 0))
        var slot = 1
        for (argument in Type.getArgumentTypes(descriptor)) {
            code.add(VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot))
            slot += argument.size
        }
        if (toNext) {
            code.add(InsnNode(Opcodes.ACONST_NULL))
            code.add(InsnNode(Opcodes.ACONST_NULL))
            code.add(MethodInsnNode(Opcodes.INVOKESPECIAL, superName, "<init>", marked, false))
        } else {
            code.add(MethodInsnNode(Opcodes.INVOKESPECIAL, superName, "<init>", descriptor, false))
        }
        code.add(InsnNode(Opcodes.RETURN))
        return method
    }
}
