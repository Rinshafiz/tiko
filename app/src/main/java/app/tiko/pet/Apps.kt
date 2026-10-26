package app.tiko.pet

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent

object Apps {
    fun names(ctx: Context, s: Store): String {
        val pm = ctx.packageManager
        return s.monitored.map { p ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }
        }.sorted().joinToString(", ").ifEmpty { "nothing yet" }
    }

    // lets the user tick which apps Pawfessor guards
    fun pick(a: Activity, s: Store, after: () -> Unit) {
        val pm = a.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != a.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        val sel = s.monitored.toMutableSet()
        AlertDialog.Builder(a).setTitle("Apps Pawfessor guards")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), BooleanArray(apps.size) { sel.contains(apps[it].first) }) { _, i, on ->
                if (on) sel.add(apps[i].first) else sel.remove(apps[i].first)
            }
            .setPositiveButton("Save") { _, _ -> s.monitored = sel; after() }
            .setNegativeButton("Cancel", null).show()
    }
}
