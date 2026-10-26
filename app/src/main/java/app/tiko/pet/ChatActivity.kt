package app.tiko.pet

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

class ChatActivity : Activity() {
    private lateinit var store: Store
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: Button
    private var queue = mutableListOf<Goal>()
    private var cur: Goal? = null
    private var checkin = false
    private var usageText = ""
    private var usageMs = 0L
    private val hist = mutableListOf<Pair<String, String>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this); Bank.load(this)
        checkin = intent.getBooleanExtra("checkin", false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4E4D9.toInt()) }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(24), dp(14), dp(8))
        }
        val pet = ImageView(this)
        Clips.set(this, pet, if (checkin) "mood_judge" else "idle_happy", if (checkin) R.drawable.tiko_judge else R.drawable.tiko_happy)
        head.addView(pet, LinearLayout.LayoutParams(dp(88), dp(88)))
        head.addView(TextView(this).apply {
            text = if (checkin) "Night check-in" else "Chat with Pawfessor"
            textSize = 20f; setTextColor(0xFF3A2A20.toInt()); setPadding(dp(10), 0, 0, 0); setTypeface(typeface, Typeface.BOLD)
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)) }
        scroll = ScrollView(this).apply { addView(list) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(6), dp(10), dp(10)) }
        input = EditText(this).apply { hint = "Type here..."; maxLines = 4 }
        send = Button(this).apply { text = "Send"; isAllCaps = false; setOnClickListener { onSend() } }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(send)
        root.addView(head)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar)
        setContentView(root)
        if (checkin) startCheckin() else startFree()
    }

    private fun bubble(text: String, mine: Boolean) {
        val tv = TextView(this).apply {
            this.text = text; textSize = 15f; setTextColor(0xFF3A2A20.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8))
            maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(if (mine) 0xFFD8E8F5.toInt() else 0xFFFFF4E6.toInt())
            }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (mine) Gravity.END else Gravity.START; topMargin = dp(6)
        }
        list.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addPet(t: String) = bubble(t, false)

    // ---------- free chat ----------
    private fun startFree() {
        addPet("Talk to me. Feeling low, stuck, or inventing excuses? I'll be kind or honest, whichever you need.")
        if (store.apiKey.isBlank()) addPet("Add your Claude API key in Settings so I can think properly. Until then I can only keep it short.")
    }

    private fun offlineFree(t: String): String {
        val l = t.lowercase()
        return if (listOf("sad", "low", "tired", "cry", "can't", "cant", "overwhelm", "stress", "anxious", "hopeless").any { l.contains(it) })
            "Rough day? That's allowed. Rest your head for a minute, then pick one tiny step: ten minutes, one page. You don't have to feel ready."
        else "Fine. What is the smallest next step you could do in ten minutes? Say it, then do it."
    }

    // ---------- night check-in ----------
    private fun startCheckin() {
        val to: Long; val from: Long
        if (store.pendingTo != 0L) { from = store.pendingFrom; to = store.pendingTo }
        else { to = System.currentTimeMillis(); from = to - 24L * 3600 * 1000 }
        store.pendingFrom = 0L; store.pendingTo = 0L
        val sm = Usage.summary(this, store, from, to)
        usageText = sm.first; usageMs = sm.second
        queue = store.goals().filter { !it.done }.toMutableList()
        val total = store.goals().size
        addPet(listOf("So. How did today go? Honestly.", "You told me " + total + " tasks. What happened?", "I waited all day. What got in the way?")
            .let { it[Random.nextInt(it.size)] })
        if (usageText.isNotBlank()) addPet("Your phone says: " + usageText + ". " + Usage.fmt(usageMs) + " in total.")
        else addPet("I couldn't read your screen time. Allow usage access in Settings. Until then, I'll assume it was not zero.")
        if (queue.isEmpty()) { addPet("Every goal is done. Fine. I'm impressed. Stretching."); finishCheckin(); return }
        askNext()
    }

    private fun finishCheckin() { store.stretchUntil = System.currentTimeMillis() + 24L * 3600 * 1000 }

    private fun askNext() {
        cur = if (queue.isEmpty()) null else queue.removeAt(0)
        val g = cur
        if (g == null) {
            addPet("That's all for tonight. Tomorrow, pick the first free hour and start. I'll be watching.")
            finishCheckin()
        } else addPet("'" + g.title + "' is not done. Why? You told me: " + g.reason)
    }

    private val genuine = listOf("sick", "fever", "hospital", "unwell", "emergency", "doctor", "funeral", "accident",
        "vomit", "injur", "surgery", "not well", "no power", "ill ")

    private fun offline(g: Goal, text: String): String {
        val real = genuine.any { (" " + text.lowercase() + " ").contains(it) }
        val hrs = if (usageText.isBlank()) "your screen time" else usageText + " (" + Usage.fmt(usageMs) + " in total)"
        val ask = " Tomorrow: what exact time do you start?"
        val opts = if (real) listOf(
            "That sounds real, I'll give you that. But you still spent " + hrs + ". A fraction of that finishes '" + g.title + "'." + ask,
            "Fair, that happens. Still: " + hrs + " on your phone. The reason explains part of the day, not all of it." + ask
        ) else listOf(
            "'" + text + "'? That's a mood, not a reason. You gave " + hrs + " to your phone. That was enough to finish '" + g.title + "' and then some." + ask,
            "Interesting. " + hrs + " went to your phone and '" + g.title + "' got nothing. Try again with a real reason."
        )
        return opts[Random.nextInt(opts.size)]
    }

    private fun reply(g: Goal, text: String, cb: (String) -> Unit) {
        val off = offline(g, text)
        if (store.apiKey.isBlank()) { cb(off); return }
        Claude.ask(store, Claude.persona(store, usageText, Usage.fmt(usageMs), g), listOf("user" to text), 300) { cb(it ?: off) }
    }

    private fun onSend() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText(""); bubble(t, true)
        send.isEnabled = false
        val g = cur
        if (g != null) {
            reply(g, t) { r -> addPet(r); send.isEnabled = true; askNext() }
        } else {
            hist.add("user" to t)
            if (store.apiKey.isBlank()) { addPet(offlineFree(t)); hist.removeAt(hist.size - 1); send.isEnabled = true; return }
            Claude.ask(store, Claude.persona(store, usageText, Usage.fmt(usageMs), null), hist.toList(), 400) { r ->
                val txt = r ?: "I couldn't reach my brain just now. Check your key, model and internet in Settings. Meanwhile: what is the smallest next step?"
                hist.add("assistant" to txt); addPet(txt); send.isEnabled = true
            }
        }
    }
}
