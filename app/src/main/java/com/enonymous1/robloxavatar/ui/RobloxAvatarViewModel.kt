package com.enonymous1.robloxavatar.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enonymous1.robloxavatar.data.AvatarModel
import com.enonymous1.robloxavatar.data.AvatarResult
import com.enonymous1.robloxavatar.data.RobloxGateway
import com.enonymous1.robloxavatar.data.RobloxUser
import com.enonymous1.robloxavatar.data.SavedUsersRepository
import com.enonymous1.robloxavatar.data.UserResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RobloxAvatarUiState(
    val username: String = "",
    val isLoading: Boolean = false,
    val avatar: AvatarModel? = null,
    val candidates: List<RobloxUser> = emptyList(),
    val showCandidateDialog: Boolean = false,
    val message: String? = null,
    val savedUsers: List<String> = emptyList(),
    val backgroundColor: String = DEFAULT_BACKGROUND_COLOR,
    val capturedImageUri: Uri? = null,
    val showCaptureDialog: Boolean = false,
    val sceneReady: Boolean = false,
) {
    companion object {
        const val DEFAULT_BACKGROUND_COLOR = "#000000"
    }
}

/** Hex colors the scene background picker offers. The bridge only ever
 * receives a value from this list or one that passed [isValidHexColor],
 * so JavaScript injection through the color is not possible. */
val BACKGROUND_COLOR_CHOICES = listOf(
    "#000000",
    "#001F25",
    "#247BA0",
    "#006688",
    "#FFFFFF",
    "#FFE16E",
    "#006D3B",
    "#BA1A1A",
)

fun isValidHexColor(value: String): Boolean =
    Regex("^#[0-9A-Fa-f]{6}$").matches(value)

class RobloxAvatarViewModel(
    private val gateway: RobloxGateway,
    private val savedUsersRepository: SavedUsersRepository,
    private val appContext: Context?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RobloxAvatarUiState())
    val uiState: StateFlow<RobloxAvatarUiState> = _uiState.asStateFlow()

    /** The avatar the JavaScript bridge reads. Read at call time, never
     * cached in the bridge, so the scene always sees the latest search. */
    val currentAvatar: AvatarModel?
        get() = _uiState.value.avatar

    init {
        viewModelScope.launch {
            savedUsersRepository.savedUsernames.collect { names ->
                _uiState.update { it.copy(savedUsers = names) }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onSearch() {
        val username = _uiState.value.username
        if (username.isBlank()) {
            _uiState.update { it.copy(message = "Enter a Roblox username.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, sceneReady = false) }
            when (val resolution = gateway.resolveUser(username)) {
                is UserResolution.Single -> fetchAvatar(resolution.user)
                is UserResolution.Multiple -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        candidates = resolution.users,
                        showCandidateDialog = true,
                    )
                }
                UserResolution.NotFound -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "No results found for '$username'. Please try again.",
                    )
                }
                is UserResolution.Failed -> _uiState.update {
                    it.copy(isLoading = false, message = resolution.message)
                }
            }
        }
    }

    fun onCandidateSelected(user: RobloxUser) {
        _uiState.update {
            it.copy(
                showCandidateDialog = false,
                candidates = emptyList(),
                username = user.name,
                isLoading = true,
                message = null,
                sceneReady = false,
            )
        }
        viewModelScope.launch { fetchAvatar(user) }
    }

    fun onCandidateDialogDismissed() {
        _uiState.update { it.copy(showCandidateDialog = false, candidates = emptyList()) }
    }

    private suspend fun fetchAvatar(user: RobloxUser) {
        when (val result = gateway.fetchAvatar(user)) {
            is AvatarResult.Loaded -> _uiState.update {
                it.copy(isLoading = false, avatar = result.avatar, message = null)
            }
            AvatarResult.Pending -> _uiState.update {
                it.copy(
                    isLoading = false,
                    message = "This avatar was updated recently. Please try again in a couple of minutes.",
                )
            }
            AvatarResult.Unavailable -> _uiState.update {
                it.copy(isLoading = false, message = "This avatar is unavailable.")
            }
            is AvatarResult.Failed -> _uiState.update {
                it.copy(isLoading = false, message = result.message)
            }
        }
    }

    fun onSceneLoaded() {
        _uiState.update { it.copy(sceneReady = true) }
    }

    fun onSceneError(message: String) {
        _uiState.update { it.copy(isLoading = false, message = message) }
    }

    fun onBackgroundColorChange(hexColor: String) {
        if (!isValidHexColor(hexColor)) return
        _uiState.update { it.copy(backgroundColor = hexColor) }
    }

    fun onSaveUsername() {
        val name = _uiState.value.avatar?.user?.name ?: _uiState.value.username.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            savedUsersRepository.save(name)
            _uiState.update { it.copy(message = "$name saved.") }
        }
    }

    fun onRemoveSavedUser(name: String) {
        viewModelScope.launch { savedUsersRepository.remove(name) }
    }

    fun onLoadSavedUser(name: String) {
        onUsernameChange(name)
        onSearch()
    }

    fun onImageCaptured(dataUrl: String) {
        val userId = _uiState.value.avatar?.user?.id ?: return
        val context = appContext ?: return
        viewModelScope.launch {
            val pngBytes = AvatarImageSaver.parsePngDataUrl(dataUrl)
            if (pngBytes == null) {
                _uiState.update { it.copy(message = "Could not read the captured image.") }
                return@launch
            }
            val uri = try {
                AvatarImageSaver.saveToCache(
                    context,
                    pngBytes,
                    AvatarImageSaver.captureFileName(userId),
                )
            } catch (e: Exception) {
                null
            }
            if (uri == null) {
                _uiState.update { it.copy(message = "Could not save the captured image.") }
            } else {
                _uiState.update {
                    it.copy(capturedImageUri = uri, showCaptureDialog = true, message = null)
                }
            }
        }
    }

    fun onSaveCapturedToPictures() {
        val uri = _uiState.value.capturedImageUri ?: return
        val userId = _uiState.value.avatar?.user?.id ?: return
        val context = appContext ?: return
        viewModelScope.launch {
            val pngBytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            if (pngBytes == null) {
                _uiState.update { it.copy(message = "Could not read the captured image.") }
                return@launch
            }
            val saved = AvatarImageSaver.saveToPictures(
                context,
                pngBytes,
                AvatarImageSaver.captureFileName(userId),
            )
            _uiState.update {
                it.copy(
                    showCaptureDialog = false,
                    message = if (saved != null) {
                        "Saved to Pictures/Roblox_Avatar."
                    } else {
                        "Saving to Pictures needs Android 10 or later."
                    },
                )
            }
        }
    }

    fun onCaptureDialogDismissed() {
        _uiState.update { it.copy(showCaptureDialog = false) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

}
