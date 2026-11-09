package org.kasumi321.ushio.phitracker.data.platform

actual fun createBinaryAssetReader(): BinaryAssetReader = AndroidBinaryAssetReader()

private class AndroidBinaryAssetReader : BinaryAssetReader {
    override fun readBytes(name: String): ByteArray {
        val context = AndroidPlatformContext.applicationContext
            ?: throw IllegalStateException(
                "AndroidPlatformContext.applicationContext not initialized. " +
                    "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
            )
        return context.assets.open(name).use { it.readBytes() }
    }
}
