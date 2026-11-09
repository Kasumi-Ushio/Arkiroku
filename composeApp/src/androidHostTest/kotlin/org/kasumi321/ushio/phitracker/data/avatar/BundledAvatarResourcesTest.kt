package org.kasumi321.ushio.phitracker.data.avatar

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Validates the real bundled catalog and every PNG it references. */
class BundledAvatarResourcesTest {
    private val assetsDir: Path? = System.getProperty("phitracker.projectDir")
        ?.let { Path.of(it, "composeApp", "src", "androidMain", "assets") }

    @Test
    fun everyCatalogEntryHasAnExistingPngWithAValidSignature() {
        val dir = assetsDir ?: return
        val catalog = dir.resolve(AvatarCatalogProvider.CATALOG_FILE)
        val lines = Files.readString(catalog).lines().filter { it.isNotEmpty() }
        assertTrue(lines.isNotEmpty(), "Bundled avatar catalog must not be empty")

        val missing = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val columns = line.removeSuffix("\r").split('\t')
            assertTrue(
                columns.size == 2 && columns[1].isNotBlank(),
                "Malformed avatar catalog row ${index + 1}: $line"
            )
            val key = columns[1]
            val png = dir.resolve("avatar.$key.png")
            if (!Files.isRegularFile(png)) {
                missing += "row ${index + 1}: $key"
                return@forEachIndexed
            }
            val bytes = Files.readAllBytes(png)
            if (bytes.size < PNG_SIGNATURE.size ||
                !PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
            ) {
                invalid += "row ${index + 1}: $key"
            }
        }

        assertTrue(missing.isEmpty(), "Catalog entries without bundled PNGs: $missing")
        assertTrue(invalid.isEmpty(), "Bundled avatar files with invalid PNG signatures: $invalid")
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
