package app.tiko.pet

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

data class Msg(val cat: String, val mood: String, val text: String)

class Ctx(val pkg: String, val app: String, val watched: Boolean, val minutes: Int,
          val done: Int, val total: Int, val goal: Goal?)

object Fields {
    // key, label, example (examples are only hints; nothing personal is stored in the app)
    val all = listOf(
        Triple("she", "Pronoun for your child", "she"),
        Triple("her", "Object pronoun for your child", "her"),
        Triple("little_one", "What you call your child", "little one"),
        Triple("daughter", "daughter / son", "daughter"),
        Triple("parent", "mother / father", "mother"),
        Triple("exam", "Exam you are preparing for", "NET"),
        Triple("exam_month", "Exam month", "June"),
        Triple("exam_date", "Exam date (YYYY-MM-DD, for days left)", "2027-06-15"),
        Triple("coaching", "Coaching you paid for", "NET coaching"),
        Triple("fee", "Fee paid", "30k"),
        Triple("age", "Your age line", "30+"),
        Triple("income", "Your income line", "still earning little"),
        Triple("years", "Years of work experience (in words)", "five"),
        Triple("topic", "Your research/work topic", "your project"),
        Triple("topic_line", "Topic as one phrase", "your topic"),
        Triple("cultures", "What you compare or study", "two big ideas"),
        Triple("chapter", "Chapter/section you are on", "Chapter 3")
    )
}

object Bank {
    private var base: List<Msg> = emptyList()
    private val re = Regex("\\{(\\w+)\\}")

    fun load(ctx: Context) {
        base = ctx.assets.open("messages.txt").bufferedReader().readLines().mapNotNull { parse(it) }
    }

    fun parse(l: String): Msg? {
        if (l.startsWith("#")) return null
        val p = l.split("|", limit = 3)
        return if (p.size == 3 && p[2].isNotBlank()) Msg(p[0].trim(), p[1].trim(), p[2].trim()) else null
    }

    fun all(s: Store) = base + s.customRaw.lines().mapNotNull { parse(it) }

    fun catOf(pkg: String) = when {
        pkg.contains("instagram") -> "insta"
        pkg.contains("pinterest") -> "pin"
        else -> "game"
    }

    private fun value(s: Store, key: String, c: Ctx): String? {
        val v: String? = when (key.lowercase()) {
            "minutes" -> if (c.minutes >= 1) c.minutes.toString() else null
            "count" -> if (c.minutes >= 1) "about " + (c.minutes * 60 / 25) else null
            "n" -> if (c.minutes >= 1) (c.minutes * 60 / 25).toString() else null
            "app" -> c.app
            "done" -> c.done.toString()
            "total" -> c.total.toString()
            "left" -> (c.total - c.done).toString()
            "goal" -> c.goal?.title
            "reason" -> c.goal?.reason
            "days_left" -> try {
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s.prof("exam_date"))).coerceAtLeast(0).toString()
            } catch (e: Exception) { null }
            else -> s.prof(key.lowercase()).ifBlank { null }
        }
        return if (v != null && key[0].isUpperCase()) v.replaceFirstChar { it.uppercase() } else v
    }

    // returns the text with every placeholder filled, or null if any value is missing
    fun fill(s: Store, t: String, c: Ctx): String? {
        var ok = true
        val out = re.replace(t) { m -> value(s, m.groupValues[1], c) ?: run { ok = false; "" } }
        return if (ok) out else null
    }

    fun pick(s: Store, c: Ctx, last: String, cat: String? = null): Pair<Msg, String>? {
        val filled = all(s).mapNotNull { m -> fill(s, m.text, c)?.let { m to it } }.filter { it.second != last }
        val pool = if (cat != null) filled.filter { it.first.cat == cat } else {
            val goalPool = filled.filter { it.first.cat == "goal" }
            val appPool = if (c.watched) filled.filter { it.first.cat == catOf(c.pkg) } else emptyList()
            val gen = filled.filter { it.first.cat in setOf("any", "baby", "phd", "exam") }
            val r = Random.nextInt(100)
            when {
                r < 30 && goalPool.isNotEmpty() -> goalPool
                r < 75 && appPool.isNotEmpty() -> appPool
                gen.isNotEmpty() -> gen
                else -> filled
            }
        }
        return pool.randomOrNull()
    }
}
