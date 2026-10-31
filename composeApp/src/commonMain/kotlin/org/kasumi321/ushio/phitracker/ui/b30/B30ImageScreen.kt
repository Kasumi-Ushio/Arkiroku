package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.preloadIllustrationThumbnail
import org.kasumi321.ushio.phitracker.ui.home.scoreCardThumbnailSizePx
import org.kasumi321.ushio.phitracker.data.platform.rememberB30BackgroundPicker
import org.kasumi321.ushio.phitracker.data.platform.saveB30ImageToPictures
import org.kasumi321.ushio.phitracker.data.platform.shareB30Image
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.domain.model.B30TagAnalysis
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.glass.GlassBottomBar
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.rememberGlassHazeStyle
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_back
import phitracker.composeapp.generated.resources.b30ex_action_save
import phitracker.composeapp.generated.resources.b30ex_action_share
import phitracker.composeapp.generated.resources.b30ex_album_image
import phitracker.composeapp.generated.resources.b30ex_background
import phitracker.composeapp.generated.resources.b30ex_background_blur
import phitracker.composeapp.generated.resources.b30ex_card_style
import phitracker.composeapp.generated.resources.b30ex_card_style_classic
import phitracker.composeapp.generated.resources.b30ex_card_style_poster
import phitracker.composeapp.generated.resources.b30ex_cd_preview
import phitracker.composeapp.generated.resources.b30ex_export_settings
import phitracker.composeapp.generated.resources.b30ex_generate_failed
import phitracker.composeapp.generated.resources.b30ex_generate_retry
import phitracker.composeapp.generated.resources.b30ex_generating
import phitracker.composeapp.generated.resources.b30ex_image_title
import phitracker.composeapp.generated.resources.b30ex_random_background
import phitracker.composeapp.generated.resources.b30ex_save_failed
import phitracker.composeapp.generated.resources.b30ex_saved_to_pictures
import phitracker.composeapp.generated.resources.b30ex_share_failed
import phitracker.composeapp.generated.resources.b30ex_theme_dark
import phitracker.composeapp.generated.resources.b30ex_theme_follow_global
import phitracker.composeapp.generated.resources.b30ex_theme_light
import phitracker.composeapp.generated.resources.tools_close

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun B30ImageScreen(
    b30: List<BestRecord>,
    displayRks: Float,
    nickname: String,
    challengeModeRank: Int = 0,
    moneyString: String = "",
    clearCounts: Map<String, Int> = emptyMap(),
    fcCount: Int = 0,
    phiCount: Int = 0,
    avatarUri: String? = null,
    showB30Overflow: Boolean = false,
    overflowCount: Int = 9,
    themeSettings: PhiTrackerThemeSettings = PhiTrackerThemeSettings(),
    tagAnalysis: B30TagAnalysis? = null,
    getLowIllustrationUrl: (String) -> String? = { null },
    getStandardIllustrationUrl: (String) -> String = { "" },
    onBack: () -> Unit
) {
    val runState = remember { B30ExportRunState<B30ImageExport>() }
    val export = runState.result
    val isGenerating = runState.isGenerating
    val generationFailed = runState.generationFailed
    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var backgroundMode by remember { mutableStateOf<B30BackgroundMode>(B30BackgroundMode.Auto) }
    var customBackgroundUri by remember { mutableStateOf<String?>(null) }
    var backgroundBlurRadius by remember { mutableFloatStateOf(50f) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val saveSuccessMessage = stringResource(Res.string.b30ex_saved_to_pictures)
    val saveFailedMessage = stringResource(Res.string.b30ex_save_failed)
    val shareFailedMessage = stringResource(Res.string.b30ex_share_failed)
    val settingsRepository = koinInject<SettingsRepository>()
    // Null until the persisted style arrives: building exportData with the
    // wrong style first would waste a full generation pass whose cancellation
    // then races the state flags below (phantom "生成失败" flash).
    val cardStyleKey by settingsRepository.b30CardStyle.collectAsState(initial = null)
    val cardStyle = cardStyleKey?.let(B30ExportCardStyle::fromStorageKey)
    val systemDark = isSystemInDarkTheme()
    // themeMode 0 (follow system) is not a valid manual export choice, so the
    // global mode is resolved to a concrete value before use as the manual default.
    val resolvedGlobalThemeMode = when (themeSettings.themeMode) {
        1 -> 1
        2, 3 -> themeSettings.themeMode
        else -> if (systemDark) 2 else 1
    }
    // Until the persisted values arrive both defaults reproduce the legacy
    // behavior (follow the app theme), so there is no generation race.
    val b30ThemeFollowGlobal by settingsRepository.b30ThemeFollowGlobal.collectAsState(initial = true)
    val b30ExportThemeMode by settingsRepository.b30ExportThemeMode.collectAsState(initial = null)
    val effectiveThemeMode =
        if (b30ThemeFollowGlobal) resolvedGlobalThemeMode
        else b30ExportThemeMode ?: resolvedGlobalThemeMode
    val exportDarkTheme = effectiveThemeMode != 1
    val exportAmoled = effectiveThemeMode == 3

    val pickBackground = rememberB30BackgroundPicker { uri ->
        if (uri != null) {
            AppLogger.event("b30_export", "background_selected", mapOf("type" to "custom", "uriPresent" to "true"))
            customBackgroundUri = uri
            backgroundMode = B30BackgroundMode.Custom(uri)
        } else {
            AppLogger.event("b30_export", "background_picker_cancelled", mapOf("type" to "custom"))
        }
    }

    val distinctSongs = remember(b30) {
        b30.distinctBy { it.songId }.map { it.songId to it.songName }
    }

    // Rolled once per page session: regenerations triggered by style, blur or
    // background-mode changes must keep the same randomly picked song.
    val autoBackgroundSongId = remember(b30) {
        B30ExportDataBuilder.b30Pool(b30).randomOrNull()?.songId
    }

    val exportData = remember(
        b30, displayRks, nickname, challengeModeRank, moneyString,
        clearCounts, fcCount, phiCount, avatarUri,
        showB30Overflow, overflowCount, getLowIllustrationUrl,
        getStandardIllustrationUrl, backgroundMode, customBackgroundUri,
        backgroundBlurRadius,
        exportDarkTheme, exportAmoled, themeSettings, tagAnalysis, cardStyle
    ) {
        val style = cardStyle ?: return@remember null
        val dateText = runCatching {
            val now = Clock.System.now()
            val localDt = now.toLocalDateTime(TimeZone.currentSystemDefault())
            "${localDt.year.toString().padStart(4, '0')}." +
                "${(localDt.month.ordinal + 1).toString().padStart(2, '0')}." +
                "${localDt.day.toString().padStart(2, '0')} " +
                "${localDt.hour.toString().padStart(2, '0')}:" +
                "${localDt.minute.toString().padStart(2, '0')}:" +
                "${localDt.second.toString().padStart(2, '0')}"
        }.getOrDefault("")

        val resolvedBg = resolveBackgroundUri(
            mode = backgroundMode,
            exportData = B30ExportDataBuilder.build(                b30 = b30,
                displayRks = displayRks,
                nickname = nickname,
                challengeModeRank = challengeModeRank,
                moneyString = moneyString,
                showB30Overflow = showB30Overflow,
                overflowCount = overflowCount,
                illustrationProvider = getLowIllustrationUrl,
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount,
                avatarUri = avatarUri,
                backgroundUri = null,
                dateText = dateText,
                darkTheme = exportDarkTheme,
                isAmoled = exportAmoled,
                themeSettings = themeSettings,
                tagAnalysis = tagAnalysis,
                cardStyle = style
            ),
            standardIllustrationProvider = getStandardIllustrationUrl,
            autoSongId = autoBackgroundSongId
        )

        B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = displayRks,
            nickname = nickname,
            challengeModeRank = challengeModeRank,
            moneyString = moneyString,
            showB30Overflow = showB30Overflow,
            overflowCount = overflowCount,
            illustrationProvider = getLowIllustrationUrl,
            clearCounts = clearCounts,
            fcCount = fcCount,
            phiCount = phiCount,
            avatarUri = avatarUri,
            backgroundUri = resolvedBg,
            backgroundBlurRadius = backgroundBlurRadius.roundToInt(),
            dateText = dateText,
            darkTheme = exportDarkTheme,
            isAmoled = exportAmoled,
            themeSettings = themeSettings,
            tagAnalysis = tagAnalysis,
            cardStyle = style
        )
    }

    LaunchedEffect(exportData, runState.retryToken) {
        runB30ExportEffect(
            data = exportData,
            state = runState,
            runner = { runB30Export(it) },
            onStarted = {
                zoomFactor = 1f
                panOffset = Offset.Zero
                AppLogger.event(
                    "b30_export",
                    "generate_started",
                    mapOf(
                        "cards" to b30.size.toString(),
                        "background" to backgroundMode::class.simpleName.orEmpty(),
                        "darkTheme" to exportDarkTheme.toString()
                    )
                )
            },
            onSuccess = { exp ->
                AppLogger.event(
                    "b30_export",
                    "generate_success",
                    mapOf("width" to exp.width.toString(), "height" to exp.height.toString())
                )
            },
            onFailure = { throwable ->
                AppLogger.event("b30_export", "generate_failed", mapOf("error" to (throwable.message ?: "unknown")))
                AppLogger.e("B30ImageScreen", "B30 image generation failed", throwable)
            }
        )
    }

    // Page-level HazeState, independent from the home one: the generated image
    // preview is the source, the top bar and the action bar float above it
    val hazeState = rememberHazeState()
    val glassStyle = rememberGlassHazeStyle()

    Scaffold(
        topBar = {
            GlassTopBar(hazeState = hazeState, style = glassStyle) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.b30ex_image_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBackgroundDialog = true }) {
                            Icon(Icons.Filled.Image, contentDescription = stringResource(Res.string.b30ex_export_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        bottomBar = {
            if (export != null && !isGenerating) {
                GlassBottomBar(hazeState = hazeState, style = glassStyle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val fileName = buildB30ExportFilename(nickname)
                                    val result = saveB30ImageToPictures(export.pngBytes, fileName)
                                    AppLogger.event(
                                        "b30_export",
                                        if (result.isSuccess) "save_success" else "save_failed",
                                        mapOf("fileName" to fileName)
                                    )
                                    showPlatformMessage(
                                        if (result.isSuccess) saveSuccessMessage else saveFailedMessage
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Text(stringResource(Res.string.b30ex_action_save), style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val fileName = buildB30ExportFilename(nickname)
                                    val result = shareB30Image(export.pngBytes, fileName)
                                    AppLogger.event(
                                        "b30_export",
                                        if (result.isSuccess) "share_success" else "share_failed",
                                        mapOf("fileName" to fileName)
                                    )
                                    if (result.isFailure) {
                                        showPlatformMessage(shareFailedMessage)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text(stringResource(Res.string.b30ex_action_share), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Full-bleed preview: the generated image extends behind the glass bars.
        // The off-screen export path never touches this haze source, so exported
        // pixels stay identical to before. Only the gesture viewport is inset by
        // the bars, so zoomed panning can always reach the image edges.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(Res.string.b30ex_generating))
                    }
                }
            } else if (generationFailed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.b30ex_generate_failed))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { runState.retry() }) {
                            Text(stringResource(Res.string.b30ex_generate_retry))
                        }
                    }
                }
            } else {
                export?.let { exp ->
                    val preview = exp.preview
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .onSizeChanged { previewSize = it }
                            .pointerInput(preview) {
                                // Double tap toggles between fit and 2x, since
                                // pinch is not the only way users expect to zoom.
                                detectTapGestures(
                                    onDoubleTap = {
                                        val newZoom = if (zoomFactor > 1.5f) 1f else 2f
                                        zoomFactor = newZoom
                                        if (newZoom == 1f) panOffset = Offset.Zero
                                    }
                                )
                            }
                            .pointerInput(preview) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (zoomFactor * zoom).coerceIn(1f, 2.5f)
                                    zoomFactor = newZoom
                                    // Clamp the pan so the fitted image edge
                                    // never travels inside the viewport.
                                    val containerW = previewSize.width.toFloat()
                                    val containerH = previewSize.height.toFloat()
                                    if (containerW > 0f && containerH > 0f) {
                                        val fitScale = minOf(
                                            containerW / preview.width,
                                            containerH / preview.height
                                        )
                                        val maxX = (
                                            preview.width * fitScale * newZoom - containerW
                                            ).coerceAtLeast(0f) / 2f
                                        val maxY = (
                                            preview.height * fitScale * newZoom - containerH
                                            ).coerceAtLeast(0f) / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = preview,
                            contentDescription = stringResource(Res.string.b30ex_cd_preview),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomFactor,
                                    scaleY = zoomFactor,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }

    if (showBackgroundDialog) {
        val selectedSongId = (backgroundMode as? B30BackgroundMode.SongBackground)?.songId
        BackgroundPickerDialog(
            distinctSongs = distinctSongs,
            selectedSongId = selectedSongId,
            blurRadius = backgroundBlurRadius,
            getLowIllustrationUrl = getLowIllustrationUrl,
            cardStyle = cardStyle ?: B30ExportCardStyle.Classic,
            onCardStyleChange = { style ->
                AppLogger.event("b30_export", "card_style_changed", mapOf("style" to style.storageKey))
                coroutineScope.launch { settingsRepository.setB30CardStyle(style.storageKey) }
            },
            themeFollowGlobal = b30ThemeFollowGlobal,
            exportThemeMode = b30ExportThemeMode ?: resolvedGlobalThemeMode,
            onThemeFollowGlobalChange = { follow ->
                AppLogger.event("b30_export", "theme_follow_global_changed", mapOf("follow" to follow.toString()))
                coroutineScope.launch {
                    // Prefill the manual mode with the current global theme so
                    // turning the switch off never flips the image appearance.
                    if (!follow && b30ExportThemeMode == null) {
                        settingsRepository.setB30ExportThemeMode(resolvedGlobalThemeMode)
                    }
                    settingsRepository.setB30ThemeFollowGlobal(follow)
                }
            },
            onExportThemeModeChange = { mode ->
                AppLogger.event("b30_export", "theme_mode_changed", mapOf("mode" to mode.toString()))
                coroutineScope.launch { settingsRepository.setB30ExportThemeMode(mode) }
            },
            onSelectDefault = {
                AppLogger.event("b30_export", "background_selected", mapOf("type" to "auto"))
                backgroundMode = B30BackgroundMode.Auto
                customBackgroundUri = null
                showBackgroundDialog = false
            },
            onSelectAlbum = {
                AppLogger.event("b30_export", "background_picker_opened", mapOf("type" to "album"))
                showBackgroundDialog = false
                pickBackground()
            },
            onSelectSong = { songId ->
                AppLogger.event("b30_export", "background_selected", mapOf("type" to "song", "songId" to songId))
                backgroundMode = B30BackgroundMode.SongBackground(songId)
                customBackgroundUri = null
                showBackgroundDialog = false
            },
            onBlurRadiusChange = { radius ->
                backgroundBlurRadius = radius
                AppLogger.event("b30_export", "background_blur_changed", mapOf("radius" to radius.roundToInt().toString()))
            },
            onDismiss = { showBackgroundDialog = false }
        )
    }
}

internal const val B30_EXPORT_TOTAL_TIMEOUT_MS = 45_000L
internal const val B30_OPTIONAL_BACKGROUND_TIMEOUT_MS = 10_000L

internal suspend fun <T> withOptionalB30Background(
    timeoutMs: Long = B30_OPTIONAL_BACKGROUND_TIMEOUT_MS,
    loader: suspend () -> T?
): T? {
    val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { loader() }
    if (result == null) {
        AppLogger.event("b30_export", "background_fallback", mapOf("reason" to "timeout_or_unavailable"))
    }
    return result
}

internal suspend fun runB30Export(
    exportData: B30ExportData,
    timeoutMs: Long = B30_EXPORT_TOTAL_TIMEOUT_MS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    preload: suspend (B30ExportData) -> Unit = ::preloadB30ExportImages,
    generate: suspend (B30ExportData) -> B30ImageExport = B30ImageGenerator::generate
): B30ImageExport = withTimeout(timeoutMs) {
    withContext(dispatcher) {
        preload(exportData)
        generate(exportData)
    }
}

internal suspend fun preloadB30ExportImages(
    exportData: B30ExportData,
    preload: suspend (url: String, size: Int, allowHardware: Boolean) -> Result<Unit> = ::preloadIllustrationThumbnail
) {
    // The off-screen capture waits only a short beat after composition, so every
    // illustration must resolve from Coil's memory cache synchronously on the
    // first frame. That only happens when the warmed entry's key matches the
    // consumer's request exactly: the export cards request
    // scoreCardThumbnailSizePx(0.9) px with software bitmaps (allowHardware=false,
    // because the layout is drawn onto a software Canvas). Preloading with a
    // different size — or a hardware bitmap — leaves the cache "warm" on disk yet
    // misses in memory, and the thumbnails still capture blank.
    val cardThumbnailPx = scoreCardThumbnailSizePx(B30_EXPORT_CARD_THUMBNAIL_SCALE)
    val cardUris = buildList {
        exportData.phiRecords.forEach { add(it.illustrationUri) }
        exportData.bestRecords.forEach { add(it.illustrationUri) }
        exportData.overflowRecords.forEach { add(it.illustrationUri) }
    }
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .distinct()

    // The avatar is pinned to B30ExportSpec.avatarSizePx so its Coil
    // memory-cache key matches the export layout's request exactly. The
    // optional background is deliberately excluded: the platform generator
    // decodes it separately with a bounded fallback, so a slow background can
    // never prevent otherwise-ready score artwork from being exported.
    val avatarUri = exportData.avatarUri?.takeIf(String::isNotBlank)

    if (cardUris.isEmpty() && avatarUri == null) return

    coroutineScope {
        val semaphore = Semaphore(6)
        val tasks = buildList {
            cardUris.forEach { uri ->
                add(uri to async {
                    semaphore.withPermit {
                        preload(uri, cardThumbnailPx, false)
                    }
                })
            }
            if (avatarUri != null) {
                add(avatarUri to async {
                    semaphore.withPermit {
                        preload(avatarUri, B30ExportSpec.avatarSizePx, false)
                    }
                })
            }
        }
        val failures = tasks.mapNotNull { (uri, task) ->
            val failure = task.await().exceptionOrNull()
            if (failure is CancellationException) throw failure
            failure?.also { throwable ->
                AppLogger.w(
                    "B30ImageScreen",
                    "B30 export image preload failed: uri=$uri error=${throwable.message ?: throwable::class.simpleName}"
                )
            }
        }
        check(failures.isEmpty()) {
            "B30 export preparation failed for ${failures.size} image(s); refusing to capture incomplete artwork"
        }
        AppLogger.event(
            "b30_export",
            "image_preload_finished",
            mapOf(
                "cardImages" to cardUris.size.toString(),
                "avatarImages" to (if (avatarUri != null) 1 else 0).toString(),
                "backgroundDeferred" to "true"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundPickerDialog(
    distinctSongs: List<Pair<String, String>>,
    selectedSongId: String?,
    blurRadius: Float,
    getLowIllustrationUrl: (String) -> String?,
    cardStyle: B30ExportCardStyle,
    onCardStyleChange: (B30ExportCardStyle) -> Unit,
    themeFollowGlobal: Boolean,
    exportThemeMode: Int,
    onThemeFollowGlobalChange: (Boolean) -> Unit,
    onExportThemeModeChange: (Int) -> Unit,
    onSelectDefault: () -> Unit,
    onSelectAlbum: () -> Unit,
    onSelectSong: (String) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    // Local drag state so the export preview only regenerates once per release
    // instead of on every slider movement.
    var blurDragValue by remember { mutableFloatStateOf(blurRadius) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Image, contentDescription = null) },
        title = { Text(stringResource(Res.string.b30ex_export_settings)) },
        text = {
            // Compact spacing and a two-row background grid keep every control
            // inside one screen on small devices; the scroll stays as a
            // fallback for even shorter viewports.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.b30ex_card_style),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = cardStyle == B30ExportCardStyle.Classic,
                        onClick = { onCardStyleChange(B30ExportCardStyle.Classic) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(Res.string.b30ex_card_style_classic))
                    }
                    SegmentedButton(
                        selected = cardStyle == B30ExportCardStyle.Poster,
                        onClick = { onCardStyleChange(B30ExportCardStyle.Poster) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(Res.string.b30ex_card_style_poster))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.b30ex_theme_follow_global),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = themeFollowGlobal,
                        onCheckedChange = onThemeFollowGlobalChange
                    )
                }
                AnimatedVisibility(visible = !themeFollowGlobal) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val themeLabels = listOf(
                            stringResource(Res.string.b30ex_theme_light),
                            stringResource(Res.string.b30ex_theme_dark),
                            "AMOLED"
                        )
                        themeLabels.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = exportThemeMode == index + 1,
                                onClick = { onExportThemeModeChange(index + 1) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeLabels.size)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(Res.string.b30ex_background_blur),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = blurDragValue,
                        onValueChange = { blurDragValue = it },
                        onValueChangeFinished = { onBlurRadiusChange(blurDragValue) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = blurDragValue.roundToInt().toString(),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Text(
                    text = stringResource(Res.string.b30ex_background),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The random/album actions live inside the grid as its first
                // two cells; on high-dpi devices the grid is the only section
                // allowed to overflow, and it scrolls in place.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(204.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "random_background") {
                        BackgroundActionGridItem(
                            icon = Icons.Filled.Shuffle,
                            label = stringResource(Res.string.b30ex_random_background),
                            onClick = onSelectDefault
                        )
                    }
                    item(key = "album_background") {
                        BackgroundActionGridItem(
                            icon = Icons.Filled.PhotoLibrary,
                            label = stringResource(Res.string.b30ex_album_image),
                            onClick = onSelectAlbum
                        )
                    }
                    items(distinctSongs, key = { it.first }) { (songId, songName) ->
                        val isSelected = selectedSongId == songId
                        SongGridItem(
                            songId = songId,
                            songName = songName,
                            getLowIllustrationUrl = getLowIllustrationUrl,
                            platformContext = platformContext,
                            isSelected = isSelected,
                            onClick = { onSelectSong(songId) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tools_close)) }
        }
    )
}

@Composable
private fun BackgroundActionGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SongGridItem(
    songId: String,
    songName: String,
    getLowIllustrationUrl: (String) -> String?,
    platformContext: coil3.PlatformContext,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val thumbUrl = remember(songId) { getLowIllustrationUrl(songId) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .let { mod -> if (thumbUrl != null) mod.clickable { onClick() } else mod }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (thumbUrl != null) {
                val imageRequest = remember(platformContext, thumbUrl) {
                    ImageRequest.Builder(platformContext)
                        .data(thumbUrl)
                        .size(144)
                        .networkCachePolicy(CachePolicy.READ_ONLY)
                        .crossfade(150)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = songName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = songName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
