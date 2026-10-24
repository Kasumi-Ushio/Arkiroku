package org.kasumi321.ushio.phitracker.data.platform

import okio.FileSystem
import okio.Path.Companion.toPath

interface TextAssetReader {
    fun readText(name: String): String
}

expect fun createTextAssetReader(): TextAssetReader

/**
 * Creates a [TextAssetReader] that first attempts to read [name] from
 * `filesDir/<subdirectory>/<name>` on the local file-system, and falls back
 * to the bundled [assetReader] when the file does not exist.
 */
fun createFileThenAssetReader(
    assetReader: TextAssetReader,
    paths: PlatformPaths,
    subdirectory: String = "song_data"
): TextAssetReader = FileThenAssetReader(assetReader, paths, subdirectory)

private class FileThenAssetReader(
    private val assetReader: TextAssetReader,
    private val paths: PlatformPaths,
    subdirectory: String
) : TextAssetReader {

    private val fs: FileSystem = platformFileSystem()
    private val dataDir by lazy { paths.filesDir.toPath() / subdirectory }

    override fun readText(name: String): String {
        val filePath = dataDir / name
        if (fs.exists(filePath)) {
            return fs.read(filePath) { readUtf8() }
        }
        return assetReader.readText(name)
    }
}
