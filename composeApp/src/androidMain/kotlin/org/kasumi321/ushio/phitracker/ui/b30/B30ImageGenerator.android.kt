package org.kasumi321.ushio.phitracker.ui.b30

import android.graphics.Bitmap
import android.graphics.Canvas
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.data.platform.AndroidPlatformContext
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import org.kasumi321.ushio.phitracker.utils.stackBlur
import java.io.ByteArrayOutputStream

/** Max dimension (px) for the bitmap passed to [stackBlur] to avoid OOM on large gallery images. */
private const val MAX_BLUR_BITMAP_DIM = 1600

actual object B30ImageGenerator {
    actual suspend fun generate(exportData: B30ExportData): B30ImageExport {
        return try {
            withContext(Dispatchers.Main) {
                val bitmap = renderOffscreen(exportData)
                val preview = bitmap.asImageBitmap()
                val pngBytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            "Unable to encode B30 image"
                        }
                        output.toByteArray()
                    }
                }
                B30ImageExport(
                    width = bitmap.width,
                    height = bitmap.height,
                    pngBytes = pngBytes,
                    preview = preview
                )
            }
        } catch (e: Exception) {
            AppLogger.e("B30ImageGenerator", "generate failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Renders [B30ExportLayout] off-screen into a software [Bitmap] at
     * [B30ExportSpec.WIDTH_PX] px wide.
     *
     * The background is pre-loaded and stack-blurred (radius configurable via
     * [B30ExportData.backgroundBlurRadius]) before the Compose composition to
     * avoid weak composable background timing risk. The
     * container is hidden off-screen (alpha=0, translated far left) to
     * eliminate visible flicker during capture.
     *
     * [allowHardwareImages] is set to false on the export layout so Coil
     * returns software bitmaps compatible with [View.draw] onto a software
     * [Canvas].
     *
     * @throws IllegalStateException if no Activity is available or the composed height is zero.
     */
    private suspend fun renderOffscreen(data: B30ExportData): Bitmap {
        val activity = requireNotNull(AndroidPlatformContext.currentActivity) {
            "B30ImageGenerator: no Activity available -- currentActivity is null (app may be backgrounded)"
        }
        val root = activity.window?.decorView as? ViewGroup
            ?: error(
                "B30ImageGenerator: decorView is not a ViewGroup " +
                    "(type=${activity.window?.decorView?.javaClass?.name})"
            )

        val preBlurredBg = data.backgroundUri?.let { uri ->
            loadAndBlurBackground(activity, uri, data.backgroundBlurRadius)?.asImageBitmap()
        }

        val augmentedData = if (preBlurredBg != null) {
            data.copy(backgroundBitmap = preBlurredBg)
        } else {
            data
        }
        val imageLoadTracker = B30ExportImageLoadTracker(augmentedData.illustrationSlotIds())

        val container = FrameLayout(activity).apply {
            alpha = 0f
            translationX = -10_000f
            layoutParams = ViewGroup.LayoutParams(
                B30ExportSpec.WIDTH_PX,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val composeView = ComposeView(activity).apply {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(B30ExportSpec.DENSITY, B30ExportSpec.FONT_SCALE)
                ) {
                    PhiTrackerTheme(
                        darkTheme = augmentedData.darkTheme,
                        isAmoled = augmentedData.isAmoled,
                        settings = augmentedData.themeSettings
                    ) {
                        B30ExportLayout(
                            data = augmentedData,
                            allowHardwareImages = false,
                            imageLoadTracker = imageLoadTracker
                        )
                    }
                }
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                B30ExportSpec.WIDTH_PX,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(container)
        try {
            waitForLayout(composeView)
            imageLoadTracker.awaitAll()
            // Painter state changes are applied through a recomposition. Wait
            // for the following frame so View.draw observes the successful
            // image state rather than the previous loading placeholder.
            waitForNextPreDraw(composeView)

            val widthSpec = MeasureSpec.makeMeasureSpec(B30ExportSpec.WIDTH_PX, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)

            val measuredHeight = composeView.measuredHeight
            require(measuredHeight > 0) {
                "B30ImageGenerator: composeView measured height is 0 after layout. " +
                    "phiRecords=${data.phiRecords.size} bestRecords=${data.bestRecords.size} " +
                    "overflowRecords=${data.overflowRecords.size} isAttached=${composeView.isAttachedToWindow}"
            }

            composeView.layout(0, 0, B30ExportSpec.WIDTH_PX, measuredHeight)

            val bitmap = Bitmap.createBitmap(
                B30ExportSpec.WIDTH_PX,
                measuredHeight,
                Bitmap.Config.ARGB_8888
            )
            composeView.draw(Canvas(bitmap))
            return bitmap
        } finally {
            root.removeView(container)
        }
    }

    private suspend fun loadAndBlurBackground(
        context: android.content.Context,
        uri: String,
        blurRadius: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        var loaded: Bitmap? = null
        var bounded: Bitmap? = null
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .size(MAX_BLUR_BITMAP_DIM, MAX_BLUR_BITMAP_DIM)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            if (result !is SuccessResult) return@withContext null

            loaded = (result.image as BitmapImage).bitmap
            bounded = if (maxOf(loaded.width, loaded.height) > MAX_BLUR_BITMAP_DIM) {
                val scale = MAX_BLUR_BITMAP_DIM.toFloat() / maxOf(loaded.width, loaded.height)
                val newW = (loaded.width * scale).toInt().coerceAtLeast(1)
                val newH = (loaded.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(loaded, newW, newH, true)
            } else {
                loaded
            }
            val blurred = if (blurRadius > 0) stackBlur(bounded, blurRadius) else bounded
            // Recycle the intermediate downscaled copy only if we created it
            // and didn't hand it straight back as the unblurred result.
            // Never recycle the Coil bitmap — it may be managed internally.
            if (bounded !== loaded && blurred !== bounded) bounded.recycle()
            blurred
        } catch (e: Exception) {
            AppLogger.e("B30ImageGenerator", "Failed to preload/blur background: ${e.message}", e)
            bounded?.let { if (it !== loaded) it.recycle() }
            null
        }
    }

    /**
     * Suspends until the [target] view's next pre-draw, ensuring the Android
     * measure/layout pass (and thus Compose's initial composition) has completed.
     */
    private suspend fun waitForLayout(target: View) {
        val deferred = CompletableDeferred<Unit>()
        val obs = target.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                obs.removeOnPreDrawListener(this)
                deferred.complete(Unit)
                return true
            }
        }
        obs.addOnPreDrawListener(listener)
        if (target.width > 0 && target.height > 0) {
            obs.removeOnPreDrawListener(listener)
            deferred.complete(Unit)
        }
        deferred.await()
    }

    private suspend fun waitForNextPreDraw(target: View) {
        val deferred = CompletableDeferred<Unit>()
        val observer = target.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                deferred.complete(Unit)
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
        // The image-success recomposition may already have completed by the
        // time this listener is registered. Explicitly schedule one draw so
        // the renderer never waits indefinitely for an otherwise idle view.
        target.invalidate()
        deferred.await()
    }
}
