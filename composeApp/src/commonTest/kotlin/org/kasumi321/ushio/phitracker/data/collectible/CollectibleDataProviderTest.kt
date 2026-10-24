package org.kasumi321.ushio.phitracker.data.collectible

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectibleLanguage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectibleDataProviderTest {

    private val fs = FileSystem.SYSTEM

    private lateinit var testBaseDir: okio.Path
    private lateinit var paths: PlatformPaths

    private val header =
        "folder,folder_sub,cover,key,sub_index,name,date,supervisor,category,content,properties"

    // Two chapters; key mope1 repeats across both folders. Third row lists
    // sub_index out of order to exercise part sorting.
    private val zhCsv = header + "\n" +
        "额外章节,,cover-ex.jpg,mope1,1,收容报告·小报,767/06/01,中之层收容所,nonsense,内容A\\n第二行,\n" +
        "额外章节,,cover-ex.jpg,mope2,2,收容报告·蝴蝶结·线索,767/06/01,中之层收容所,nonsense,内容B,\n" +
        "额外章节,,cover-ex.jpg,mope2,1,收容报告·蝴蝶结,767/06/01,中之层收容所,nonsense,内容B1,\n" +
        "冰封世界,第一章,cover-ice.jpg,mope1,1,冰封物品,768/01/01,冰之主管,main,内容C,"

    private val enCsv = header + "\n" +
        "Chapter EX,,cover-ex.jpg,mope1,1,[Item Report] Tabloid,767/06/01,Mid-Level Shelter,nonsense,Content A\\nLine 2,\n" +
        "Chapter EX,,cover-ex.jpg,mope2,2,[Item Report] Bow Clue,767/06/01,Mid-Level Shelter,nonsense,Content B,\n" +
        "Chapter EX,,cover-ex.jpg,mope2,1,[Item Report] Bow,767/06/01,Mid-Level Shelter,nonsense,Content B1,\n" +
        "Frozen World,Chapter 1,cover-ice.jpg,mope1,1,Frozen Item,768/01/01,Ice Supervisor,main,Content C,"

    @BeforeTest
    fun setUp() {
        testBaseDir = "/tmp/collectible-provider-test-${hashCode()}".toPath()
        fs.createDirectories(testBaseDir)
        fs.createDirectories(testBaseDir / "cache")
        paths = PlatformPaths(
            filesDir = testBaseDir.toString(),
            cacheDir = (testBaseDir / "cache").toString()
        )
    }

    @AfterTest
    fun tearDown() {
        if (::testBaseDir.isInitialized) {
            fs.deleteRecursively(testBaseDir)
        }
    }

    private fun fakeReader(assets: Map<String, String>): TextAssetReader = object : TextAssetReader {
        override fun readText(name: String): String = assets[name] ?: error("Asset not found: $name")
    }

    private fun provider(assets: Map<String, String>): CollectibleDataProvider =
        CollectibleDataProvider(assetReader = fakeReader(assets), paths = paths)

    private fun bundledAssets(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "collection_zh_cn.csv" to zhCsv,
            "collection_en.csv" to enCsv,
            "collection_version.txt" to "4.0.1.2"
        ) + extra

    private fun writeDataFile(name: String, content: String) {
        val dir = testBaseDir / "collectible_data"
        fs.createDirectories(dir)
        fs.write(dir / name) { writeUtf8(content) }
    }

    @Test
    fun chaptersGroupByZhFolderInFirstAppearanceOrder() {
        val provider = provider(bundledAssets())

        val chapters = provider.getChapters(CollectibleLanguage.ZH_CN)

        assertEquals(listOf("额外章节", "冰封世界"), chapters.map { it.folderKey })
        val ex = chapters[0]
        assertEquals("额外章节", ex.displayName)
        assertEquals("", ex.displaySub)
        assertEquals("RubbishSorting.CtymaxvsNceS", ex.coverSongId)
        val ice = chapters[1]
        assertEquals("冰封世界", ice.displayName)
        assertEquals("第一章", ice.displaySub)
        assertEquals("Dlyrotz.Likey", ice.coverSongId)
    }

    @Test
    fun unknownChapterFallsBackToDefaultCover() {
        val csv = header + "\n" +
            "未来章节,,cover.jpg,mystery1,1,神秘物品,770/01/01,主管,nonsense,内容,"
        val provider = provider(bundledAssets(mapOf("collection_zh_cn.csv" to csv)))

        val chapters = provider.getChapters(CollectibleLanguage.ZH_CN)

        assertEquals(1, chapters.size)
        assertEquals("LeaveAllBehind.rider", chapters.single().coverSongId)
    }

    @Test
    fun itemsGroupByFolderAndKeyWithPartsSortedBySubIndex() {
        val provider = provider(bundledAssets())

        val items = provider.getItems(CollectibleLanguage.ZH_CN)

        assertEquals(3, items.size)
        val bow = items.single { it.chapterFolderKey == "额外章节" && it.key == "mope2" }
        assertEquals("收容报告·蝴蝶结", bow.displayName)
        assertEquals(listOf(1, 2), bow.parts.map { it.subIndex })
        assertEquals("内容B1", bow.parts[0].content)
        assertEquals(CollectibleCategory.NONSENSE, bow.parts[0].category)
        assertEquals("内容A\\n第二行", items.first { it.key == "mope1" }.parts[0].content)
    }

    @Test
    fun duplicateKeyAcrossFoldersStaysDistinct() {
        val provider = provider(bundledAssets())

        val items = provider.getItems(CollectibleLanguage.ZH_CN)
        val exItem = provider.getItem("额外章节", "mope1", CollectibleLanguage.ZH_CN)
        val iceItem = provider.getItem("冰封世界", "mope1", CollectibleLanguage.ZH_CN)

        val exItems = items.filter { it.key == "mope1" }
        assertEquals(2, exItems.size)
        assertEquals(setOf("额外章节", "冰封世界"), exItems.map { it.chapterFolderKey }.toSet())
        assertNotNull(exItem)
        assertNotNull(iceItem)
        assertEquals("收容报告·小报", exItem.displayName)
        assertEquals("冰封物品", iceItem.displayName)
        assertNull(provider.getItem("冰封世界", "mope2", CollectibleLanguage.ZH_CN))
    }

    @Test
    fun localizedCsvAlignsByRowIndexAndLocalizesFields() {
        val provider = provider(bundledAssets())

        val chapters = provider.getChapters(CollectibleLanguage.EN)
        val items = provider.getItems(CollectibleLanguage.EN)

        assertEquals(listOf("Chapter EX", "Frozen World"), chapters.map { it.displayName })
        assertEquals("", chapters[0].displaySub)
        assertEquals("Chapter 1", chapters[1].displaySub)
        // Structure still comes from zh_cn: same item count, same identities.
        assertEquals(3, items.size)
        val tabloid = provider.getItem("额外章节", "mope1", CollectibleLanguage.EN)
        assertNotNull(tabloid)
        assertEquals("[Item Report] Tabloid", tabloid.displayName)
        assertEquals("Mid-Level Shelter", tabloid.parts[0].supervisor)
        assertEquals("Content A\\nLine 2", tabloid.parts[0].content)
    }

    @Test
    fun misalignedLocalizedRowFallsBackToZhCn() {
        val misalignedEn = header + "\n" +
            "Chapter EX,,cover-ex.jpg,mope1,1,[Item Report] Tabloid,767/06/01,Mid-Level Shelter,nonsense,Content A,\n" +
            "Chapter EX,,cover-ex.jpg,WRONG-KEY,1,[Item Report] Bow Clue,767/06/01,Mid-Level Shelter,nonsense,Content B,\n" +
            "Chapter EX,,cover-ex.jpg,mope2,1,[Item Report] Bow,767/06/01,Mid-Level Shelter,nonsense,Content B1,\n" +
            "Frozen World,Chapter 1,cover-ice.jpg,mope1,1,Frozen Item,768/01/01,Ice Supervisor,main,Content C,"
        val provider = provider(bundledAssets(mapOf("collection_en.csv" to misalignedEn)))

        val bow = provider.getItem("额外章节", "mope2", CollectibleLanguage.EN)
        val clue = bow?.parts?.firstOrNull { it.subIndex == 2 }

        assertNotNull(clue)
        // Row 2 of the EN CSV has key WRONG-KEY, so that row reverts to zh_cn.
        assertEquals("收容报告·蝴蝶结·线索", clue.name)
        assertEquals("内容B", clue.content)
        // Rows before and after the mismatch stay localized.
        assertEquals("[Item Report] Tabloid", provider.getItem("额外章节", "mope1", CollectibleLanguage.EN)?.displayName)
        assertEquals("Frozen Item", provider.getItem("冰封世界", "mope1", CollectibleLanguage.EN)?.displayName)
    }

    @Test
    fun emptyLocalizedFieldFallsBackToZhCnField() {
        val sparseEn = header + "\n" +
            "Chapter EX,,cover-ex.jpg,mope1,1,,767/06/01,Mid-Level Shelter,nonsense,Content A,\n" +
            "Chapter EX,,cover-ex.jpg,mope2,2,[Item Report] Bow Clue,767/06/01,,nonsense,,\n" +
            "Chapter EX,,cover-ex.jpg,mope2,1,[Item Report] Bow,767/06/01,Mid-Level Shelter,nonsense,Content B1,\n" +
            "Frozen World,Chapter 1,cover-ice.jpg,mope1,1,Frozen Item,768/01/01,Ice Supervisor,main,Content C,"
        val provider = provider(bundledAssets(mapOf("collection_en.csv" to sparseEn)))

        val tabloid = provider.getItem("额外章节", "mope1", CollectibleLanguage.EN)
        val clue = provider.getItem("额外章节", "mope2", CollectibleLanguage.EN)?.parts?.first { it.subIndex == 2 }

        assertEquals("收容报告·小报", tabloid?.displayName)
        assertEquals("Content A", tabloid?.parts?.first()?.content)
        assertNotNull(clue)
        assertEquals("中之层收容所", clue.supervisor)
        assertEquals("内容B", clue.content)
    }

    @Test
    fun missingLocalizedCsvFallsBackToZhCnStructure() {
        val assets = bundledAssets() - "collection_en.csv"
        val provider = provider(assets)

        val items = provider.getItems(CollectibleLanguage.EN)

        assertEquals(3, items.size)
        assertEquals("收容报告·小报", items.first().displayName)
    }

    @Test
    fun fileOverrideWinsOverBundledAssetAndInvalidationReReads() {
        val provider = provider(bundledAssets())
        assertEquals("收容报告·小报", provider.getItems(CollectibleLanguage.ZH_CN).first().displayName)

        val updatedZh = header + "\n" +
            "额外章节,,cover-ex.jpg,mope1,1,更新后的名字,767/06/01,中之层收容所,nonsense,新内容,"
        writeDataFile("collection_zh_cn.csv", updatedZh)

        // Cached snapshot still serves bundled data until invalidated.
        assertEquals("收容报告·小报", provider.getItems(CollectibleLanguage.ZH_CN).first().displayName)

        provider.invalidateCache()

        assertEquals("更新后的名字", provider.getItems(CollectibleLanguage.ZH_CN).first().displayName)
        assertEquals("4.0.1.2", provider.currentVersionText())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cacheInvalidationEmitsOnSharedFlow() = runTest {
        val provider = provider(bundledAssets())
        provider.getItems(CollectibleLanguage.ZH_CN)

        val collector = launch { provider.cacheInvalidations.first() }
        runCurrent()
        provider.invalidateCache()
        collector.join()

        assertTrue(collector.isCompleted)
    }

    @Test
    fun languageMappingFollowsAppLanguageSetting() {
        assertEquals(CollectibleLanguage.ZH_CN, CollectibleLanguage.fromAppLanguage("zh-Hans") { "en" })
        assertEquals(CollectibleLanguage.ZH_TW, CollectibleLanguage.fromAppLanguage("zh-Hant") { "en" })
        assertEquals(CollectibleLanguage.EN, CollectibleLanguage.fromAppLanguage("en") { "zh-CN" })
        assertEquals(CollectibleLanguage.EN, CollectibleLanguage.fromAppLanguage("fr") { "zh-CN" })
        // "system" resolves through the current-locale supplier.
        assertEquals(CollectibleLanguage.ZH_CN, CollectibleLanguage.fromAppLanguage("system") { "zh-Hans" })
        assertEquals(CollectibleLanguage.ZH_TW, CollectibleLanguage.fromAppLanguage("system") { "zh-Hant-TW" })
        assertEquals(CollectibleLanguage.EN, CollectibleLanguage.fromAppLanguage("system") { "en-US" })
        assertEquals(CollectibleLanguage.ZH_CN, CollectibleLanguage.fromAppLanguage("") { "zh-CN" })
    }
}
