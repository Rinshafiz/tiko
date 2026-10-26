package app.tiko.pet

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Midnight {
    private const val DAY = 24L * 3600 * 1000

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Store(ctx).checkinHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val fire = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, MidnightReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val show = PendingIntent.getActivity(ctx, 8, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(cal.timeInMillis, show), fire)
    }

    fun markDue(ctx: Context) {
        val s = Store(ctx); val now = System.currentTimeMillis()
        s.pendingFrom = now - DAY; s.pendingTo = now
    }

    // opens the check-in only when the phone is unlocked; otherwise it waits for the unlock
    fun openIfPossible(ctx: Context) {
        if (Store(ctx).pendingTo == 0L) return
        val kg = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (kg.isKeyguardLocked) return
        ctx.startActivity(Intent(ctx, ChatActivity::class.java).putExtra("checkin", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Midnight.markDue(c); Midnight.schedule(c); Midnight.openIfPossible(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Midnight.schedule(c)
        if (Store(c).running) c.startForegroundService(Intent(c, PetService::class.java))
    }
}
