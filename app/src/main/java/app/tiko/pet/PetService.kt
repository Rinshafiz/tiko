package app.tiko.pet

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random

class PetService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var usm: UsageStatsManager
    private lateinit var store: Store
    private val h = Handler(Looper.getMainLooper())

    // the pet: a full-width, touch-through strip along the very bottom edge; the tiger slides inside it
    private var winView: FrameLayout? = null
    private var holder: FrameLayout? = null
    private var imgA: ImageView? = null
    private var imgB: ImageView? = null
    private var front = 0
    private var playToken = 0
    private var curAnim: Animator? = null
    private var bubbleView: TextView? = null
    private var bubbleOn = false
    private var gate: FrameLayout? = null
    private var busy = false

    private var curPkg = ""
    private var homePkg = ""
    private var atHome = false
    private var unlockedAt = 0L
    private var lastText = ""
    private var sw = 0
    private val tp = 155 // tiger box in px, from your spec
    private val allow = HashMap<String, Long>()
    private var breakUntil = 0L

    private val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        store = Store(this)
        Bank.load(this); Meta.load(this)
        homePkg = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: ""
        val f = IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_EXPORTED) else registerReceiver(screen, f)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("tiko", "Tiko", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "tiko").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pawfessor is on duty").setContentText("Watching your distractions, quietly.").build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        h.post(poll)
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_USER_PRESENT) {
                unlockedAt = System.currentTimeMillis(); curPkg = ""
                Midnight.openIfPossible(c)
            } else { hidePet(); hideGate(); curPkg = "" }
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "TEST") { showPet(); h.postDelayed({ nag() }, 700); selfHide() }
        if (i?.action == "WIN") { showPet(); h.postDelayed({ nag("win") }, 700); selfHide() }
        return START_STICKY
    }

    // a demo bubble shown over Tiko itself disappears again after a while
    private fun selfHide() { h.postDelayed({ if (curPkg == packageName || curPkg.isEmpty()) hidePet() }, 13000) }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screen) } catch (e: Exception) { }
        hidePet(); hideGate()
        super.onDestroy()
    }

    // ---------- which app is open ----------
    private val poll = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) { }
            h.postDelayed(this, 1500)
        }
    }

    private fun foregroundPkg(): String? {
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 10000, now)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.packageName
        }
        return pkg
    }

    private fun allowed(p: String): Boolean {
        val now = System.currentTimeMillis()
        return now < breakUntil || now < (allow[p] ?: 0L)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        var p = foregroundPkg()
        if (p == null && curPkg.isEmpty() && now - unlockedAt < 6000) p = homePkg
        if (p != null && p != curPkg) {
            curPkg = p; atHome = (p == homePkg)
            hideGate()
            hidePet()
            if (p != packageName) {
                if (store.monitored.contains(p)) { if (allowed(p)) showPet() }
                else if (atHome) showPet(now - unlockedAt < 20000)
            }
        }
        // a guarded app is open and no allowance is running: Pawfessor takes the screen
        if (curPkg != packageName && store.monitored.contains(curPkg) && gate == null && !allowed(curPkg)) showGate(curPkg)
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun v() = Meta.v * tp / Meta.s          // walking speed in screen px per second
    private fun allDone(): Boolean { val g = store.goals(); return g.isNotEmpty() && g.all { it.done } }
    private fun canStretch() = allDone() || store.stretchOk()

    private fun moodRes(m: String) = when (m) {
        "angry" -> R.drawable.tiko_angry
        "judge" -> R.drawable.tiko_judge
        "sad" -> R.drawable.tiko_sad
        "excited" -> R.drawable.tiko_excited
        "xhappy" -> R.drawable.tiko_xhappy
        else -> R.drawable.tiko_happy
    }

    private fun clipFor(m: String) =
        if (m == "xhappy") (if (hasClip("mood_excited")) "mood_excited" else "idle_happy") else "mood_" + m

    private fun restRes() = moodRes(if (allDone()) "xhappy" else "happy")

    private fun hasClip(name: String) = try { assets.open("clips/" + name + ".webp").close(); true } catch (e: Exception) { false }

    private fun ctxNow(): Ctx {
        val g = store.goals()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val ms = Usage.between(this, cal.timeInMillis, System.currentTimeMillis())[curPkg] ?: 0L
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(curPkg, 0)).toString() } catch (e: Exception) { "this app" }
        return Ctx(curPkg, app, store.monitored.contains(curPkg), (ms / 60000).toInt(), g.count { it.done }, g.size,
            g.filter { !it.done }.randomOrNull())
    }

    // ---------- clips, cross-faded so one never "pops" into the next ----------
    private fun play(name: String, loop: Boolean, fallback: Int, onEnd: (() -> Unit)? = null): Boolean {
        val a = imgA ?: return false
        val b = imgB ?: return false
        val back = if (front == 0) b else a
        val fr = if (front == 0) a else b
        val my = ++playToken
        val d: Drawable? = try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(assets, "clips/" + name + ".webp"))
        } catch (e: Exception) { null }
        if (d == null) back.setImageResource(fallback) else {
            back.setImageDrawable(d)
            if (d is AnimatedImageDrawable) {
                d.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
                if (onEnd != null) d.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(dr: Drawable?) { if (my == playToken) onEnd() }
                })
                d.start()
            }
        }
        back.animate().cancel(); fr.animate().cancel()
        back.alpha = 0f
        back.animate().alpha(1f).setDuration(250).start()
        fr.animate().alpha(0f).setDuration(250).start()
        front = 1 - front
        return d != null
    }

    private fun showRest() { play(if (allDone()) "idle_happy" else "idle", true, restRes()) }

    // ---------- the companion pet on the bottom edge ----------
    private fun showPet(entrance: Boolean = false) {
        if (winView != null || !Settings.canDrawOverlays(this)) return
        sw = resources.displayMetrics.widthPixels
        val a = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val b = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0f }
        val hd = FrameLayout(this).apply {
            addView(a, FrameLayout.LayoutParams(tp, tp)); addView(b, FrameLayout.LayoutParams(tp, tp))
            pivotX = tp / 2f; pivotY = tp.toFloat()
        }
        val outer = FrameLayout(this).apply { addView(hd, FrameLayout.LayoutParams(tp, tp)) }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, tp, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 0; y = 0 }   // y = 0: feet on the very edge
        wm.addView(outer, p)
        winView = outer; holder = hd; imgA = a; imgB = b; front = 0; busy = false
        hd.translationX = if (entrance) -tp.toFloat() else (sw - tp).toFloat()
        a.setImageResource(restRes())
        if (entrance) { busy = true; startEntrance() }
        else {
            showRest()
            if (atHome && canStretch()) h.postDelayed({ if (!busy && winView != null) doAct("stretch") }, 2000)
        }
        sched()
    }

    // walks in from the left edge with a real walking cycle, turns and sits in the right corner
    private fun startEntrance() {
        play("walk", true, restRes())
        val ms = (sw / (v() * 1.3f) * 1000).toLong()
        slide((sw - tp).toFloat(), ms) {
            val done: () -> Unit = {
                busy = false; showRest()
                if (atHome && canStretch()) h.postDelayed({ if (!busy && winView != null) doAct("stretch") }, 1500)
            }
            val ok = play("sit", false, restRes()) { done() }
            if (!ok) done()
        }
    }

    private fun hidePet() {
        h.removeCallbacks(actRun); h.removeCallbacks(hideBubbleRun)
        curAnim?.cancel(); holder?.animate()?.cancel()
        hideBubbleNow()
        winView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        winView = null; holder = null; imgA = null; imgB = null; busy = false; playToken++
    }

    // ---------- speech bubble (silent) ----------
    private val hideBubbleRun = Runnable { hideBubble() }

    private fun nag(cat: String? = null) {
        if (winView == null) return
        val c = ctxNow()
        val pk = Bank.pick(store, c, lastText, cat) ?: return
        lastText = pk.first.text
        play(clipFor(pk.first.mood), true, moodRes(pk.first.mood))
        showBubble(pk.second)
    }

    private fun showBubble(text: String) {
        hideBubbleNow()
        val tv = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(0xFF3A2A20.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10)); maxWidth = (sw * 0.7).toInt()
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat(); setColor(0xFFFFF4E6.toInt()); setStroke(dp(2), 0xFFE8A54B.toInt())
            }
            setOnClickListener { hideBubble() }
        }
        tv.measure(View.MeasureSpec.makeMeasureSpec((sw * 0.7).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val w = tv.measuredWidth
        val cx = (holder?.translationX ?: 0f) + tp / 2f
        val bp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FLAGS, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = (cx - w / 2f).toInt().coerceIn(dp(8), (sw - w - dp(8)).coerceAtLeast(dp(8))); y = tp + dp(4)
        }
        wm.addView(tv, bp); bubbleView = tv; bubbleOn = true
        h.removeCallbacks(hideBubbleRun); h.postDelayed(hideBubbleRun, 9000)
    }

    private fun hideBubbleNow() {
        bubbleView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bubbleView = null; bubbleOn = false
    }

    private fun hideBubble() { if (!bubbleOn) return; hideBubbleNow(); if (winView != null) showRest() }

    // ---------- the gate: Pawfessor takes the whole screen first ----------
    private fun showGate(pkg: String) {
        if (gate != null || !Settings.canDrawOverlays(this)) return
        hidePet()
        sw = resources.displayMetrics.widthPixels
        val pk = Bank.pick(store, ctxNow(), lastText)
        val pet = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        Clips.set(this, pet, "mood_judge", R.drawable.tiko_judge)
        val msg = TextView(this).apply {
            textSize = 17f; setTextColor(0xFF3A2A20.toInt()); gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12)); alpha = 0f
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat(); setColor(0xFFFFF4E6.toInt()); setStroke(dp(2), 0xFFE8A54B.toInt())
            }
        }
        val opts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; alpha = 0f; visibility = View.INVISIBLE }
        fun opt(label: String, primary: Boolean, f: () -> Unit) {
            val b = Button(this).apply {
                text = label; isAllCaps = false; textSize = 16f; setOnClickListener { f() }
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF3A2A20.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(if (primary) 0xFFE8892B.toInt() else 0xFFF3CBB8.toInt())
                }
            }
            opts.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
        }
        opt("Close the app", true) { choose(pkg, "close") }
        opt("Just 5 minutes", false) { choose(pkg, "five") }
        opt("I need a break for \u00BD an hour", false) { choose(pkg, "break") }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(0xF8F4E4D9.toInt())
        }
        col.addView(pet, LinearLayout.LayoutParams((sw * 0.6).toInt(), (sw * 0.6).toInt()))
        col.addView(msg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        col.addView(opts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        val root = FrameLayout(this).apply { addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        val gp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, FLAGS, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(root, gp); gate = root
        h.postDelayed({
            if (gate === root) {
                if (pk != null) {
                    lastText = pk.first.text; msg.text = pk.second
                    Clips.set(this, pet, clipFor(pk.first.mood), moodRes(pk.first.mood))
                } else msg.text = "Pause. Is this the best use of the next few minutes?"
                msg.animate().alpha(1f).setDuration(400).start()
            }
        }, 900)
        h.postDelayed({
            if (gate === root) { opts.visibility = View.VISIBLE; opts.animate().alpha(1f).setDuration(500).start() }
        }, 3300)
    }

    private fun hideGate() {
        gate?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        gate = null
    }

    private fun choose(pkg: String, c: String) {
        hideGate()
        val now = System.currentTimeMillis()
        when (c) {
            "close" -> {
                curPkg = ""
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "five" -> { allow[pkg] = now + 5 * 60000L; showPet() }
            else -> { breakUntil = now + 30 * 60000L; showPet() }
        }
    }

    // ---------- cat mannerisms: one at a time, short walks, no jumping between sides ----------
    private val actRun = Runnable { act() }

    private fun sched() { h.removeCallbacks(actRun); h.postDelayed(actRun, 6500L + Random.nextLong(6500L)) }

    private fun act() {
        if (winView == null) return
        if (busy || bubbleOn || gate != null) { sched(); return }
        val can = canStretch()
        val o = mutableListOf("look", "groom", "blink", "breathe")
        if (!atHome) { o.addAll(listOf("walk", "walk", "walk", "roll")); if (can) o.add("stretch") }
        else if (can) o.addAll(listOf("stretch", "stretch", "stretch"))
        doAct(o[Random.nextInt(o.size)])
    }

    private fun doAct(a: String) {
        if (busy || winView == null) return
        busy = true
        val fin: () -> Unit = { busy = false; sched() }
        when (a) {
            "walk" -> walk(fin)
            "roll" -> roll(fin)
            "look" -> look(fin)
            "groom" -> groom(fin)
            "blink" -> blink(fin)
            "breathe" -> breathe(fin)
            else -> stretch(fin)
        }
    }

    private fun runAnim(an: Animator, end: () -> Unit) {
        curAnim = an
        an.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(a: Animator) { cancelled = true }
            override fun onAnimationEnd(a: Animator) { if (!cancelled) end() }
        })
        an.start()
    }

    private fun slide(toX: Float, ms: Long, end: () -> Unit) {
        val hd = holder ?: return
        curAnim?.cancel()
        val an = ObjectAnimator.ofFloat(hd, View.TRANSLATION_X, hd.translationX, toX).apply {
            duration = ms.coerceAtLeast(1L); interpolator = LinearInterpolator()
        }
        runAnim(an, end)
    }

    private fun walk(done: () -> Unit) {
        val hd = holder ?: return done()
        val x = hd.translationX
        val rmax = (sw - tp).toFloat()
        var d = if (Random.nextBoolean()) -1 else 1
        if (x < tp * 0.4f) d = 1
        if (x > rmax - tp * 0.4f) d = -1
        val to = (x + d * tp * (1.0f + Random.nextFloat() * 1.2f)).coerceIn(0f, rmax)
        if (abs(to - x) < tp * 0.6f) return done()
        val ms = (abs(to - x) / v() * 1000).toLong()
        hd.animate().scaleX(if (to > x) 1f else -1f).setDuration(220).withEndAction {
            play("walk", true, restRes())
            slide(to, ms) {
                showRest()
                h.postDelayed({ hd.animate().scaleX(1f).setDuration(220).withEndAction { done() }.start() }, 350)
            }
        }.start()
    }

    private fun roll(done: () -> Unit) {
        val hd = holder ?: return done()
        val x = hd.translationX
        val rmax = (sw - tp).toFloat()
        val to = (x + (if (x < rmax / 2) 1 else -1) * tp * 0.9f).coerceIn(0f, rmax)
        hd.pivotY = tp / 2f
        val set = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(hd, View.TRANSLATION_X, x, to),
                ObjectAnimator.ofFloat(hd, View.ROTATION, 0f, if (to > x) 360f else -360f))
            duration = 1400; interpolator = AccelerateDecelerateInterpolator()
        }
        runAnim(set) { hd.rotation = 0f; hd.pivotY = tp.toFloat(); done() }
    }

    private fun look(done: () -> Unit) {
        val hd = holder ?: return done()
        hd.animate().scaleX(-1f).setDuration(220).withEndAction {
            h.postDelayed({ hd.animate().scaleX(1f).setDuration(220).withEndAction { done() }.start() }, 1100)
        }.start()
    }

    private fun groom(done: () -> Unit) {
        val hd = holder ?: return done()
        runAnim(ObjectAnimator.ofFloat(hd, View.ROTATION, 0f, -6f, 6f, -4f, 0f).apply {
            duration = 1500; interpolator = AccelerateDecelerateInterpolator()
        }) { done() }
    }

    private fun blink(done: () -> Unit) {
        val hd = holder ?: return done()
        runAnim(ObjectAnimator.ofFloat(hd, View.SCALE_Y, 1f, .94f, 1f).apply { duration = 260 }) { done() }
    }

    private fun breathe(done: () -> Unit) {
        val hd = holder ?: return done()
        runAnim(ObjectAnimator.ofFloat(hd, View.SCALE_Y, 1f, 1.03f, 1f).apply {
            duration = 2000; interpolator = AccelerateDecelerateInterpolator()
        }) { done() }
    }

    // only allowed after all goals are done or the night talk is finished
    private fun stretch(done: () -> Unit) {
        val hd = holder ?: return done()
        val sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X, Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(.35f, 1.1f),
            Keyframe.ofFloat(.7f, 1.1f), Keyframe.ofFloat(1f, 1f))
        val sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(.35f, .9f),
            Keyframe.ofFloat(.7f, .9f), Keyframe.ofFloat(1f, 1f))
        runAnim(ObjectAnimator.ofPropertyValuesHolder(hd, sx, sy).apply {
            duration = 2300; interpolator = AccelerateDecelerateInterpolator()
        }) { done() }
    }
}
