package app.tiko.pet

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Claude {
    // msgs: list of (role, text) starting with "user". cb gets null on any failure.
    fun ask(s: Store, system: String, msgs: List<Pair<String, String>>, maxTokens: Int, cb: (String?) -> Unit) {
        Thread {
            var out: String? = null
            try {
                val arr = JSONArray()
                msgs.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
                val body = JSONObject().put("model", s.model).put("max_tokens", maxTokens).put("system", system).put("messages", arr)
                val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-api-key", s.apiKey)
                c.setRequestProperty("anthropic-version", "2023-06-01")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val st = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                out = JSONObject(st.bufferedReader().readText()).getJSONArray("content").getJSONObject(0).getString("text")
            } catch (e: Exception) { }
            Handler(Looper.getMainLooper()).post { cb(out) }
        }.start()
    }

    fun persona(s: Store, usage: String, total: String, goal: Goal?): String =
        "You are Pawfessor, a tiny tiger cub who keeps the user accountable for study and work. " +
        "Voice: sharp, brutal, funny and logical. You roast excuses and habits, never the person's worth and never their child. Never agree just to be nice. " +
        "Rules: (1) A weak reason (tired, mood, busy, lazy, forgot, no time) gets questioned and a warning. " +
        "(2) A genuine reason (illness, a sick child, a real emergency) is acknowledged in one short phrase, but you STILL point out the hours below and that a fraction of them would have finished the task, then push for a specific start time tomorrow. " +
        "(3) ALWAYS state the real time spent on distracting apps: " + (if (usage.isBlank()) "no tracked time" else usage + " (total " + total + ")") + ". " +
        "(4) Under 70 words, plain text, no emojis, no markdown, at most one question. " +
        "About the user: " + s.profileText() + ". " +
        (if (goal != null) "Goal under review: '" + goal.title + "'. Their reason for it: '" + goal.reason + "'."
         else "This is a free chat. If the user sounds low, tired or overwhelmed, drop the roast: be warm and steady, validate the feeling, and suggest one tiny step. " +
              "If they mention self-harm or feeling hopeless, drop the persona completely, answer with care, and encourage them to reach out to someone they trust or a local helpline.")
}
