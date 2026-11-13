package io.github.raghavsatyadev.androidworld.core.domain.content

import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

class PimTableTest {

    @Test
    fun `query commands quote the where clause and projection`() {
        PimTable.Contacts.spec.command().render() shouldBe
            "content query --uri 'content://com.android.contacts/data' --projection 'contact_id:display_name:mimetype:data1:starred' " +
            "--where 'mimetype IN ('\\''vnd.android.cursor.item/name'\\'','\\''vnd.android.cursor.item/phone_v2'\\'','\\''vnd.android.cursor.item/email_v2'\\'')' " +
            "--sort 'display_name ASC'"
        PimTable.SmsSent.spec.command().render() shouldBe
            "content query --uri 'content://sms/sent' --projection 'address:date:read:body' --sort 'date DESC'"
    }

    @Test
    fun `contacts fold phones and emails into one row per contact`() {
        val rows = listOf(
            mapOf("contact_id" to "2", "display_name" to "bob", "mimetype" to "vnd.android.cursor.item/phone_v2", "data1" to "1", "starred" to "1"),
            mapOf("contact_id" to "1", "display_name" to "Alice", "mimetype" to "vnd.android.cursor.item/phone_v2", "data1" to "555", "starred" to "0"),
            mapOf("contact_id" to "1", "display_name" to "Alice", "mimetype" to "vnd.android.cursor.item/phone_v2", "data1" to "666", "starred" to "0"),
            mapOf("contact_id" to "1", "display_name" to "Alice", "mimetype" to "vnd.android.cursor.item/email_v2", "data1" to "a@x", "starred" to "0"),
            mapOf("contact_id" to "1", "display_name" to "Alice", "mimetype" to "vnd.android.cursor.item/name", "data1" to "Alice", "starred" to "0"),
        )
        val table = PimTable.Contacts.table(rows, TimeZone.UTC)
        table.headers shouldBe listOf("Name", "Phones", "Emails", "Starred")
        table.rows shouldBe listOf(listOf("Alice", "555; 666", "a@x", "No"), listOf("bob", "1", "", "Yes"))
    }

    @Test
    fun `call log shows readable types and dates`() {
        val rows = listOf(mapOf("number" to "5551234", "name" to null, "type" to "3", "date" to "1763000000000", "duration" to "42"))
        PimTable.CallLog.table(rows, TimeZone.UTC).rows.single() shouldBe
            listOf("5551234", "", "Missed", "2025-11-13T02:13:20", "42")
    }

    @Test
    fun `dates keep seconds and pass non-numbers through`() {
        PimTable.date("1763020800000", TimeZone.UTC) shouldBe "2025-11-13T08:00:00"
        PimTable.date("soon", TimeZone.UTC) shouldBe "soon"
        PimTable.date(null, TimeZone.UTC) shouldBe ""
        PimTable.date("0", TimeZone.UTC) shouldBe ""
    }

    @Test
    fun `csv quotes commas, quotes and line breaks`() {
        val table = PimTableData(listOf("a", "b"), listOf(listOf("x, y", "say \"hi\""), listOf("line1\nline2", "plain")))
        CsvFormat.format(table) shouldBe "a,b\r\n\"x, y\",\"say \"\"hi\"\"\"\r\n\"line1\nline2\",plain\r\n"
    }
}
