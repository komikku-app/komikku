package app.komikku.desktop.extension.linkcheck

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.zip.ZipFile

/** A field or method reference: `owner.name descriptor` (JVM internal names). */
data class MemberRef(val owner: String, val name: String, val descriptor: String) : Comparable<MemberRef> {
    override fun compareTo(other: MemberRef): Int = toString().compareTo(other.toString())
    override fun toString(): String = "$owner.$name$descriptor"
}

/**
 * What [LinkChecker] found: references from the checked classes to classes / methods / fields that
 * the host class path does not have, each with the checked classes that reference it.
 *
 * [forwardedMethods] are missing methods only called by a method of the same signature in a class implementing their
 * owner: a delegating wrapper compiled against a newer library (Temple Scan's `Interceptor.Chain` wrapper forwards the
 * chain methods of a newer OkHttp). The host's library never calls them, so they are not counted as missing.
 */
data class LinkCheckReport(
    val checkedClasses: Int,
    val missingClasses: Map<String, Set<String>>,
    val missingMethods: Map<MemberRef, Set<String>>,
    val missingFields: Map<MemberRef, Set<String>>,
    val forwardedMethods: Map<MemberRef, Set<String>> = emptyMap(),
) {
    val isClean: Boolean get() = missingClasses.isEmpty() && missingMethods.isEmpty() && missingFields.isEmpty()

    /** Human-readable report, grouped by kind and sorted. */
    fun format(title: String): String = buildString {
        appendLine("== $title: $checkedClasses classes checked")
        if (isClean) {
            appendLine("All references resolve.")
            return@buildString
        }
        fun section(name: String, entries: Map<out Comparable<*>, Set<String>>) {
            if (entries.isEmpty()) return
            appendLine("Missing $name (${entries.size}):")
            entries.entries.sortedBy { it.key.toString() }.forEach { (ref, users) ->
                val shown = users.sorted().take(MAX_USERS_SHOWN)
                val more = if (users.size > shown.size) ", ... (+${users.size - shown.size})" else ""
                appendLine("  ${ref.toString().replace('/', '.')}  <- ${shown.joinToString { it.replace('/', '.') }}$more")
            }
        }
        section("classes", missingClasses)
        section("methods", missingMethods)
        section("fields", missingFields)
    }

    private companion object {
        const val MAX_USERS_SHOWN = 3
    }
}

/**
 * Link check of extension code against the desktop host (step 5.3): reads every class of a jar
 * (converted from an extension APK) with ASM and resolves each referenced class, method and field
 * that the jar does not define itself against [hostClassLoader] — on desktop, the Android compat
 * layer, `source-api`, the network stack and the JDK. Missing symbols are what the compat layer
 * (`:desktop:android-compat`) still lacks for that extension, e.g. `android.graphics.BitmapFactory`.
 *
 * Resolution follows the JVM: methods and fields are looked up in the owner, its superclasses and
 * all superinterfaces. Host classes are read as class files (never loaded or initialized).
 * Signature-polymorphic `MethodHandle` / `VarHandle` calls and array members are skipped.
 */
