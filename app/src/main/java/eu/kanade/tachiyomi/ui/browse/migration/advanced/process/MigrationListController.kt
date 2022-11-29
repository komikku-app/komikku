package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.util.system.getSerializableCompat

class MigrationListController(bundle: Bundle? = null) :
    BasicFullComposeController(bundle) {

    constructor(config: MigrationProcedureConfig) : this(
        bundleOf(
            CONFIG_EXTRA to config,
        ),
    )

    val config = args.getSerializableCompat<MigrationProcedureConfig>(CONFIG_EXTRA)!!

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MigrationListScreen(config))
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"
        const val TAG = "migration_list"
    }
}
