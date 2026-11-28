package app.komikku.desktop.compat

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** `COMPAT_REGISTRY.md` lists every class of the compat layer, with a valid kind, and nothing else (ANSWER 010). */
class CompatRegistryTest {

    private val row = Regex("""^\| `([\w.$]+)` \| ([^|]+?) \|""")

    private val registry: Map<String, String> by lazy {
        File("COMPAT_REGISTRY.md").readLines()
            .mapNotNull { line -> row.find(line)?.destructured?.let { (name, kind) -> name to kind } }
            .toMap()
    }

    /** Public top-level classes of the layer (`app.komikku.desktop.compat` is the desktop implementation behind it). */
    private val compatClasses: List<String> by lazy {
        val location = File(UnsupportedOnDesktop::class.java.protectionDomain.codeSource.location.toURI())
        val entries = if (location.isDirectory) {
            location.walkTopDown().filter { it.isFile }.map { it.relativeTo(location).invariantSeparatorsPath }.toList()
        } else {
            ZipFile(location).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        }
        entries.asSequence()
            .filter { it.endsWith(".class") && '$' !in it && !it.startsWith("META-INF/") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .filterNot { it.startsWith("app.komikku.desktop.compat.") }
            .filter { Modifier.isPublic(Class.forName(it, false, javaClass.classLoader).modifiers) }
            .sorted()
            .toList()
    }

    @Test
    fun `every compat class is in the registry`() {
        compatClasses.shouldNotBeEmpty()
        withClue("add these classes to desktop/android-compat/COMPAT_REGISTRY.md") {
            compatClasses.filterNot(registry::containsKey).shouldBeEmpty()
        }
    }

    @Test
    fun `every registry entry is a compat class with a valid kind`() {
        withClue("classes listed in COMPAT_REGISTRY.md that the layer does not have") {
            registry.keys.filterNot { name ->
                runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
            }.shouldBeEmpty()
        }
        withClue("kinds other than 'type only', 'no-op' and 'real behaviour'") {
            registry.filterValues { it !in KINDS }.keys.shouldBeEmpty()
        }
    }

    private companion object {
        val KINDS = setOf("type only", "no-op", "real behaviour")
    }
}
