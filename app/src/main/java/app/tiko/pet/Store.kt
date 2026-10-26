package app.tiko.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Goal(val id: Long, val title: String, val reason: String, var done: Boolean)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tiko", Context.MODE_PRIVATE)

    fun goals(): MutableList<Goal> {
        val a = JSONArray(sp.getString("goals", "[]"))
        val l = mutableListOf<Goal>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            l.add(Goal(o.getLong("id"), o.getString("t"), o.getString("r"), o.getBoolean("d")))
        }
        return l
    }

    fun saveGoals(l: List<Goal>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("r", it.reason).put("d", it.done)) }
        sp.edit().putString("goals", a.toString()).apply()
    }

    var monitored: Set<String>
        get() = HashSet(sp.getStringSet("mon", setOf("com.instagram.android", "com.pinterest"))!!)
        set(v) { sp.edit().putStringSet("mon", HashSet(v)).apply() }

    var running: Boolean
        get() = sp.getBoolean("run", false)
        set(v) { sp.edit().putBoolean("run", v).apply() }

    // personal data used to fill {placeholders}; stays on the phone
    fun prof(k: String): String = sp.getString("p_$k", "") ?: ""
    fun setProf(k: String, v: String) { sp.edit().putString("p_$k", v.trim()).apply() }
    fun profileText(): String = Fields.all.map { it.first to prof(it.first) }.filter { it.second.isNotBlank() }
        .joinToString("; ") { it.first + ": " + it.second }

    var apiKey: String
        get() = sp.getString("api", "") ?: ""
        set(v) { sp.edit().putString("api", v.trim()).apply() }
    var model: String
        get() = sp.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(v) { sp.edit().putString("model", v.trim()).apply() }
    var checkinHour: Int
        get() = sp.getInt("cih", 0)
        set(v) { sp.edit().putInt("cih", v.coerceIn(0, 23)).apply() }
    var customRaw: String
        get() = sp.getString("custom", "") ?: ""
        set(v) { sp.edit().putString("custom", v).apply() }
    var pendingFrom: Long
        get() = sp.getLong("pfrom", 0L)
        set(v) { sp.edit().putLong("pfrom", v).apply() }
    var pendingTo: Long
        get() = sp.getLong("pto", 0L)
        set(v) { sp.edit().putLong("pto", v).apply() }
    var stretchUntil: Long
        get() = sp.getLong("stretch", 0L)
        set(v) { sp.edit().putLong("stretch", v).apply() }
    fun stretchOk() = System.currentTimeMillis() < stretchUntil
}
