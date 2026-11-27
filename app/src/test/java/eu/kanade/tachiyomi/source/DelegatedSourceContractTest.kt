package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DelegatedSourceContractTest {

    @Test
    fun `delegated sources expose the HttpSource and Context constructor`() {
        AndroidSourceManager.DELEGATED_SOURCES.forEach { delegated ->
            val hasExpectedConstructor = delegated.newSourceClass.constructors.any { constructor ->
                constructor.parameters.size == 2 &&
                    constructor.parameters[0].type.classifier == HttpSource::class &&
                    constructor.parameters[1].type.classifier == Context::class
            }

            hasExpectedConstructor shouldBe true
        }
    }

    @Test
    fun `delegated source entry points are unique`() {
        val qualifiedNames = AndroidSourceManager.DELEGATED_SOURCES.map { it.newSourceClass.qualifiedName }

        qualifiedNames.toSet().size shouldBe qualifiedNames.size
    }
}
