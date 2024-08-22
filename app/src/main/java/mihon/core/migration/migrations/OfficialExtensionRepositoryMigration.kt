package mihon.core.migration.migrations

import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.interactor.CreateExtensionRepo
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class OfficialExtensionRepositoryMigration : Migration {
    override val version: Float = 70f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val extensionRepositoryRepository =
            migrationContext.get<ExtensionRepoRepository>() ?: return@withIOContext false
        try {
            extensionRepositoryRepository.upsertRepo(
                baseUrl = CreateExtensionRepo.OFFICIAL_REPO_BASE_URL,
                name = "Komikku Official",
                shortName = "Komikku",
                website = "https://komikku-app.github.io",
                signingKeyFingerprint = CreateExtensionRepo.OFFICIAL_REPO_SIGNATURE,
            )
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.ERROR, e) { "Error inserting Official Extension Repo" }
            return@withIOContext false
        }
        return@withIOContext true
    }
}
