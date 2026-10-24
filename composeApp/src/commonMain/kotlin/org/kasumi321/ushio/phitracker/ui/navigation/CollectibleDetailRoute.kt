package org.kasumi321.ushio.phitracker.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation route for the collectible detail screen.
 *
 * Mirrors [SongDetailRoute]: Navigation Compose derives the route pattern and
 * argument encoding from [Serializable], so the non-ASCII zh_cn [folderKey]
 * travels without manual percent-encoding. Keep the payload limited to
 * Navigation-supported primitive types — no custom enum fields.
 *
 * ([folderKey], [key]) together identify one collectible item; the same key
 * can repeat across chapters, so the chapter part is not optional.
 */
@Serializable
data class CollectibleDetailRoute(val folderKey: String, val key: String)