class LinkChecker(
    private val hostClassLoader: ClassLoader = LinkChecker::class.java.classLoader,
) {

    private class HostClass(
        val superName: String?,
        val interfaces: List<String>,
        val methods: Set<String>,
        val fields: Set<String>,
    )

    private val hostClasses = HashMap<String, HostClass?>()

    fun check(jar: File): LinkCheckReport {
        val classes = ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                .map { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }
        return check(classes)
    }

    /** Checks class files ([classFiles] = their bytes). */
    fun check(classFiles: List<ByteArray>): LinkCheckReport {
        val readers = classFiles.map(::ClassReader)
        val local = readers.associateBy { it.className }
        val localMembers = readers.associate { reader ->
            reader.className to readMembers(reader)
        }

        val missingClasses = sortedMapOf<String, MutableSet<String>>()
        val missingMethods = sortedMapOf<MemberRef, MutableSet<String>>()
        val missingFields = sortedMapOf<MemberRef, MutableSet<String>>()
        val forwardedMethods = sortedMapOf<MemberRef, MutableSet<String>>()

        fun classExists(name: String): Boolean = name in local || host(name) != null

        fun checkClass(name: String?, user: String) {
            val element = name?.let(::elementClass) ?: return
            if (!classExists(element)) missingClasses.getOrPut(element) { sortedSetOf() } += user
        }

        fun checkDescriptor(descriptor: String, user: String) {
            val type = Type.getType(descriptor)
            if (type.sort == Type.METHOD) {
                type.argumentTypes.forEach { checkClass(it.descriptor.typeName(), user) }
                checkClass(type.returnType.descriptor.typeName(), user)
            } else {
                checkClass(descriptor.typeName(), user)
            }
        }

        fun checkMember(owner: String, name: String, descriptor: String, isMethod: Boolean, user: String, caller: String? = null) {
            if (owner.startsWith("[")) {
                checkDescriptor(owner, user)
                return
            }
            if (isMethod && owner in SIGNATURE_POLYMORPHIC_OWNERS) return
            checkDescriptor(descriptor, user)
            if (!classExists(owner)) {
                missingClasses.getOrPut(owner) { sortedSetOf() } += user
                return
            }
            val found = resolve(owner, local, localMembers) { members -> (if (isMethod) members.first else members.second).contains(name + descriptor) }
            if (!found) {
                val forwarded = isMethod && caller == name + descriptor && user != owner && isSubtype(user, owner, local)
                val target = if (forwarded) {
                    forwardedMethods
                } else if (isMethod) {
                    missingMethods
                } else {
                    missingFields
                }
                target.getOrPut(MemberRef(owner, name, descriptor)) { sortedSetOf() } += user
            }
        }

        readers.forEach { reader ->
            val user = reader.className
            reader.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visit(
                        version: Int,
                        access: Int,
                        name: String,
                        signature: String?,
                        superName: String?,
                        interfaces: Array<out String>?,
                    ) {
                        checkClass(superName, user)
                        interfaces?.forEach { checkClass(it, user) }
                    }

                    override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                        checkDescriptor(descriptor, user)
                        return null
                    }

                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor {
                        checkDescriptor(descriptor, user)
                        exceptions?.forEach { checkClass(it, user) }
                        val caller = name + descriptor
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitTypeInsn(opcode: Int, type: String) = checkClass(type, user)

                            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) =
                                checkMember(owner, name, descriptor, isMethod = false, user = user)

                            override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) =
                                checkMember(owner, name, descriptor, isMethod = true, user = user, caller = caller)

                            override fun visitInvokeDynamicInsn(name: String, descriptor: String, bsm: Handle, vararg bsmArgs: Any?) {
                                checkDescriptor(descriptor, user)
                                (listOf(bsm) + bsmArgs.filterIsInstance<Handle>()).forEach { handle ->
                                    checkMember(handle.owner, handle.name, handle.desc, isMethod = handle.tag >= Opcodes.H_INVOKEVIRTUAL, user = user)
                                }
                            }

                            override fun visitLdcInsn(value: Any?) {
                                when (value) {
                                    is Type -> checkDescriptor(value.descriptor, user)
                                    is Handle -> checkMember(value.owner, value.name, value.desc, isMethod = value.tag >= Opcodes.H_INVOKEVIRTUAL, user = user)
                                }
                            }

                            override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = checkDescriptor(descriptor, user)

                            override fun visitTryCatchBlock(
                                start: org.objectweb.asm.Label?,
                                end: org.objectweb.asm.Label?,
                                handler: org.objectweb.asm.Label?,
                                type: String?,
                            ) = checkClass(type, user)
                        }
                    }
                },
                ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
        }

        return LinkCheckReport(readers.size, missingClasses, missingMethods, missingFields, forwardedMethods)
    }

    /** Whether [name] is [type] or extends / implements it (local classes first, then host). */
    private fun isSubtype(name: String, type: String, local: Map<String, ClassReader>): Boolean {
        val queue = ArrayDeque(listOf(name))
        val seen = HashSet<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == type) return true
            if (!seen.add(current)) continue
            val reader = local[current]
            if (reader != null) {
                reader.superName?.let(queue::add)
                queue.addAll(reader.interfaces)
            } else {
                val hostClass = host(current) ?: continue
                hostClass.superName?.let(queue::add)
                queue.addAll(hostClass.interfaces)
            }
        }
        return false
    }

    /** Searches [owner], its superclasses and superinterfaces (local first, then host) for a member. */
    private fun resolve(
        owner: String,
        local: Map<String, ClassReader>,
        localMembers: Map<String, Pair<Set<String>, Set<String>>>,
        matches: (Pair<Set<String>, Set<String>>) -> Boolean,
    ): Boolean {
        val queue = ArrayDeque(listOf(owner))
        val seen = HashSet<String>()
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!seen.add(name)) continue
            val localReader = local[name]
            if (localReader != null) {
                if (matches(localMembers.getValue(name))) return true
                localReader.superName?.let(queue::add)
                queue.addAll(localReader.interfaces)
                continue
            }
            val hostClass = host(name) ?: continue
            if (matches(hostClass.methods to hostClass.fields)) return true
            hostClass.superName?.let(queue::add)
            queue.addAll(hostClass.interfaces)
        }
        return false
    }

    private fun host(name: String): HostClass? = hostClasses.getOrPut(name) {
        val bytes = hostClassLoader.getResourceAsStream("$name.class")?.use { it.readBytes() }
            ?: ClassLoader.getPlatformClassLoader().getResourceAsStream("$name.class")?.use { it.readBytes() }
            ?: return@getOrPut null
        val reader = ClassReader(bytes)
        val (methods, fields) = readMembers(reader)
        HostClass(reader.superName, reader.interfaces.toList(), methods, fields)
    }

    private fun readMembers(reader: ClassReader): Pair<Set<String>, Set<String>> {
        val methods = HashSet<String>()
        val fields = HashSet<String>()
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    methods += name + descriptor
                    return null
                }

                override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    fields += name + descriptor
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return methods to fields
    }

    private companion object {
        val SIGNATURE_POLYMORPHIC_OWNERS = setOf("java/lang/invoke/MethodHandle", "java/lang/invoke/VarHandle")

        /** Internal class name of an object / array type descriptor, `null` for primitives. */
        fun String.typeName(): String? {
            val type = Type.getType(this)
            return when (type.sort) {
                Type.OBJECT -> type.internalName
                Type.ARRAY -> type.elementType.takeIf { it.sort == Type.OBJECT }?.internalName
                else -> null
            }
        }

        /** Element class of an internal name or array descriptor (`[Lfoo/Bar;` → `foo/Bar`). */
        fun elementClass(name: String): String? = if (name.startsWith("[")) name.typeName() else name
    }
}
