package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.CollectibleCategory
import org.kasumi321.ushio.phitracker.domain.model.CollectibleItem
import org.kasumi321.ushio.phitracker.domain.model.CollectiblePart
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCollectibleUseCaseTest {

    private val useCase = SearchCollectibleUseCase()

    private fun part(
        subIndex: Int = 1,
        name: String,
        supervisor: String = "主管"
    ) = CollectiblePart(
        subIndex = subIndex,
        name = name,
        date = "767/06/01",
        supervisor = supervisor,
        category = CollectibleCategory.NONSENSE,
        content = "",
        properties = ""
    )

    private fun item(
        key: String,
        chapterFolderKey: String = "额外章节",
        vararg parts: CollectiblePart
    ) = CollectibleItem(key = key, chapterFolderKey = chapterFolderKey, parts = parts.toList())

    private fun fixture(): List<CollectibleItem> = listOf(
        item("mope1", parts = arrayOf(part(name = "收容报告·小报", supervisor = "中之层收容所"))),
        item("mope2", parts = arrayOf(
            part(name = "收容报告·蝴蝶结"),
            part(subIndex = 2, name = "收容报告·蝴蝶结·线索一", supervisor = "线索主管")
        )),
        item("mope1", chapterFolderKey = "冰封世界", parts = arrayOf(part(name = "冰封物品")))
    )

    @Test
    fun blankQueryReturnsAllItems() {
        val items = fixture()
        assertEquals(3, useCase("", items).size)
        assertEquals(3, useCase("   ", items).size)
    }

    @Test
    fun containsMatchSearchesNameKeyAndSupervisorCaseInsensitively() {
        val items = fixture()

        assertEquals(listOf("冰封物品"), useCase("冰封", items).map { it.displayName })
        assertEquals(listOf("冰封物品", "收容报告·小报"), useCase("MOPE1", items).map { it.displayName })
        assertEquals(listOf("mope2"), useCase("线索主管", items).map { it.key })
    }

    @Test
    fun resultsSortByDisplayName() {
        val items = listOf(
            item("k2", parts = arrayOf(part(name = "Zulu"))),
            item("k1", parts = arrayOf(part(name = "Alpha")))
        )

        // Blank queries return the input order untouched; wildcard queries sort by displayName.
        assertEquals(listOf("Zulu", "Alpha"), useCase("", items).map { it.displayName })
        assertEquals(listOf("Alpha", "Zulu"), useCase("*", items).map { it.displayName })
    }

    @Test
    fun wildcardAsteriskMatchesNonSpaceChars() {
        val items = listOf(
            item("a", parts = arrayOf(part(name = "收容报告"))),
            item("b", parts = arrayOf(part(name = "收容 报告")))
        )

        // 收容* -> regex 收容\S+ ; the space in "收容 报告" is whitespace, so it fails.
        assertEquals(listOf("收容报告"), useCase("收容*", items).map { it.displayName })
    }

    @Test
    fun wildcardQuestionMatchesSingleNonSpaceChar() {
        val items = listOf(
            item("a", parts = arrayOf(part(name = "报告AB"))),
            item("b", parts = arrayOf(part(name = "报告AXB"))),
            item("c", parts = arrayOf(part(name = "报告A B")))
        )

        assertEquals(listOf("报告AXB"), useCase("报告A?B", items).map { it.displayName })
    }

    @Test
    fun wildcardSpaceMatchesOneOrMoreWhitespace() {
        val items = listOf(
            item("a", parts = arrayOf(part(name = "收容 报告"))),
            item("b", parts = arrayOf(part(name = "收容  报告"))),
            item("c", parts = arrayOf(part(name = "收容报告")))
        )

        // 收容 * compiles to 收容\s+\S+ : both single- and double-space match.
        // Sorted by displayName: ' ' (0x20) sorts before '报', so the double-space
        // variant comes first.
        assertEquals(listOf("收容  报告", "收容 报告"), useCase("收容 *", items).map { it.displayName })
    }

    @Test
    fun consecutiveAsterisksGuardReturnsAllItemsImmediately() {
        val items = listOf(
            item("a", parts = arrayOf(part(name = "Alpha"))),
            item("b", parts = arrayOf(part(name = "Beta")))
        )

        assertEquals(2, useCase("**", items).size)
        assertEquals(2, useCase("***", items).size)
        assertEquals(2, useCase("**mope", items).size)
    }

    @Test
    fun spacedAsterisksStillUseRegex() {
        val items = listOf(
            item("a", parts = arrayOf(part(name = "Alpha Beta"))),
            item("b", parts = arrayOf(part(name = "Beta")))
        )

        // "* *" has no consecutive '*' so the guard does not fire.
        assertEquals(listOf("Alpha Beta"), useCase("* *", items).map { it.displayName })
    }

    @Test
    fun matchesAnyPartOfMultiPartItem() {
        val items = fixture()

        // The clue part (sub_index 2) of mope2 is searchable even though the
        // item title comes from part 1.
        assertEquals(listOf("mope2"), useCase("线索一", items).map { it.key })
    }
}
