package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

class AddDefaultExtensionRepoMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val repository = migrationContext.get<ExtensionStoreRepository>() ?: return@withIOContext false
        if (repository.getAll().any { it.indexUrl.contains("keiyoushi") }) return@withIOContext false

        // Add Keiyoushi repository
        repository.insert(
            indexUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
        )

        return@withIOContext true
    }
}
