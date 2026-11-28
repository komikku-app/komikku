package app.komikku.desktop.extension.linkcheck

import app.komikku.desktop.extension.testing.FixtureClasses
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File

class LinkCheckerTest {

    @TempDir
    lateinit var dir: File

    private val user = "eu/kanade/tachiyomi/extension/en/linkfixture/LinkFixture"
    private val helper = "eu/kanade/tachiyomi/extension/en/linkfixture/Helper"

    /** A class whose `run()` references present and missing host symbols and a class of its own jar. */
    private fun fixture(): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, user, null, "java/lang/Object", arrayOf("eu/kanade/tachiyomi/source/Source"))
        writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null).apply {
            visitCode()
            // Present in the compat layer
            visitInsn(Opcodes.ACONST_NULL)
            visitInsn(Opcodes.ICONST_0)
            visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "encodeToString", "([BI)Ljava/lang/String;", false)
            visitInsn(Opcodes.POP)
            visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build\$VERSION", "SDK_INT", "I")
            visitInsn(Opcodes.POP)
            // Interface member of the host source API
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(Opcodes.INVOKEINTERFACE, "eu/kanade/tachiyomi/source/Source", "getLang", "()Ljava/lang/String;", true)
            visitInsn(Opcodes.POP)
            // Inherited member through the jar's own class (LinkFixture implements Source)
            visitInsn(Opcodes.ACONST_NULL)
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, user, "getName", "()Ljava/lang/String;", false)
            visitInsn(Opcodes.POP)
            // Missing class
            visitMethodInsn(Opcodes.INVOKESTATIC, "android/hardware/Camera", "open", "()Landroid/hardware/Camera\$Parameters;", false)
            visitInsn(Opcodes.POP)
            // Missing method on an existing class
            visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "doesNotExist", "()V", false)
            // Missing field on an existing class
            visitFieldInsn(Opcodes.GETSTATIC, "android/os/Build", "NO_SUCH_FIELD", "Ljava/lang/String;")
            visitInsn(Opcodes.POP)
            // A class of the extension itself
            visitMethodInsn(Opcodes.INVOKESTATIC, helper, "touch", "()V", false)
            // JDK
            visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false)
            visitInsn(Opcodes.POP2)
            visitInsn(Opcodes.RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    @Test
    fun `reports symbols the host lacks`() {
        val jar = File(dir, "fixture.jar")
        FixtureClasses.jar(
            jar,
            mapOf(
                user to fixture(),
                helper to FixtureClasses.classCalling(helper, "java/lang/Thread", "yield", "()V"),
            ),
        )

        val report = LinkChecker().check(jar)

        report.checkedClasses shouldBe 2
        report.missingClasses.keys shouldContainExactly setOf("android/hardware/Camera", "android/hardware/Camera\$Parameters")
        report.missingClasses.getValue("android/hardware/Camera") shouldContainExactly setOf(user)
        report.missingMethods.keys shouldContainExactly setOf(MemberRef("android/util/Base64", "doesNotExist", "()V"))
        report.missingFields.keys shouldContainExactly setOf(MemberRef("android/os/Build", "NO_SUCH_FIELD", "Ljava/lang/String;"))
        report.isClean shouldBe false
        report.format("fixture.jar") shouldContain
            "android.hardware.Camera  <- eu.kanade.tachiyomi.extension.en.linkfixture.LinkFixture"
    }

    @Test
    fun `a wrapper forwarding a method of a newer library is not a missing method`() {
        val chain = "okhttp3/Interceptor\$Chain"
        val wrapper = "eu/kanade/tachiyomi/extension/en/linkfixture/ChainWrapper"
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, wrapper, null, "java/lang/Object", arrayOf(chain))
        writer.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, "chain", "L$chain;", null, null).visitEnd()
        // getDns(): forwards to the wrapped chain (OkHttp 5.3 has no Chain.getDns); misuse(): calls it itself
        for (method in listOf("getDns", "misuse")) {
            writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()Lokhttp3/Dns;", null, null).apply {
                visitCode()
                visitVarInsn(Opcodes.ALOAD, 0)
                visitFieldInsn(Opcodes.GETFIELD, wrapper, "chain", "L$chain;")
                visitMethodInsn(Opcodes.INVOKEINTERFACE, chain, "getDns", "()Lokhttp3/Dns;", true)
                visitInsn(Opcodes.ARETURN)
                visitMaxs(0, 0)
                visitEnd()
            }
        }
        writer.visitEnd()
        val jar = File(dir, "wrapper.jar")
        FixtureClasses.jar(jar, mapOf(wrapper to writer.toByteArray()))

        val report = LinkChecker().check(jar)

        val getDns = MemberRef(chain, "getDns", "()Lokhttp3/Dns;")
        report.forwardedMethods.keys shouldContainExactly setOf(getDns)
        report.missingMethods.keys shouldContainExactly setOf(getDns)
    }

    @Test
    fun `lists the host classes it is asked to track`() {
        val jar = File(dir, "fixture.jar")
        FixtureClasses.jar(jar, mapOf(user to fixture()))

        val report = LinkChecker(trackHostClass = { it.startsWith("android/") }).check(jar)

        report.usedHostClasses shouldContainExactly setOf("android/os/Build", "android/os/Build\$VERSION", "android/util/Base64")
    }

    @Test
    fun `clean jar`() {
        val jar = File(dir, "clean.jar")
        FixtureClasses.jar(jar, mapOf(helper to FixtureClasses.classCalling(helper, "java/lang/Thread", "yield", "()V")))

        val report = LinkChecker().check(jar)

        report.isClean shouldBe true
        report.format("clean.jar") shouldContain "All references resolve."
    }
}
