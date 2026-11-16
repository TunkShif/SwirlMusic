package one.tunkshif.swirlmusic.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmCommentsApi
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.data.model.CommentTarget
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState

data class CommentsUiState(
    /** Hot is the default every time the screen opens (D66). */
    val sort: CommentSort = CommentSort.Hot,
    val comments: SectionState<List<Comment>> = SectionState.Loading,
    /** The thread's total, which can exceed what paging ever loads. Null until known. */
    val totalCount: Long? = null,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** A failed page keeps its comments; the footer shows retry. */
    val moreError: LoadError? = null,
)

/**
 * Comments for one song/playlist/album. Each sort keeps its own loaded pages (D66): switching
 * shows the cached list and only fetches the first page the first time. One request in flight
 * per sort; appends de-duplicate by comment id.
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val api: NcmCommentsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var target: CommentTarget? = null
    private var sort: CommentSort = CommentSort.Hot
    private val feeds = mutableMapOf<CommentSort, Feed>()

    private class Feed {
        var state: SectionState<List<Comment>> = SectionState.Loading
        var totalCount: Long? = null
        var next: CommentPageKey? = null
        var loadingMore: Boolean = false
        var moreError: LoadError? = null
        var started: Boolean = false
        var job: Job? = null
    }

    /** The destination is a parameter, like TrackListViewModel.load, so the VM stays injectable. */
    fun load(target: CommentTarget) {
        if (this.target == target) return
        this.target = target
        // A new thread starts over: empty feeds, Hot first (D66).
        feeds.values.forEach { it.job?.cancel() }
        feeds.clear()
        sort = CommentSort.Hot
        emit()
        fetchFirst(CommentSort.Hot)
    }

    fun onSelectSort(selected: CommentSort) {
        if (selected == sort) return
        sort = selected
        emit()
        val feed = feed(selected)
        if (!feed.started) fetchFirst(selected)
    }

    /** First-page retry after a full error state. */
    fun retry() = fetchFirst(sort)

    /** The footer retry shares this path: a failed page keeps [Feed.next], so it resumes. */
    fun loadMore() {
        val feed = feed(sort)
        if (feed.job?.isActive == true) return
        val key = feed.next ?: return
        val loaded = (feed.state as? SectionState.Content)?.value ?: return
        val target = this.target ?: return
        val forSort = sort
        feed.loadingMore = true
        feed.moreError = null
        emit()
        feed.job = viewModelScope.launch {
            when (val result = api.comments(target, forSort, key)) {
                is ApiResult.Success -> {
                    feed.next = result.value.next
                    feed.totalCount = result.value.totalCount
                    feed.state = SectionState.Content(
                        (loaded + result.value.comments).distinctBy { it.id },
                    )
                }
                is ApiResult.NetworkError -> feed.moreError = LoadError.Network
                is ApiResult.HttpError, is ApiResult.Unauthorized ->
                    feed.moreError = LoadError.Server
            }
            feed.loadingMore = false
            emit()
        }
    }

    private fun fetchFirst(forSort: CommentSort) {
        val feed = feed(forSort)
        if (feed.job?.isActive == true) return
        val target = this.target ?: return
        feed.started = true
        feed.state = SectionState.Loading
        feed.moreError = null
        emit()
        feed.job = viewModelScope.launch {
            when (val result = api.comments(target, forSort, null)) {
                is ApiResult.Success -> {
                    feed.totalCount = result.value.totalCount
                    feed.next = result.value.next
                    feed.state = SectionState.Content(
                        result.value.comments.distinctBy { it.id },
                    )
                }
                is ApiResult.NetworkError -> feed.state = SectionState.Failed(LoadError.Network)
                is ApiResult.HttpError, is ApiResult.Unauthorized ->
                    feed.state = SectionState.Failed(LoadError.Server)
            }
            emit()
        }
    }

    private fun feed(sort: CommentSort): Feed = feeds.getOrPut(sort) { Feed() }

    /** Projects the selected sort's feed into the UI state. */
    private fun emit() {
        val feed = feed(sort)
        _state.value = CommentsUiState(
            sort = sort,
            comments = feed.state,
            totalCount = feed.totalCount,
            canLoadMore = feed.next != null && feed.state is SectionState.Content,
            loadingMore = feed.loadingMore,
            moreError = feed.moreError,
        )
    }
}
