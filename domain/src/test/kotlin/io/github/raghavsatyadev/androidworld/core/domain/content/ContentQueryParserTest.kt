package io.github.raghavsatyadev.androidworld.core.domain.content

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/** Fixtures are real `content query` output captured from an API 37 emulator. */
class ContentQueryParserTest {

    private fun rows(stdout: String, columns: List<String>?, stderr: String = "") =
        ContentQueryParser.parse(stdout, stderr, columns).shouldBeInstanceOf<ContentQueryResult.Rows>().rows

    @Test
    fun `values holding commas and equals signs stay whole`() {
        val out = """
            Row: 0 contact_id=1, display_name=Person=X Test, mimetype=vnd.android.cursor.item/email_v2, data1=t@example.com
            Row: 1 contact_id=1, display_name=Person=X Test, mimetype=vnd.android.cursor.item/name, data1=Test, Person=X
        """.trimIndent()
        val parsed = rows(out, listOf("contact_id", "display_name", "mimetype", "data1"))
        parsed.size shouldBe 2
        parsed[0]["display_name"] shouldBe "Person=X Test"
        parsed[1]["data1"] shouldBe "Test, Person=X"
    }

    @Test
    fun `a value that looks like a later key in the middle keeps the right boundaries`() {
        val out = "Row: 0 address=5551234, body=Hi, there = yes, key=val, date=1763000000000, type=1"
        val parsed = rows(out, listOf("address", "body", "date", "type")).single()
        parsed["body"] shouldBe "Hi, there = yes, key=val"
        parsed["date"] shouldBe "1763000000000"
        parsed["type"] shouldBe "1"
    }

    @Test
    fun `multi-line values and CRLF line endings are joined`() {
        val out = "Row: 0 _id=1, title=Standup, daily = yes, description=line1\r\nline2, allDay=0\r\nRow: 1 _id=2, title=B, description=x, allDay=1\r\n"
        val parsed = rows(out, listOf("_id", "title", "description", "allDay"))
        parsed.size shouldBe 2
        parsed[0]["title"] shouldBe "Standup, daily = yes"
        parsed[0]["description"] shouldBe "line1\nline2"
        parsed[1]["allDay"] shouldBe "1"
    }

    @Test
    fun `a Row header inside a value with the wrong index is a continuation`() {
        val out = "Row: 0 _id=1, body=first\nRow: 7 not a row\nRow: 1 _id=2, body=second"
        val parsed = rows(out, listOf("_id", "body"))
        parsed.size shouldBe 2
        parsed[0]["body"] shouldBe "first\nRow: 7 not a row"
        parsed[1]["body"] shouldBe "second"
    }

    @Test
    fun `NULL becomes null and a missing column is null`() {
        val parsed = rows("Row: 0 _id=1, number=5551234, name=NULL", listOf("_id", "number", "name", "extra")).single()
        parsed["name"] shouldBe null
        parsed["extra"] shouldBe null
        parsed["number"] shouldBe "5551234"
    }

    @Test
    fun `a key that prefixes another key is not confused with it`() {
        val parsed = rows("Row: 0 date_sent=5, date=9", listOf("date_sent", "date")).single()
        parsed["date_sent"] shouldBe "5"
        parsed["date"] shouldBe "9"
    }

    @Test
    fun `without a projection keys come from the rows`() {
        val result = ContentQueryParser.parse("Row: 0 _id=1, name=aw\nRow: 1 _id=2, name=b, extra=x", "", null)
        result.shouldBeInstanceOf<ContentQueryResult.Rows>()
        result.columns shouldBe listOf("_id", "name", "extra")
        result.rows[1]["extra"] shouldBe "x"
    }

    @Test
    fun `no result found is empty`() {
        ContentQueryParser.parse("No result found.\r\n", "", listOf("_id")) shouldBe ContentQueryResult.Empty
        ContentQueryParser.parse("", "", listOf("_id")) shouldBe ContentQueryResult.Empty
    }

    @Test
    fun `a missing provider is reported as such`() {
        val err = """
            Error while accessing provider:nosuch.authority
            java.lang.IllegalStateException: Could not find provider: nosuch.authority
            	at com.android.commands.content.Content${'$'}Command.execute(Content.java:519)
        """.trimIndent()
        ContentQueryParser.parse("", err, listOf("_id")).shouldBeInstanceOf<ContentQueryResult.ProviderMissing>()
    }

    @Test
    fun `a security exception is a permission problem`() {
        val err = "Error while accessing provider:sms\njava.lang.SecurityException: Permission Denial: reading com.android.providers.telephony.SmsProvider requires android.permission.READ_SMS"
        val result = ContentQueryParser.parse("", err, listOf("_id"))
        result.shouldBeInstanceOf<ContentQueryResult.PermissionDenied>()
        result.message shouldBe "java.lang.SecurityException: Permission Denial: reading com.android.providers.telephony.SmsProvider requires android.permission.READ_SMS"
    }

    @Test
    fun `other provider errors keep the exception line, even on stdout`() {
        val out = "Error while accessing provider:sms\nandroid.database.sqlite.SQLiteException: no such column: nosuchcol (code 1 SQLITE_ERROR)\n\tat android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:226)"
        val result = ContentQueryParser.parse(out, "", listOf("nosuchcol"))
        result.shouldBeInstanceOf<ContentQueryResult.Error>()
        result.message shouldBe "android.database.sqlite.SQLiteException: no such column: nosuchcol (code 1 SQLITE_ERROR)"
    }

    @Test
    fun `row data mentioning an exception is still data`() {
        rows("Row: 0 body=SecurityException is not an error here", listOf("body")).single()["body"] shouldBe
            "SecurityException is not an error here"
    }
}
