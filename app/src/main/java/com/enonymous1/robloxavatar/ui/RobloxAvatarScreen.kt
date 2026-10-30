package com.enonymous1.robloxavatar.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The single screen: username search, saved usernames, the Three.js
 * WebView scene, background colors, and capture/share.
 *
 * The WebView is the one part of the original architecture kept
 * deliberately: Three.js has no native Android equivalent, and the
 * Roblox model arrives as OBJ/MTL. What changed is the bridge around
 * it: file-URL access is off, the scene loads only from the asset
 * loader, and the page is told to load only after it has finished
 * loading and an avatar is set, instead of racing the page load. */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RobloxAvatarScreen(viewModel: RobloxAvatarViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val webViewHolder = remember { WebViewHolder() }
    var pageLoaded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val bridge = remember(viewModel) {
        AvatarBridge(
            avatarProvider = { viewModel.currentAvatar },
            onImageCaptured = { viewModel.onImageCaptured(it) },
            onError = { viewModel.onSceneError(it) },
            onModelLoaded = { viewModel.onSceneLoaded() },
        )
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    LaunchedEffect(state.avatar, pageLoaded) {
        if (state.avatar != null && pageLoaded) {
            webViewHolder.webView?.evaluateJavascript("loadAvatar()", null)
        }
    }

    LaunchedEffect(state.backgroundColor, pageLoaded) {
        if (pageLoaded) {
            webViewHolder.webView?.evaluateJavascript(
                "changeBackgroundColor('${state.backgroundColor}')",
                null,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roblox Avatar") },
                actions = {
                    IconButton(
                        onClick = { webViewHolder.webView?.evaluateJavascript("captureImage()", null) },
                        enabled = state.avatar != null,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Capture and share")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "About this app")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { viewModel.onUsernameChange(it) },
                    label = { Text("Roblox username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.onSearch() },
                    enabled = !state.isLoading,
                ) {
                    Text("Fetch")
                }
            }

            if (state.savedUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.savedUsers) { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SuggestionChip(
                                onClick = { viewModel.onLoadSavedUser(name) },
                                label = { Text(name) },
                            )
                            IconButton(
                                onClick = { viewModel.onRemoveSavedUser(name) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $name",
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { factoryContext ->
                            val assetLoader = WebViewAssetLoader.Builder()
                                .addPathHandler(
                                    "/assets/",
                                    WebViewAssetLoader.AssetsPathHandler(factoryContext),
                                )
                                .build()
                            WebView(factoryContext).apply {
                                settings.javaScriptEnabled = true
                                // Deliberately off, unlike the original: the
                                // scene only needs its own assets and
                                // https:// model/texture hosts.
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.allowFileAccessFromFileURLs = false
                                settings.allowUniversalAccessFromFileURLs = false
                                addJavascriptInterface(bridge, "jsInterface")
                                webViewClient = object : WebViewClientCompat() {
                                    override fun onPageFinished(view: WebView, url: String?) {
                                        super.onPageFinished(view, url)
                                        pageLoaded = true
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: android.webkit.WebResourceRequest,
                                    ): android.webkit.WebResourceResponse? {
                                        return assetLoader.shouldInterceptRequest(request.url)
                                    }
                                }
                                loadUrl(
                                    "https://appassets.androidplatform.net/assets/www/index.html",
                                )
                                webViewHolder.webView = this
                            }
                        },
                        onRelease = {
                            webViewHolder.webView = null
                            it.destroy()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.avatar == null && !state.isLoading) {
                        Text(
                            text = "Enter a username to view their 3D avatar.",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            state.avatar?.let { avatar ->
                Text(
                    text = avatar.user.displayName +
                        " (@" + avatar.user.name + ")" +
                        if (avatar.user.hasVerifiedBadge) " ✓" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.onSaveUsername() }) {
                        Text("Save username")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Background", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BACKGROUND_COLOR_CHOICES.forEach { hex ->
                    val color = try {
                        Color(android.graphics.Color.parseColor(hex))
                    } catch (e: IllegalArgumentException) {
                        Color.Black
                    }
                    val selected = state.backgroundColor.equals(hex, ignoreCase = true)
                    androidx.compose.material3.Button(
                        onClick = { viewModel.onBackgroundColorChange(hex) },
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        shape = CircleShape,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = color,
                            contentColor = color,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Unofficial viewer. Not affiliated with Roblox Corporation. " +
                    "Usernames are sent only to Roblox's public users and " +
                    "thumbnails APIs; saved usernames stay on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (state.showCandidateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onCandidateDialogDismissed() },
            title = { Text("Did you mean:") },
            text = {
                Column {
                    state.candidates.forEach { user ->
                        TextButton(onClick = { viewModel.onCandidateSelected(user) }) {
                            Text(user.displayName + " (@" + user.name + ")")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.onCandidateDialogDismissed() }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (state.showCaptureDialog && state.capturedImageUri != null) {
        CaptureDialog(
            uri = state.capturedImageUri!!,
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, state.capturedImageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Share image"),
                )
                viewModel.onCaptureDialogDismissed()
            },
            onSave = { viewModel.onSaveCapturedToPictures() },
            onDismiss = { viewModel.onCaptureDialogDismissed() },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About") },
            text = {
                Text(
                    "Roblox Avatar Viewer looks up a Roblox username, " +
                        "fetches that user's 3D avatar model, and renders " +
                        "it with Three.js. Enter an exact username for a " +
                        "direct match; otherwise pick from the search " +
                        "results. Captures can be shared or saved to " +
                        "Pictures/Roblox_Avatar. This app is not made by " +
                        "or affiliated with Roblox Corporation, and it " +
                        "never asks for a Roblox account or password.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun CaptureDialog(
    uri: android.net.Uri,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Captured") },
        text = {
            Column {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured avatar",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Share it, or save it to Pictures/Roblox_Avatar.")
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) { Text("Share") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSave) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private class WebViewHolder {
    var webView: WebView? = null
}
