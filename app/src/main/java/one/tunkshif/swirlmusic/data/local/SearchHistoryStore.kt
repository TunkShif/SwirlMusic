package one.tunkshif.swirlmusic.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.searchHistoryDataStore by preferencesDataStore("search_history")

/** Recent search terms, newest first. */
interface SearchHistory {
    suspend fun recent(): List<String>
    suspend fun add(query: String)
    suspend fun clear()
}

/**
 * Backed by DataStore. A plain newline-separated string: queries do not contain newlines, and it
 * keeps the stored value readable.
 */
@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SearchHistory {

    override suspend fun recent(): List<String> =
        context.searchHistoryDataStore.data.first()[KEY]
            ?.lines()
            ?.filter { it.isNotBlank() }
            .orEmpty()

    override suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + recent().filterNot { it.equals(trimmed, true) })
            .take(MAX_ENTRIES)
        context.searchHistoryDataStore.edit { it[KEY] = updated.joinToString("\n") }
    }

    override suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent")
        const val MAX_ENTRIES = 8
    }
}
