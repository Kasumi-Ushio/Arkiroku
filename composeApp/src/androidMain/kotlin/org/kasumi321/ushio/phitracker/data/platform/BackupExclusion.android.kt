package org.kasumi321.ushio.phitracker.data.platform

/**
 * Android keeps these artifacts out of cloud backup and device transfer via
 * `androidApp/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`; the
 * platform has no meaningful per-path runtime flag to set here.
 */
actual fun markExcludedFromPlatformBackup(path: String) = Unit
