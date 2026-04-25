package ai.opencode.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.opencode.android.api.ConnectionMode
import ai.opencode.android.api.OpenCodeApi
import ai.opencode.android.api.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

object SettingsKeys {
    val SERVER_URL = stringPreferencesKey("server_url")
    val SERVER_PASSWORD = stringPreferencesKey("server_password")
    val CONNECTION_MODE = stringPreferencesKey("connection_mode")
    val DIRECTORY = stringPreferencesKey("directory")
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.SERVER_URL] ?: "http://127.0.0.1:4096"
    }
    val serverUrlState = serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "http://127.0.0.1:4096")

    val serverPassword: Flow<String> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.SERVER_PASSWORD] ?: ""
    }
    val serverPasswordState = serverPassword.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val connectionMode: Flow<ConnectionMode> = dataStore.data.map { prefs ->
        when (prefs[SettingsKeys.CONNECTION_MODE]) {
            ConnectionMode.REMOTE.name -> ConnectionMode.REMOTE
            else -> ConnectionMode.DEFAULT
        }
    }
    val connectionModeState = connectionMode.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionMode.DEFAULT)

    val directory: Flow<String> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.DIRECTORY] ?: "/root"
    }
    val directoryState = directory.stateIn(viewModelScope, SharingStarted.Eagerly, "/root")

    fun setServerUrl(value: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SettingsKeys.SERVER_URL] = value }
        }
    }

    fun setServerPassword(value: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SettingsKeys.SERVER_PASSWORD] = value }
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SettingsKeys.CONNECTION_MODE] = mode.name }
        }
    }

    fun setDirectory(value: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SettingsKeys.DIRECTORY] = value }
        }
    }

    suspend fun testConnection(baseUrl: String, password: String): Result<String> {
        return try {
            val config = ServerConfig(url = baseUrl, password = password.ifBlank { null })
            val api = OpenCodeApi(config)
            val response = api.health()
            if (response.healthy) {
                Result.success("Healthy (v${response.version})")
            } else {
                Result.failure(Exception("Server unhealthy"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrlState.collectAsState()
    val serverPassword by viewModel.serverPasswordState.collectAsState()
    val connectionMode by viewModel.connectionModeState.collectAsState()
    val directory by viewModel.directoryState.collectAsState()

    var testInProgress by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionHeader(title = "Connection")

            OutlinedTextField(
                value = serverUrl,
                onValueChange = viewModel::setServerUrl,
                label = { Text("Server URL") },
                placeholder = { Text("http://127.0.0.1:4096") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = serverPassword,
                onValueChange = viewModel::setServerPassword,
                label = { Text("Password") },
                placeholder = { Text("Server authentication password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Connection Mode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = connectionMode == ConnectionMode.EMBEDDED,
                    onClick = { viewModel.setConnectionMode(ConnectionMode.EMBEDDED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(ConnectionMode.EMBEDDED.label)
                }
                SegmentedButton(
                    selected = connectionMode == ConnectionMode.REMOTE,
                    onClick = { viewModel.setConnectionMode(ConnectionMode.REMOTE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(ConnectionMode.REMOTE.label)
                }
            }

            if (connectionMode == ConnectionMode.EMBEDDED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The OpenCode server will run locally on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect to an external OpenCode server using the URL and password above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "Project")

            OutlinedTextField(
                value = directory,
                onValueChange = viewModel::setDirectory,
                label = { Text("Working Directory") },
                placeholder = { Text("/root") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        SectionHeader(title = "About")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OpenCode",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Version ${getAppVersion()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    testInProgress = true
                    testResult = null
                    viewModel.viewModelScope.launch {
                        val effectiveUrl = if (serverUrl.isBlank()) "http://127.0.0.1:4096" else serverUrl
                        val result = viewModel.testConnection(effectiveUrl, serverPassword)
                        testInProgress = false
                        testResult = result.fold(
                            onSuccess = { "Connected: $it" },
                            onFailure = { "Failed: ${it.message}" }
                        )
                    }
                },
                enabled = !testInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Test Connection")
            }

            testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                val isSuccess = result.startsWith("Connected")
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun getAppVersion(): String {
    return try {
        val packageInfo = android.os.Build.MANUFACTURER
        @Suppress("MagicNumber")
        "1.0.0"
    } catch (_: Exception) {
        "Unknown"
    }
}
