package ai.opencode.android.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "opencode")

@Singleton
class ApiProvider @Inject constructor(
 @ApplicationContext private val context: Context,
) {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private val _api = MutableStateFlow<OpenCodeApi?>(null)
 val api: StateFlow<OpenCodeApi?> = _api.asStateFlow()
 val dataStore: DataStore<Preferences> = context.dataStore

 init {
 scope.launch {
 initialize()
 }
 }

 suspend fun initialize() {
 val prefs = dataStore.data.first()
 val config = buildConfig(prefs)
 _api.value = OpenCodeApi(config)
 }

 fun reinitialize() {
 scope.launch {
 initialize()
 }
 }

 private fun buildConfig(prefs: Preferences): ServerConfig {
 return ServerConfig(
 url = prefs[ApiProvider.KEY_SERVER_URL] ?: "http://127.0.0.1:4096",
 password = prefs[ApiProvider.KEY_SERVER_PASSWORD],
 mode = when (prefs[ApiProvider.KEY_CONNECTION_MODE]) {
 "EMBEDDED" -> ConnectionMode.EMBEDDED
 else -> ConnectionMode.DEFAULT
 },
 directory = prefs[ApiProvider.KEY_DIRECTORY],
 )
 }

 companion object {
 val KEY_SERVER_URL = stringPreferencesKey("server_url")
 val KEY_SERVER_PASSWORD = stringPreferencesKey("server_password")
 val KEY_CONNECTION_MODE = stringPreferencesKey("connection_mode")
 val KEY_DIRECTORY = stringPreferencesKey("directory")
 }
}