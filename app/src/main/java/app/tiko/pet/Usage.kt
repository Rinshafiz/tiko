package app.tiko.pet

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

object Usage {
    fun between(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val openAt = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                openAt[e.packageName] = e.timeStamp
            } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                val st = openAt.remove(e.packageName)
                if (st != null) total[e.packageName] = (total[e.packageName] ?: 0L) + (e.timeStamp - st)
            }
        }
        for ((p, st) in openAt) { total[p] = (total[p] ?: 0L) + (to - st) }
        return total
    }

    fun fmt(ms: Long): String { val m = ms / 60000; return if (m >= 60) (m / 60).toString() + "h " + (m % 60) + "m" else m.toString() + "m" }

    // "Instagram 2h 10m, Pinterest 55m" and the total, for the apps Pawfessor watches
    fun summary(ctx: Context, s: Store, from: Long, to: Long): Pair<String, Long> {
        val pm = ctx.packageManager
        val u = between(ctx, from, to)
        val rows = s.monitored.mapNotNull { p ->
            val ms = u[p] ?: 0L
            if (ms < 60000) null else {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }
                Triple(label, ms, 0)
            }
        }.sortedByDescending { it.second }
        return rows.joinToString(", ") { it.first + " " + fmt(it.second) } to rows.sumOf { it.second }
    }
}
