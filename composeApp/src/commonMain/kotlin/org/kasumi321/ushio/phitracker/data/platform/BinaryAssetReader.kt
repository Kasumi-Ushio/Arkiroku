package org.kasumi321.ushio.phitracker.data.platform

interface BinaryAssetReader {
    fun readBytes(name: String): ByteArray
}

expect fun createBinaryAssetReader(): BinaryAssetReader
