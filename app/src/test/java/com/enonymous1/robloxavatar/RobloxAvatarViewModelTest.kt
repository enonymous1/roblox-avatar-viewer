package com.enonymous1.robloxavatar

import com.enonymous1.robloxavatar.data.AvatarModel
import com.enonymous1.robloxavatar.data.AvatarResult
import com.enonymous1.robloxavatar.data.RobloxGateway
import com.enonymous1.robloxavatar.data.RobloxUser
import com.enonymous1.robloxavatar.data.SavedUsersRepository
import com.enonymous1.robloxavatar.data.UserResolution
import com.enonymous1.robloxavatar.data.Vector3
import com.enonymous1.robloxavatar.ui.RobloxAvatarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RobloxAvatarViewModelTest {

    private val user = RobloxUser(123, "ExampleUser", "Example User", true, emptyList())
    private val avatar = AvatarModel(
        user = user,
        cameraPosition = Vector3(-2.1652, 104.947, -4.72907),
        cameraDirection = Vector3(-0.40558, 0.40558, -0.819152),
        fov = 70.0,
        aabbMin = Vector3(-2.03532, 100.003, -1.16793),
        aabbMax = Vector3(2.24066, 105.39, 0.622441),
        mtlUrl = "https://t0.rbxcdn.com/f70734be44332e058a3270e4545e2943",
        objUrl = "https://t5.rbxcdn.com/bbdb80c2b573bf222da3e92f5f148330",
        textureUrls = emptyList(),
    )

    private inner class FakeGateway(
        var resolution: UserResolution = UserResolution.Single(user),
        var avatarResult: AvatarResult = AvatarResult.Loaded(avatar),
    ) : RobloxGateway {
        override suspend fun resolveUser(rawUsername: String): UserResolution = resolution
        override suspend fun fetchAvatar(user: RobloxUser): AvatarResult = avatarResult
    }

    private class FakeSavedUsersRepository : SavedUsersRepository {
        private val names = MutableStateFlow<List<String>>(emptyList())
        override val savedUsernames: Flow<List<String>> = names
        override suspend fun save(username: String) {
            names.value = (names.value + username).distinct().sorted()
        }

        override suspend fun remove(username: String) {
            names.value = names.value - username
        }
    }

    // Unconfined, not a StandardTestDispatcher: the ViewModel runs on
    // Dispatchers.Main, and a StandardTestDispatcher here would have its
    // own scheduler that runTest's advanceUntilIdle() does not advance,
    // so async assertions could run before the ViewModel's coroutines.
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        gateway: RobloxGateway = FakeGateway(),
        saved: SavedUsersRepository = FakeSavedUsersRepository(),
    ): RobloxAvatarViewModel = RobloxAvatarViewModel(gateway, saved, null)

    @Test
    fun searchLoadsAvatarAndClearsLoading() = runTest {
        val vm = viewModel()
        vm.onUsernameChange("ExampleUser")
        vm.onSearch()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.avatar)
        assertEquals("ExampleUser", state.avatar!!.user.name)
    }

    @Test
    fun multipleCandidatesShowDialogAndSelectionLoads() = runTest {
        val other = user.copy(id = 456, name = "ExampleOther")
        val vm = viewModel(
            FakeGateway(resolution = UserResolution.Multiple(listOf(user, other))),
        )
        vm.onUsernameChange("Example")
        vm.onSearch()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showCandidateDialog)
        assertEquals(2, vm.uiState.value.candidates.size)
        vm.onCandidateSelected(other)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showCandidateDialog)
        assertNotNull(vm.uiState.value.avatar)
    }

    @Test
    fun pendingAvatarShowsMessageNotAvatar() = runTest {
        val vm = viewModel(FakeGateway(avatarResult = AvatarResult.Pending))
        vm.onUsernameChange("ExampleUser")
        vm.onSearch()
        advanceUntilIdle()
        assertNull(vm.uiState.value.avatar)
        assertTrue(vm.uiState.value.message!!.contains("recently"))
    }

    @Test
    fun saveUsernamePersistsAndInvalidColorIsIgnored() = runTest {
        val saved = FakeSavedUsersRepository()
        val vm = viewModel(saved = saved)
        vm.onUsernameChange("ExampleUser")
        vm.onSearch()
        advanceUntilIdle()
        vm.onSaveUsername()
        advanceUntilIdle()
        assertEquals(listOf("ExampleUser"), vm.uiState.value.savedUsers)

        vm.onBackgroundColorChange("not-a-color")
        assertEquals("#000000", vm.uiState.value.backgroundColor)
        vm.onBackgroundColorChange("#247BA0")
        assertEquals("#247BA0", vm.uiState.value.backgroundColor)
    }
}
