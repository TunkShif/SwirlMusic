package one.tunkshif.swirlmusic.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lyricsPrefsDataStore by preferencesDataStore("lyrics_prefs")

/** Which extra line a synced-lyrics row shows under the original text. */
enum class LyricsExtra { None, Translation, Romanization }

interface LyricsPreferences {
    val extra: Flow<LyricsExtra>
    suspend fun setExtra(value: LyricsExtra)
}

/** DataStore-backed; an unknown stored value falls back to the default. */
@Singleton
class LyricsPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LyricsPreferences {

    override val extra: Flow<LyricsExtra> = context.lyricsPrefsDataStore.data.map { prefs ->
        prefs[KEY_EXTRA]
            ?.let { name -> LyricsExtra.entries.firstOrNull { it.name == name } }
            ?: LyricsExtra.Translation
    }

    override suspend fun setExtra(value: LyricsExtra) {
        context.lyricsPrefsDataStore.edit { it[KEY_EXTRA] = value.name }
    }

    private companion object {
        val KEY_EXTRA = stringPreferencesKey("extra")
    }
}
