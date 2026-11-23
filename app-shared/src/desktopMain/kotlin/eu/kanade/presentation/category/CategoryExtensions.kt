package eu.kanade.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource

/*
 * KMK: desktop counterpart of app/…/presentation/category/CategoryExtensions.kt (same API and behaviour). The Android
 * file imports both `stringResource` functions; on desktop `tachiyomi.core.common.i18n` also has a context-free
 * `stringResource(StringResource)`, so the composable getter is ambiguous there.
 */

val Category.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isSystemCategory -> context.contextStringResource(MR.strings.label_default)
        else -> name
    }
