package org.kasumi321.ushio.phitracker.data.avatar

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AvatarCatalogProviderTest {
    private val fs = FileSystem.SYSTEM
    private lateinit var base: Path
    private lateinit var paths: PlatformPaths
    private var bundledCatalog = "光\t光\nCipher : /2&//<|0\tCipher1\n"

    @BeforeTest
    fun setUp() {
        base = "/tmp/avatar-provider-${hashCode()}-${Random.nextLong().toString(16)}".toPath()
        fs.createDirectories(base)
        fs.createDirectories(base / "cache")
        paths = PlatformPaths(base.toString(), (base / "cache").toString())
    }

    @AfterTest
    fun tearDown() {
        if (::base.isInitialized) fs.deleteRecursively(base)
    }

    private fun provider() = AvatarCatalogProvider(
        assetReader = object : TextAssetReader {
            override fun readText(name: String): String {
                assertEquals(AvatarCatalogProvider.CATALOG_FILE, name)
                return bundledCatalog
            }
        },
        paths = paths
    )

    private fun writeOverride(text: String) {
        val directory = base / AvatarCatalogProvider.DATA_DIRECTORY
        fs.createDirectories(directory)
        fs.write(directory / AvatarCatalogProvider.CATALOG_FILE) { writeUtf8(text) }
    }

    @Test
    fun bundledCatalogPreservesUnicodeAndCipherId() {
        val provider = provider()

        assertEquals(
            listOf("光", "Cipher : /2&//<|0"),
            provider.getAvatars().map { it.id }
        )
        assertEquals("光", provider.findById("光")?.resourceKey)
        assertEquals("Cipher1", provider.findById("Cipher : /2&//<|0")?.resourceKey)
        assertNull(provider.findById("missing"))
    }

    @Test
    fun committedFileReplacesBundledCatalog() {
        writeOverride("下载头像\tdownloaded\n")

        val avatars = provider().getAvatars()

        assertEquals(1, avatars.size)
        assertEquals("下载头像", avatars.single().id)
        assertEquals("downloaded", avatars.single().resourceKey)
    }

    @Test
    fun cacheSurvivesSourceChangesUntilInvalidated() {
        writeOverride("old\toldKey\n")
        val provider = provider()
        assertEquals("old", provider.getAvatars().single().id)

        writeOverride("new\tnewKey\n")
        assertEquals("old", provider.getAvatars().single().id)
        assertNull(provider.findById("new"))

        provider.invalidateCache()
        assertEquals("new", provider.getAvatars().single().id)
        assertEquals("newKey", provider.findById("new")?.resourceKey)
    }

    @Test
    fun bomCrLfAndSingleTerminalNewlineAreAccepted() {
        val provider = provider()
        provider.validateCatalogText("\uFEFF甲\tkeyA\r\n乙\tkeyB\r\n")

        bundledCatalog = "\uFEFF甲\tkeyA\r\n乙\tkeyB\r\n"
        assertEquals(listOf("甲", "乙"), provider.getAvatars().map { it.id })
    }

    @Test
    fun malformedAndDuplicateRecordsAreRejected() {
        val provider = provider()
        val invalidCatalogs = mapOf(
            "empty" to "",
            "BOM only" to "\uFEFF",
            "interior blank line" to "a\tone\n\nb\ttwo",
            "leading blank line" to "\na\tone",
            "one column" to "a",
            "three columns" to "a\tone\textra",
            "blank id" to " \tone",
            "blank key" to "a\t ",
            "duplicate id" to "a\tone\na\ttwo",
            "duplicate key" to "a\tone\nb\tone"
        )

        invalidCatalogs.forEach { (description, text) ->
            assertFailsWith<IllegalArgumentException>(description) {
                provider.validateCatalogText(text)
            }
        }
    }

    @Test
    fun traversalAndSpecialResourceKeysAreRejected() {
        val provider = provider()
        val unsafeKeys = listOf(".", "..", "../secret", "dir/key", "dir\\key", "bad\u0000key")

        unsafeKeys.forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) {
                provider.validateCatalogText("avatar\t$key")
            }
        }
    }
}
