package jp.fairguide.jimtof2026.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** assets/jimtof2026.json を読み取って FairData に変換する */
object FairDataParser {

    fun parse(json: String): FairData {
        val root = JSONObject(json)
        val info = root.getJSONObject("info")
        return FairData(
            info = FairInfo(
                title = info.getString("title"),
                nameJa = info.getString("nameJa"),
                nameEn = info.getString("nameEn"),
                period = info.getString("period"),
                venue = info.getString("venue"),
                address = info.getString("address"),
                halls = info.getString("halls"),
                area = info.getString("area"),
                scale = info.getString("scale"),
                organizers = info.getString("organizers"),
                registrationUrl = info.getString("registrationUrl"),
                mypageUrl = info.getString("mypageUrl"),
                officialUrl = info.getString("officialUrl"),
                verifiedOn = info.getString("verifiedOn"),
                sourceNote = info.getString("sourceNote"),
            ),
            days = root.getJSONArray("days").objects().map { o ->
                FairDay(
                    date = LocalDate.parse(o.getString("date")),
                    open = LocalTime.parse(o.getString("open")),
                    close = LocalTime.parse(o.getString("close")),
                )
            },
            admission = root.getJSONArray("admission").objects().map { o ->
                Admission(o.getString("label"), o.getString("price"), o.text("note"))
            },
            visitorNotes = root.getJSONArray("visitorNotes").strings(),
            notices = root.getJSONArray("notices").objects().map { o ->
                Notice(o.getString("text"), o.textOrNull("until")?.let { LocalDateTime.parse(it) })
            },
            halls = root.getJSONArray("halls").objects().map { o ->
                Hall(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    location = o.getString("location"),
                    prefixes = o.optJSONArray("prefixes")?.strings().orEmpty(),
                    categoryLabel = o.textOrNull("categoryLabel") ?: "出展分野",
                    categories = o.getJSONArray("categories").strings(),
                    highlights = o.optJSONArray("highlights")?.strings().orEmpty(),
                    mapUrl = o.textOrNull("mapUrl"),
                    pdfUrl = o.textOrNull("pdfUrl"),
                )
            },
            hallNotes = root.getJSONArray("hallNotes").strings(),
            events = root.getJSONArray("events").objects().map { o ->
                FairEvent(
                    id = o.getString("id"),
                    date = LocalDate.parse(o.getString("date")),
                    start = o.textOrNull("start")?.let { LocalTime.parse(it) },
                    end = o.textOrNull("end")?.let { LocalTime.parse(it) },
                    category = EventCategory.fromKey(o.getString("cat")),
                    label = o.getString("label"),
                    title = o.getString("title"),
                    speaker = o.text("speaker"),
                    place = o.text("place"),
                    entry = o.text("entry"),
                    url = o.textOrNull("url"),
                )
            },
            scheduleNotes = root.getJSONArray("scheduleNotes").strings(),
            access = root.getJSONArray("access").objects().map { o ->
                InfoItem(o.getString("title"), o.getString("body"))
            },
            linkGroups = root.getJSONArray("links").objects().map { group ->
                LinkGroup(
                    title = group.getString("group"),
                    items = group.getJSONArray("items").objects().map { o ->
                        LinkItem(o.getString("title"), o.text("desc"), o.getString("url"))
                    },
                )
            },
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    // Android の org.json は JSON の null を "null" という文字列で返すことがあるため、自前で判定する
    private fun JSONObject.textOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.text(key: String): String = textOrNull(key) ?: ""
}
