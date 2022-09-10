package eu.kanade.presentation.category

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesPresenter
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem

@Stable
interface BiometricTimesState {
    val isLoading: Boolean
    var dialog: BiometricTimesPresenter.Dialog?
    val timeRanges: List<TimeRangeItem>
    val isEmpty: Boolean
}

fun BiometricTimesState(): BiometricTimesState {
    return BiometricTimesStateImpl()
}

class BiometricTimesStateImpl : BiometricTimesState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var dialog: BiometricTimesPresenter.Dialog? by mutableStateOf(null)
    override var timeRanges: List<TimeRangeItem> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { timeRanges.isEmpty() }
}
