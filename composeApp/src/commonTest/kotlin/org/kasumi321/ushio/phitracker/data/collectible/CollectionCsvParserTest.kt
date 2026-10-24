package org.kasumi321.ushio.phitracker.data.collectible

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionCsvParserTest {

    private val header =
        "folder,folder_sub,cover,key,sub_index,name,date,supervisor,category,content,properties"

    @Test
    fun headerRowIsSkippedAndColumnsAreMapped() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense,内容,长度=1min"

        val rows = CollectionCsvParser.parse(text)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("额外章节", row.folder)
        assertEquals("", row.folderSub)
        assertEquals("cover.jpg", row.cover)
        assertEquals("mope1", row.key)
        assertEquals(1, row.subIndex)
        assertEquals("收容报告", row.name)
        assertEquals("767/06/01", row.date)
        assertEquals("主管", row.supervisor)
        assertEquals("nonsense", row.category)
        assertEquals("内容", row.content)
        assertEquals("长度=1min", row.properties)
    }

    @Test
    fun quotedFieldKeepsCommas() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,\"Name, with comma\",767/06/01,主管,nonsense,内容,"

        val row = CollectionCsvParser.parse(text).single()

        assertEquals("Name, with comma", row.name)
    }

    @Test
    fun doubledQuotesUnescapeToLiteralQuote() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,\"He said \"\"Hi\"\"\",767/06/01,主管,nonsense,内容,"

        val row = CollectionCsvParser.parse(text).single()

        assertEquals("He said \"Hi\"", row.name)
    }

    @Test
    fun literalBackslashNInContentIsPreservedUnescaped() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense,\"第一行\\n\\n第二行\","

        val row = CollectionCsvParser.parse(text).single()

        // The two-character sequence stays literal; the display layer converts it.
        assertEquals("第一行\\n\\n第二行", row.content)
    }

    @Test
    fun quotedFieldMayContainRecordTerminatorAndComma() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,\"Line1\nLine2, still quoted\",767/06/01,主管,nonsense,内容,"

        val row = CollectionCsvParser.parse(text).single()

        assertEquals("Line1\nLine2, still quoted", row.name)
    }

    @Test
    fun crlfTerminatorsAreAccepted() {
        val text = header + "\r\n" +
            "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense,内容,\r\n" +
            "冰封世界,,cover.jpg,mope2,1,冰封,768/01/01,主管,main,内容,"

        val rows = CollectionCsvParser.parse(text)

        assertEquals(2, rows.size)
        assertEquals("冰封世界", rows[1].folder)
    }

    @Test
    fun finalRecordWithoutTrailingNewlineIsParsed() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense,内容,"

        val rows = CollectionCsvParser.parse(text)

        assertEquals(1, rows.size)
        assertEquals("收容报告", rows.single().name)
    }

    @Test
    fun emptyTextAndHeaderOnlyYieldNoRows() {
        assertEquals(emptyList(), CollectionCsvParser.parse(""))
        assertEquals(emptyList(), CollectionCsvParser.parse(header + "\n"))
    }

    @Test
    fun malformedRowWithTooFewColumnsIsSkipped() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,1,收容报告,767/06/01,主管,nonsense\n" +
            "冰封世界,,cover.jpg,mope2,1,冰封,768/01/01,主管,main,内容,"

        val rows = CollectionCsvParser.parse(text)

        assertEquals(1, rows.size)
        assertEquals("冰封世界", rows.single().folder)
    }

    @Test
    fun nonNumericSubIndexFallsBackToZero() {
        val text = header + "\n" +
            "额外章节,,cover.jpg,mope1,abc,收容报告,767/06/01,主管,nonsense,内容,"

        val row = CollectionCsvParser.parse(text).single()

        assertEquals(0, row.subIndex)
    }
}
