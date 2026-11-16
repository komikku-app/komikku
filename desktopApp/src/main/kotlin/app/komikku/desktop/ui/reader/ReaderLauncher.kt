package app.komikku.desktop.ui.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens and closes the reader. The reader is a full-window screen drawn above the shell (see
 * `KomikkuApp`), not a second window: it shares the main window's fullscreen / placement state,
 * keyboard focus and Compose test harness, and screens anywhere in the tab navigators can open it
 * without a shared navigator. One instance per process, from DI.
 */
class ReaderLauncher {

    data class Request(val mangaId: Long, val chapterId: Long, val page: Int? = null)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun open(mangaId: Long, chapterId: Long, page: Int? = null) {
        _request.value = Request(mangaId, chapterId, page)
    }

    fun close() {
        _request.value = null
    }
}
