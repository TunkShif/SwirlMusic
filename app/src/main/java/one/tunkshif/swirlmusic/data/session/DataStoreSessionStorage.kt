package one.tunkshif.swirlmusic.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class DataStoreSessionStorage(
    private val dataStore: DataStore<Preferences>,
) : SessionStorage {

    override suspend fun read(): String? = dataStore.data.first()[KEY]

    override suspend fun write(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(KEY)
            } else {
                preferences[KEY] = value
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("session_ciphertext")
    }
}
