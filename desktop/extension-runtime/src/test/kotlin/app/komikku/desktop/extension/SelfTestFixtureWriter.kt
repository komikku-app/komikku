package app.komikku.desktop.extension

import app.komikku.desktop.extension.testing.FixtureApk
import app.komikku.desktop.extension.testing.FixtureClasses
import app.komikku.desktop.extension.testing.TestSigningKey
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Writes the extension APK of the packaged app's self-test (`desktopApp/src/main/resources/app/komikku/desktop/selftest/
 * selftest-extension.apk`, run by `Komikku --self-test`, M2): one source with dex code, signed with an EC key, and the
 * classes of [FixtureClasses.branching] (methods that need stack map frames, R8-style inlined constructors), which
 * the self-test verifies. Not part of the normal run; to regenerate it:
 *
 * `KOMIKKU_WRITE_SELFTEST_APK=$PWD/desktopApp/src/main/resources/app/komikku/desktop/selftest/selftest-extension.apk
 * ./gradlew :desktop:extension-runtime:test --tests "*.SelfTestFixtureWriter"`
 */
class SelfTestFixtureWriter {

    @TempDir
    lateinit var dir: File

    @Test
    @EnabledIfEnvironmentVariable(named = "KOMIKKU_WRITE_SELFTEST_APK", matches = ".+")
    fun `writes the self-test extension APK`() {
        val target = File(System.getenv("KOMIKKU_WRITE_SELFTEST_APK"))
        val jar = File(dir, "classes.jar")
        FixtureClasses.jar(
            jar,
            mapOf(
                SOURCE_CLASS to FixtureClasses.source(SOURCE_CLASS, id = SOURCE_ID, name = SOURCE_NAME),
            ) + FixtureClasses.branching(PACKAGE_PATH),
        )
        target.parentFile.mkdirs()
        FixtureApk(
            packageName = "eu.kanade.tachiyomi.extension.all.komikkuselftest",
            label = "Tachiyomi: Komikku self-test",
            sourceClass = ".SelfTestSource",
            dex = FixtureClasses.dex(jar, dir),
        ).writeSigned(target, TestSigningKey.ec("Komikku self-test"))
        ExtensionRuntime(File(dir, "extensions")).inspect(target).shouldBeInstanceOf<ExtensionPackageResult.Valid>()
    }

    private companion object {
        const val PACKAGE_PATH = "eu/kanade/tachiyomi/extension/all/komikkuselftest"
        const val SOURCE_CLASS = "$PACKAGE_PATH/SelfTestSource"
        const val SOURCE_ID = 4243L
        const val SOURCE_NAME = "Komikku self-test"
    }
}
