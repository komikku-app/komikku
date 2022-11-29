package eu.kanade.tachiyomi.ui.browse.migration.advanced.design

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationProcedureConfig

class PreMigrationController(bundle: Bundle? = null) : BasicFullComposeController(bundle) {

    constructor(mangaIds: List<Long>) : this(
        bundleOf(
            MANGA_IDS_EXTRA to mangaIds.toLongArray(),
        ),
    )

    private val config: LongArray = args.getLongArray(MANGA_IDS_EXTRA) ?: LongArray(0)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = PreMigrationScreen(config.toList()))
    }

    companion object {
        private const val MANGA_IDS_EXTRA = "manga_ids"

        fun navigateToMigration(skipPre: Boolean, router: Router, mangaIds: List<Long>) {
            router.pushController(
                if (skipPre) {
                    MigrationListController(
                        MigrationProcedureConfig(mangaIds, null),
                    )
                } else {
                    PreMigrationController(mangaIds)
                }.withFadeTransaction().tag(MigrationListController.TAG),
            )
        }
    }
}
