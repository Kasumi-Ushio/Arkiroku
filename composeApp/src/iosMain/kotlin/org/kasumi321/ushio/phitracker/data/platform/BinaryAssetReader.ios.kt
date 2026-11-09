package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create

actual fun createBinaryAssetReader(): BinaryAssetReader = IosBinaryAssetReader()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosBinaryAssetReader : BinaryAssetReader {
    override fun readBytes(name: String): ByteArray {
        val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.'))
            ?: error("Asset not found: $name")
        val data = NSData.create(contentsOfFile = path, options = 0u, error = null)
            ?: error("Failed to read asset file: $name")
        return data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt())
            ?: error("Failed to extract bytes from asset: $name")
    }
}
