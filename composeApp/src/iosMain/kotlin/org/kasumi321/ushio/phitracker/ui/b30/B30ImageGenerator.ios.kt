@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIScreen
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.posix.memcpy
import kotlin.math.ceil

actual object B30ImageGenerator {

    private val httpClient by lazy {
        HttpClient(Darwin) {
            engine {
                configureRequest {
                    setAllowsCellularAccess(true)
                }
            }
        }
    }

    private const val MAX_BLUR_BITMAP_DIM = 800

    /**
     * UIKit frames are in points, while the export contract is in pixels.
     *
     * Compose iOS maps [LocalDensity] pixels through the backing screen scale
     * before drawing into a UIView. Therefore the off-screen UIView must be
     * sized as targetPixels / UIScreen.scale, and the bitmap context must use
     * the same UIScreen.scale. Using scale=1 shrinks the export on 2x/3x
     * screens; using export density as UIKit scale makes the root constraints
     * drift from the Android baseline.
     */
    private val captureScale: Double
        get() = UIScreen.mainScreen.scale

    private fun exportWidthPoints(scale: Double): Double =
        B30ExportSpec.WIDTH_PX.toDouble() / scale

    private fun exportHeightPoints(data: B30ExportData, scale: Double): Double =
        exportHeightPixels(data) / scale

    actual suspend fun generate(exportData: B30ExportData): B30ImageExport = withContext(Dispatchers.Main) {
        try {
            val backgroundBitmap = exportData.backgroundUri?.let { uri ->
                withOptionalB30Background {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            loadAndBlurBackground(uri, exportData.backgroundBlurRadius)?.toComposeImageBitmap()
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            AppLogger.e("B30ImageGenerator", "Failed to preload/blur background: ${error.message}", error)
                        }.getOrNull()
                    }
                }
            }
            val renderData = if (backgroundBitmap != null) {
                exportData.copy(backgroundBitmap = backgroundBitmap)
            } else {
                exportData
            }

            val pngBytes = captureSharedExportLayout(renderData)
            val image = requireNotNull(SkiaImage.makeFromEncoded(pngBytes)) {
                "Unable to decode captured B30 image"
            }
            validateCapturedSize(renderData, image)
            AppLogger.i("B30ImageGenerator", "B30 iOS export captured: ${exportDiagnostics(renderData, image)}")

            B30ImageExport(
                width = image.width,
                height = image.height,
                pngBytes = pngBytes,
                preview = image.toComposeImageBitmap()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.e("B30ImageGenerator", "B30 iOS export failed: ${exportDiagnostics(exportData, null)} error=${e.message ?: e::class.simpleName}", e)
            throw e
        }
    }

    private suspend fun captureSharedExportLayout(exportData: B30ExportData): ByteArray {
        val windowScene = findActiveWindowScene() ?: error("B30ImageGenerator: no active UIWindowScene")
        val scale = captureScale
        val widthPt = exportWidthPoints(scale)
        val heightPt = exportHeightPoints(exportData, scale)
        val presenter = UIViewController()
        val imageLoadTracker = B30ExportImageLoadTracker(exportData.illustrationSlotIds())
        val captureWindow = UIWindow(windowScene = windowScene).apply {
            setFrame(CGRectMake(-widthPt - 100.0, 0.0, widthPt, heightPt))
            setContentScaleFactor(scale)
            layer.contentsScale = scale
            rootViewController = presenter
            setHidden(false)
        }

        val controller = ComposeUIViewController {
            CompositionLocalProvider(
                LocalDensity provides Density(B30ExportSpec.DENSITY, B30ExportSpec.FONT_SCALE)
            ) {
                PhiTrackerTheme(
                    darkTheme = exportData.darkTheme,
                    isAmoled = exportData.isAmoled,
                    settings = exportData.themeSettings
                ) {
                    B30ExportLayout(
                        data = exportData,
                        allowHardwareImages = false,
                        imageLoadTracker = imageLoadTracker
                    )
                }
            }
        }

        val view = requireNotNull(controller.view) { "B30ImageGenerator: ComposeUIViewController view is null" }
        presenter.view.addSubview(view)
        try {
            captureWindow.setFrame(CGRectMake(-widthPt - 100.0, 0.0, widthPt, heightPt))
            presenter.view.setFrame(CGRectMake(0.0, 0.0, widthPt, heightPt))
            view.setFrame(CGRectMake(0.0, 0.0, widthPt, heightPt))
            view.setContentScaleFactor(scale)
            view.layer.contentsScale = scale
            presenter.view.setNeedsLayout()
            presenter.view.layoutIfNeeded()
            view.setNeedsLayout()
            view.layoutIfNeeded()
            waitForUIKitFrame()
            captureWindow.setFrame(CGRectMake(-widthPt - 100.0, 0.0, widthPt, heightPt))
            presenter.view.setFrame(captureWindow.bounds)
            view.setFrame(CGRectMake(0.0, 0.0, widthPt, heightPt))
            presenter.view.setNeedsLayout()
            presenter.view.layoutIfNeeded()
            view.setNeedsLayout()
            view.layoutIfNeeded()
            waitForUIKitFrame()
            imageLoadTracker.awaitAll()
            // State.Success reaches AsyncImage before its recomposition has
            // necessarily been drawn into UIKit's hierarchy.
            waitForUIKitFrame()
            return captureViewHierarchy(view, widthPt, heightPt, scale)
        } finally {
            view.removeFromSuperview()
            captureWindow.setHidden(true)
            captureWindow.rootViewController = null
        }
    }

    private suspend fun waitForUIKitFrame() {
        repeat(6) { delay(16) }
    }

    private fun captureViewHierarchy(
        view: platform.UIKit.UIView,
        width: Double,
        height: Double,
        scale: Double
    ): ByteArray {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, scale)
        val ok = view.drawViewHierarchyInRect(CGRectMake(0.0, 0.0, width, height), afterScreenUpdates = true)
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        check(ok) { "B30ImageGenerator: UIKit hierarchy capture failed" }
        val data = UIImagePNGRepresentation(requireNotNull(image) { "B30ImageGenerator: captured image is null" })
            ?: error("B30ImageGenerator: unable to encode captured image")
        return data.toByteArray()
    }

    private fun validateCapturedSize(data: B30ExportData, image: SkiaImage) {
        check(image.width == B30ExportSpec.WIDTH_PX) {
            "Expected B30 width ${B30ExportSpec.WIDTH_PX}px, got ${image.width}px; ${exportDiagnostics(data, image)}"
        }
        require(image.height > 0) {
            "Captured B30 image height is invalid: ${image.height}; ${exportDiagnostics(data, image)}"
        }
        val expectedHeightPx = exportHeightPixels(data).toInt()
        val lowerBound = (expectedHeightPx * 0.95).toInt()
        val upperBound = (expectedHeightPx * 1.05).toInt()
        if (image.height !in lowerBound..upperBound) {
            AppLogger.w("B30ImageGenerator", "Captured B30 height is suspicious: ${exportDiagnostics(data, image)}")
        }
    }

    private fun exportDiagnostics(data: B30ExportData, image: SkiaImage?): String {
        val heightDp = exportHeightDp(data)
        val heightPx = exportHeightPixels(data).toInt()
        val scale = captureScale
        return "phiRecords=${data.phiRecords.size} bestRecords=${data.bestRecords.size} overflowRecords=${data.overflowRecords.size} showOverflow=${data.overflowRecords.isNotEmpty()} heightDp=$heightDp heightPx=$heightPx widthPt=${exportWidthPoints(scale)} heightPt=${exportHeightPoints(data, scale)} capturedWidth=${image?.width ?: "unknown"} capturedHeight=${image?.height ?: "unknown"} widthPx=${B30ExportSpec.WIDTH_PX} uiScale=$scale density=${B30ExportSpec.DENSITY}"
    }

    private fun exportHeightPixels(data: B30ExportData): Double {
        return ceil(exportHeightDp(data) * B30ExportSpec.DENSITY)
    }

    private fun exportHeightDp(data: B30ExportData): Double {
        val pagePadding = B30ExportSpec.PAGE_PADDING_DP
        val header = B30ExportSpec.profileCardHeightDp
        val sectionTitle = B30ExportSpec.sectionTitleHeightDp
        val cardHeight = B30ExportSpec.cardHeightDp
        val verticalGap = B30ExportSpec.cardVerticalGapDp
        fun gridHeight(cardCount: Int): Float {
            if (cardCount == 0) return 0f
            val rows = ceil(cardCount / 3.0).toInt()
            return rows * cardHeight + (rows - 1) * verticalGap
        }
        var h = pagePadding
        h += header + 12f
        h += sectionTitle + 6f + gridHeight(data.phiRecords.size)
        h += 8f + sectionTitle + 6f + gridHeight(data.bestRecords.size)
        if (data.overflowRecords.isNotEmpty()) {
            h += 8f + sectionTitle + 6f + gridHeight(data.overflowRecords.size)
        }
        if (data.tagAnalysis != null || data.histogram != null) {
            h += 8f + sectionTitle + B30ExportSpec.tagSectionGapDp + B30ExportSpec.tagSectionHeightDp
        }
        h += 8f + B30ExportSpec.footerHeightDp + pagePadding
        return h.toDouble().coerceAtLeast(1.0)
    }

    private suspend fun loadAndBlurBackground(uri: String, blurRadius: Int): SkiaImage? {
        val original = loadSkiaImage(uri) ?: return null
        val maxDim = MAX_BLUR_BITMAP_DIM
        val scale = if (maxOf(original.width, original.height) <= maxDim) {
            1f
        } else {
            maxDim.toFloat() / maxOf(original.width, original.height)
        }
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)

        val downSurface = Surface.makeRasterN32Premul(w, h)
        if (scale < 1f) {
            downSurface.canvas.scale(scale, scale)
        }
        downSurface.canvas.drawImage(original, 0f, 0f)
        val downscaled = downSurface.makeImageSnapshot()
        if (blurRadius <= 0) return downscaled

        // Gaussian sigma scaled to match the Android StackBlur radius
        // (Android radius 50 corresponds to the previous fixed sigma 15).
        val sigma = blurRadius * 0.3f
        val blurFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null)
        val blurPaint = Paint().apply {
            imageFilter = blurFilter
            isAntiAlias = true
        }
        val blurSurface = Surface.makeRasterN32Premul(w, h)
        blurSurface.canvas.drawImage(downscaled, 0f, 0f, blurPaint)
        return blurSurface.makeImageSnapshot()
    }

    private suspend fun loadSkiaImage(uri: String): SkiaImage? {
        val bytes = try {
            when {
                uri.startsWith("http://") || uri.startsWith("https://") -> httpClient.get(uri).bodyAsBytes()
                else -> {
                    val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                    FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        return try {
            SkiaImage.makeFromEncoded(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }
}

private fun findActiveWindowScene(): UIWindowScene? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    return scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull { it.keyWindow != null }
        ?: scenes.firstOrNull()
}


private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    return ByteArray(length).also { output ->
        output.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
}
