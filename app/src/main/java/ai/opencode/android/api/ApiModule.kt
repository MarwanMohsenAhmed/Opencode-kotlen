package ai.opencode.android.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode")

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    private val KEY_SERVER_PASSWORD = stringPreferencesKey("server_password")
    private val KEY_CONNECTION_MODE = stringPreferencesKey("connection_mode")
    private val KEY_DIRECTORY = stringPreferencesKey("directory")

    @Provides
    @Singleton
    fun provideServerConfig(@ApplicationContext context: Context): ServerConfig {
        val prefs = runBlocking { context.dataStore.data.first() }
        return ServerConfig(
            url = prefs[KEY_SERVER_URL] ?: "http://127.0.0.1:4096",
            password = prefs[KEY_SERVER_PASSWORD],
            mode = when (prefs[KEY_CONNECTION_MODE]) {
                "EMBEDDED" -> ConnectionMode.EMBEDDED
                else -> ConnectionMode.REMOTE
            },
            directory = prefs[KEY_DIRECTORY],
        )
    }

    @Provides
    @Singleton
    fun provideApi(config: ServerConfig): OpenCodeApi =
        OpenCodeApi.create(config)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
