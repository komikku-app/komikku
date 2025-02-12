package eu.kanade.tachiyomi.ui.errors

import androidx.compose.runtime.Immutable
import tachiyomi.domain.error.model.Error

@Immutable
data class ErrorItem(
    val error: Error,
    val selected: Boolean,
)
