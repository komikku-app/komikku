package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.LocalSource

val Category.visualName: String
    @Composable
    get() = when {
        id == LocalSource.ID && name == stringResource(MR.strings.local_source) -> stringResource(MR.strings.local_source)
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        id == LocalSource.ID && name == context.stringResource(MR.strings.local_source) -> context.stringResource(MR.strings.local_source)
        isSystemCategory -> context.stringResource(MR.strings.label_default)
        else -> name
    }
