package com.enonymous1.robloxavatar.ui

import android.webkit.JavascriptInterface
import com.enonymous1.robloxavatar.data.AvatarModel
import com.enonymous1.robloxavatar.data.Cdn

/**
 * The JavaScript bridge for the Three.js scene. Only the annotated
 * members are callable from JavaScript, and every getter returns a safe
 * default when no avatar is loaded, so the scene can never crash the app
 * by reading a null model the way the original fragment's getters could.
 *
 * Bridge methods run on a WebView thread; the callbacks hand work to the
 * ViewModel, which moves it onto its own scope. */
class AvatarBridge(
    private val avatarProvider: () -> AvatarModel?,
    private val onImageCaptured: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onModelLoaded: () -> Unit,
) {
    @JavascriptInterface
    fun getObjUrl(): String = avatarProvider()?.objUrl ?: ""

    @JavascriptInterface
    fun getMtlUrl(): String = avatarProvider()?.mtlUrl ?: ""

    @JavascriptInterface
    fun getCDN(hash: String): String = Cdn.urlFor(hash) ?: ""

    @JavascriptInterface
    fun getAvatarCameraPositionX(): Double = avatarProvider()?.cameraPosition?.x ?: 0.0

    @JavascriptInterface
    fun getAvatarCameraPositionY(): Double = avatarProvider()?.cameraPosition?.y ?: 0.0

    @JavascriptInterface
    fun getAvatarCameraPositionZ(): Double = avatarProvider()?.cameraPosition?.z ?: 0.0

    @JavascriptInterface
    fun getAvatarCameraDirectionX(): Double = avatarProvider()?.cameraDirection?.x ?: 0.0

    @JavascriptInterface
    fun getAvatarCameraDirectionY(): Double = avatarProvider()?.cameraDirection?.y ?: 0.0

    @JavascriptInterface
    fun getAvatarCameraDirectionZ(): Double = avatarProvider()?.cameraDirection?.z ?: 0.0

    @JavascriptInterface
    fun getAabbMinX(): Double = avatarProvider()?.aabbMin?.x ?: 0.0

    @JavascriptInterface
    fun getAabbMinY(): Double = avatarProvider()?.aabbMin?.y ?: 0.0

    @JavascriptInterface
    fun getAabbMinZ(): Double = avatarProvider()?.aabbMin?.z ?: 0.0

    @JavascriptInterface
    fun getAabbMaxX(): Double = avatarProvider()?.aabbMax?.x ?: 0.0

    @JavascriptInterface
    fun getAabbMaxY(): Double = avatarProvider()?.aabbMax?.y ?: 0.0

    @JavascriptInterface
    fun getAabbMaxZ(): Double = avatarProvider()?.aabbMax?.z ?: 0.0

    @JavascriptInterface
    fun getFov(): Double = avatarProvider()?.fov ?: 0.0

    @JavascriptInterface
    fun setImageData(imageData: String) {
        onImageCaptured(imageData)
    }

    @JavascriptInterface
    fun onLoadError(message: String) {
        onError(message)
    }

    @JavascriptInterface
    fun onModelLoaded() {
        onModelLoaded()
    }
}
