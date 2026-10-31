package org.kasumi321.ushio.phitracker.ui.utils

import org.kasumi321.ushio.phitracker.data.song.CatalogRepairFailure
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.catalog_update_error_busy_message
import phitracker.composeapp.generated.resources.catalog_update_error_network_message
import phitracker.composeapp.generated.resources.catalog_update_error_scene_message
import phitracker.composeapp.generated.resources.catalog_update_error_storage_message
import phitracker.composeapp.generated.resources.catalog_update_error_unknown_message

fun CatalogRepairFailure?.toCatalogRepairErrorText(): UiText = UiText.Res(
    when (this) {
        CatalogRepairFailure.Network -> Res.string.catalog_update_error_network_message
        CatalogRepairFailure.Storage -> Res.string.catalog_update_error_storage_message
        CatalogRepairFailure.Scene -> Res.string.catalog_update_error_scene_message
        CatalogRepairFailure.Busy -> Res.string.catalog_update_error_busy_message
        CatalogRepairFailure.Unknown, null -> Res.string.catalog_update_error_unknown_message
    }
)
