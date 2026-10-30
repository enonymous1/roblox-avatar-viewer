package com.enonymous1.robloxavatar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enonymous1.robloxavatar.data.DataStoreSavedUsersRepository
import com.enonymous1.robloxavatar.data.RobloxRepository
import com.enonymous1.robloxavatar.data.api.RobloxApiFactory
import com.enonymous1.robloxavatar.ui.RobloxAvatarScreen
import com.enonymous1.robloxavatar.ui.RobloxAvatarViewModel
import com.enonymous1.robloxavatar.ui.theme.RobloxAvatarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RobloxAvatarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            check(modelClass.isAssignableFrom(RobloxAvatarViewModel::class.java)) {
                                "Unknown ViewModel class"
                            }
                            val client = RobloxApiFactory.createClient()
                            return RobloxAvatarViewModel(
                                gateway = RobloxRepository(
                                    usersApi = RobloxApiFactory.createUsersApi(client),
                                    thumbnailsApi = RobloxApiFactory.createThumbnailsApi(client),
                                    json = RobloxApiFactory.json,
                                ),
                                savedUsersRepository = DataStoreSavedUsersRepository(
                                    applicationContext,
                                ),
                                appContext = applicationContext,
                            ) as T
                        }
                    }
                    val viewModel: RobloxAvatarViewModel = viewModel(factory = factory)
                    RobloxAvatarScreen(viewModel = viewModel)
                }
            }
        }
    }
}
