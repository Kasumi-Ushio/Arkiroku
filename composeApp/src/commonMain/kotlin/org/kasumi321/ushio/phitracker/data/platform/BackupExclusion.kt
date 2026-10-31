package org.kasumi321.ushio.phitracker.data.platform

/**
 * Best-effort platform hook that keeps transaction/fallback artifacts out of
 * OS-managed backups and device transfers.
 *
 * The song/collectible update engine calls this for every backup scene, repair
 * marker and quarantined dataset. Android excludes the same paths through its
 * backup rules XML (no runtime API is needed); iOS sets
 * `NSURLIsExcludedFromBackupKey` on the file or directory because its persistent
 * data lives under Documents, which is backed up by default.
 *
 * Exclusion failures are never fatal: a scene that still arrives on a new
 * device is fail-closed by the on-disk transaction state rules.
 */
expect fun markExcludedFromPlatformBackup(path: String)
