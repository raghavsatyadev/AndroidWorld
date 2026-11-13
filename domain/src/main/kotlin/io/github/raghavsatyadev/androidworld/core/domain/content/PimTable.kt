package io.github.raghavsatyadev.androidworld.core.domain.content

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A read-only table ready to show or export: [headers] and one [rows] entry per record. */
data class PimTableData(val headers: List<String>, val rows: List<List<String>>) {
    companion object {
        val None = PimTableData(emptyList(), emptyList())
    }
}

/**
 * The personal-data providers the viewer reads: what to query and how to turn the raw columns into
 * readable cells (dates, call and message types). Contacts are read from the `data` table so a
 * contact's phones and emails come back in one query, then folded into one row per contact.
 */
/** [noun] names the rows in sentences, e.g. "No sent SMS on this device". */
enum class PimTable(val label: String, val noun: String, val spec: ContentQuerySpec) {
    Contacts(
        "Contacts",
        "contacts",
        ContentQuerySpec(
            uri = "content://com.android.contacts/data",
            columns = listOf("contact_id", "display_name", "mimetype", "data1", "starred"),
            where = "mimetype IN ('$NAME_MIME','$PHONE_MIME','$EMAIL_MIME')",
            sort = "display_name ASC",
        ),
    ),
    SmsInbox("SMS inbox", "received SMS", smsSpec("content://sms/inbox")),
    SmsSent("SMS sent", "sent SMS", smsSpec("content://sms/sent")),
    CallLog(
        "Call log",
        "calls",
        ContentQuerySpec(
            uri = "content://call_log/calls",
            columns = listOf("number", "name", "type", "date", "duration"),
            sort = "date DESC",
        ),
    ),
    Calendar(
        "Calendar",
        "calendar events",
        ContentQuerySpec(
            uri = "content://com.android.calendar/events",
            columns = listOf("title", "dtstart", "dtend", "allDay", "eventLocation", "calendar_displayName", "description"),
            where = "deleted=0",
            sort = "dtstart DESC",
        ),
    ),
    ;

    /** Turns parsed rows into display cells; [zone] renders epoch-millisecond dates. */
    fun table(rows: List<Map<String, String?>>, zone: TimeZone): PimTableData = when (this) {
        Contacts -> contacts(rows)
        SmsInbox, SmsSent -> PimTableData(
            listOf("Address", "Date", "Message", "Read"),
            rows.map { listOf(it.text("address"), date(it["date"], zone), it.text("body"), yesNo(it["read"])) },
        )
        CallLog -> PimTableData(
            listOf("Number", "Name", "Type", "Date", "Duration (s)"),
            rows.map { listOf(it.text("number"), it.text("name"), callType(it["type"]), date(it["date"], zone), it.text("duration")) },
        )
        Calendar -> PimTableData(
            listOf("Title", "Start", "End", "All day", "Location", "Calendar", "Description"),
            rows.map {
                val allDay = it["allDay"] == "1"
                // All-day events are stored as UTC midnights; show them in UTC so the date doesn't shift.
                val eventZone = if (allDay) TimeZone.UTC else zone
                listOf(
                    it.text("title"), date(it["dtstart"], eventZone), date(it["dtend"], eventZone), yesNo(it["allDay"]),
                    it.text("eventLocation"), it.text("calendar_displayName"), it.text("description"),
                )
            },
        )
    }

    private fun contacts(rows: List<Map<String, String?>>): PimTableData {
        val byContact = rows.groupBy { it["contact_id"] ?: "" }
        val out = byContact.values.map { data ->
            fun values(mime: String) = data.filter { it["mimetype"] == mime }.mapNotNull { it["data1"] }.distinct().joinToString("; ")
            val name = data.firstNotNullOfOrNull { it["display_name"] } ?: ""
            listOf(name, values(PHONE_MIME), values(EMAIL_MIME), yesNo(data.firstNotNullOfOrNull { it["starred"] }))
        }.sortedBy { it[0].lowercase() }
        return PimTableData(listOf("Name", "Phones", "Emails", "Starred"), out)
    }

    companion object {
        private fun Map<String, String?>.text(key: String) = this[key] ?: ""

        private fun yesNo(value: String?) = when (value) {
            "1" -> "Yes"
            "0" -> "No"
            else -> value ?: ""
        }

        /** Android `CallLog.Calls.TYPE` codes. */
        fun callType(value: String?): String = when (value) {
            "1" -> "Incoming"
            "2" -> "Outgoing"
            "3" -> "Missed"
            "4" -> "Voicemail"
            "5" -> "Rejected"
            "6" -> "Blocked"
            "7" -> "Answered elsewhere"
            else -> value ?: ""
        }

        /** Epoch milliseconds as `yyyy-MM-ddTHH:mm:ss` in [zone]; anything else is shown as is. */
        fun date(value: String?, zone: TimeZone): String {
            val millis = value?.toLongOrNull() ?: return value ?: ""
            if (millis <= 0) return ""
            val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
            return local.toString().substringBefore('.').let { if (it.count { c -> c == ':' } == 1) "$it:00" else it }
        }
    }
}

private const val NAME_MIME = "vnd.android.cursor.item/name"
private const val PHONE_MIME = "vnd.android.cursor.item/phone_v2"
private const val EMAIL_MIME = "vnd.android.cursor.item/email_v2"

/** Body goes last: it is the column most likely to hold text like `, read=`. */
private fun smsSpec(uri: String) = ContentQuerySpec(
    uri = uri,
    columns = listOf("address", "date", "read", "body"),
    sort = "date DESC",
)
