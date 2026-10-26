package app.tiko.pet

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var editing = false
    private val h = Handler(Looper.getMainLooper())
    private var headPet: ImageView? = null

    // Pawfessor stretches here too once everything is done or the night talk is finished
    private val stretchRun = object : Runnable {
        override fun run() {
            val g = store.goals()
            val can = (g.isNotEmpty() && g.all { it.done }) || store.stretchOk()
            headPet?.let {
                if (can) {
                    it.pivotX = it.width / 2f; it.pivotY = it.height.toFloat()
                    val sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X, Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(.35f, 1.12f), Keyframe.ofFloat(.7f, 1.12f), Keyframe.ofFloat(1f, 1f))
                    val sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(.35f, .9f), Keyframe.ofFloat(.7f, .9f), Keyframe.ofFloat(1f, 1f))
                    ObjectAnimator.ofPropertyValuesHolder(it, sx, sy).apply { duration = 2300 }.start()
                }
            }
            h.postDelayed(this, 9000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        Midnight.schedule(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4E4D9.toInt()); setPadding(dp(16), dp(26), dp(16), dp(10))
        }
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render(); h.postDelayed(stretchRun, 4000) }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }

    private fun tv(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(0xFF3A2A20.toInt())
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun icon(s: String, f: () -> Unit) = TextView(this).apply {
        text = s; textSize = 24f; setPadding(dp(12), dp(6), dp(12), dp(6)); setOnClickListener { f() }
    }
    private fun btn(s: String, f: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { f() } }

    private fun render() {
        val goals = store.goals()
        val allDone = goals.isNotEmpty() && goals.all { it.done }
        root.removeAllViews()

        val top = row()
        top.addView(tv("Tiko", 28f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(icon("⚙") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(top)

        if (!(Perm.hasOverlay(this) && Perm.hasUsage(this)))
            root.addView(btn("Finish setup: allow permissions") { startActivity(Intent(this, SettingsActivity::class.java)) })
        if (store.pendingTo != 0L)
            root.addView(btn("Tonight's check-in is waiting. Open it") {
                startActivity(Intent(this, ChatActivity::class.java).putExtra("checkin", true))
            })

        val sc = ScrollView(this)
        val body = col(); sc.addView(body)

        // Pawfessor waits next to the goals heading
        val head = row()
        val pet = ImageView(this)
        headPet = pet
        Clips.set(this, pet, if (allDone) "idle_happy" else "idle", if (allDone) R.drawable.tiko_xhappy else R.drawable.tiko_happy)
        head.addView(pet, LinearLayout.LayoutParams(dp(120), dp(120)))
        val side = col()
        val hrow = row()
        hrow.addView(tv("Goals", 24f, true))
        hrow.addView(icon(if (editing) "✔" else "✏️") { editing = !editing; render() })
        side.addView(hrow)
        side.addView(tv(goals.count { it.done }.toString() + " of " + goals.size + " done", 14f))
        head.addView(side)
        body.addView(head)
        body.addView(tv("Watching: " + Apps.names(this, store) + "  ✎", 13f).apply {
            setPadding(0, dp(2), 0, dp(8)); setOnClickListener { Apps.pick(this@MainActivity, store) { render() } }
        })

        if (editing) {
            val t = EditText(this).apply { hint = "Goal (e.g. Read 10 pages of Chapter 3)" }
            val r = EditText(this).apply { hint = "Why must you finish this early?" }
            body.addView(t); body.addView(r)
            body.addView(btn("Add goal") {
                val gt = t.text.toString().trim(); val gr = r.text.toString().trim()
                if (gt.isEmpty() || gr.isEmpty()) Toast.makeText(this, "A goal needs a title and a reason.", Toast.LENGTH_SHORT).show()
                else { val l = store.goals(); l.add(Goal(System.currentTimeMillis(), gt, gr, false)); store.saveGoals(l); render() }
            })
        }
        if (goals.isEmpty()) body.addView(tv("No goals yet. Tap the pencil to add one.", 14f))

        for (g in goals) {
            body.addView(CheckBox(this).apply {
                text = g.title; isChecked = g.done; textSize = 17f
                setOnCheckedChangeListener { _, c ->
                    val l = store.goals(); l.find { it.id == g.id }?.done = c; store.saveGoals(l)
                    if (c && l.all { it.done } && store.running)
                        startForegroundService(Intent(this@MainActivity, PetService::class.java).setAction("WIN"))
                    render()
                }
            })
            body.addView(tv("Why: " + g.reason, 13f).apply { setPadding(dp(32), 0, 0, dp(4)) })
            if (editing) body.addView(btn("Delete") {
                val l = store.goals(); l.removeAll { it.id == g.id }; store.saveGoals(l); render()
            })
        }
        root.addView(sc, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // chat bar with the animated Pawfessor
        val bar = row().apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFFFFF4E6.toInt()); setStroke(dp(2), 0xFFE8A54B.toInt()) }
            setOnClickListener { startActivity(Intent(this@MainActivity, ChatActivity::class.java)) }
        }
        val chatPet = ImageView(this)
        Clips.set(this, chatPet, "idle_happy", R.drawable.tiko_happy)
        bar.addView(chatPet, LinearLayout.LayoutParams(dp(64), dp(64)))
        val txt = col()
        txt.addView(tv("Chat with Pawfessor", 17f, true))
        txt.addView(tv("Feeling low? Talk to me.", 13f))
        bar.addView(txt, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) })
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
    }
}
