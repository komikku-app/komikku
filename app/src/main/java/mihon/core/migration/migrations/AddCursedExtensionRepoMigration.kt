package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

class AddCursedExtensionRepoMigration : Migration {
    override val version: Float = 84f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val repository = migrationContext.get<ExtensionStoreRepository>() ?: return@withIOContext false

        // Add Cursed repository
        repository.insert(
            indexUrl = "https://raw.githubusercontent.com/devil6venom/cursed-repo/repo/index.min.json",
        )

        return@withIOContext true
    }
}
