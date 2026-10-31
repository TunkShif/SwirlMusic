package one.tunkshif.swirlmusic.ui.common

enum class LoadError { Network, Server }

/**
 * One section of a screen, loaded on its own. A failing section must not blank the whole screen.
 */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>

    data class Content<T>(val value: T) : SectionState<T>

    data class Failed(val error: LoadError) : SectionState<Nothing>
}

/** A list that grows: first page, then "load more" at the end. */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val state: SectionState<Unit> = SectionState.Loading,
    val hasMore: Boolean = false,
    val appending: Boolean = false,
) {
    val isFirstLoad: Boolean get() = state is SectionState.Loading && items.isEmpty()
}

fun interface ToggleLike {
    suspend fun toggle(trackId: Long, liked: Boolean)
}
