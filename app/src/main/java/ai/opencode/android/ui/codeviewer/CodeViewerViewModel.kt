package ai.opencode.android.ui.codeviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ai.opencode.android.api.FileContent
import ai.opencode.android.data.FileRepository
import javax.inject.Inject

@HiltViewModel
class CodeViewerViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _fileContent = MutableStateFlow<FileContent?>(null)
    val fileContent: StateFlow<FileContent?> = _fileContent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadFile(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                fileRepository.loadFile(path)
                _fileContent.value = fileRepository.currentFile.value
                if (_fileContent.value == null) {
                    _error.value = "Failed to load file: $path"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load file: $path"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
