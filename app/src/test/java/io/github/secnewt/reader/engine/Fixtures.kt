package io.github.secnewt.reader.engine

import org.json.JSONObject

/** Loads a probe capture from src/test/resources/fixtures (needs Robolectric for org.json). */
object Fixtures {
    fun load(name: String): Screen {
        val json = JSONObject(javaClass.classLoader!!.getResource("fixtures/$name.json")!!.readText())
        val windows = json.getJSONArray("windows")
        val window = (0 until windows.length()).map { windows.getJSONObject(it) }.first { it.getBoolean("active") }
        return Screen(json.getString("activePackage"), window.getString("title"), node(window.getJSONObject("root")).withPaths())
    }

    private fun node(j: JSONObject): ScreenNode {
        val b = j.getString("b").split(" ").map(String::toInt)
        val kids = j.optJSONArray("kids")
        return ScreenNode(
            cls = j.getString("cls"),
            text = j.optString("text").ifEmpty { null },
            desc = j.optString("desc").ifEmpty { null },
            hint = j.optString("hint").ifEmpty { null },
            viewId = j.optString("id").ifEmpty { null },
            clickable = j.optBoolean("click"),
            scrollable = j.optBoolean("scroll"),
            editable = j.optBoolean("edit"),
            password = j.optBoolean("password"),
            heading = j.optBoolean("heading"),
            visible = j.optBoolean("visible", true),
            bounds = Bounds(b[0], b[1], b[2], b[3]),
            children = if (kids == null) emptyList() else (0 until kids.length()).map { node(kids.getJSONObject(it)) },
        )
    }
}
