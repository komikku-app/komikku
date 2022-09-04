package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import eu.davidea.flexibleadapter.FlexibleAdapter

class MigrationProcessAdapter(
    val controller: MigrationListController,
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true)
