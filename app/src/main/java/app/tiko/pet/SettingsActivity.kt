package app.tiko.pet

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var box: LinearLayout
    private val edits = HashMap<String, EditText>()
    private lateinit var hourBox: EditText
    private lateinit var apiBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var customBox: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(28), dp(18), dp(28)) }
        setContentView(ScrollView(this).apply { addView(box); setBackgroundColor(0xFFF4E4D9.toInt()) })
    }

    override fun onResume() { super.onResume(); render() }
    override fun onPause() { super.onPause(); if (::hourBox.isInitialized) save() }

    private fun tv(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(0xFF3A2A20.toInt())
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun btn(s: String, f: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { f() } }

    private fun edit(hint: String, value: String, multi: Boolean = false, secret: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(value)
        if (multi) { minLines = 4; gravity = android.view.Gravity.TOP }
        if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun render() {
        box.removeAllViews(); edits.clear()
        box.addView(tv("Settings", 26f, true))

        box.addView(tv("Permissions", 18f, true))
        box.addView(if (!Perm.hasOverlay(this)) btn("Allow: display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        } else tv("Display over other apps: allowed"))
        box.addView(if (!Perm.hasUsage(this)) btn("Allow: usage access (sees which app is open, and screen time)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else tv("Usage access: allowed"))
        box.addView(if (!Perm.hasNotif(this)) btn("Allow: notifications") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else tv("Notifications: allowed"))

        box.addView(tv("Pawfessor", 18f, true))
        box.addView(Switch(this).apply {
            text = "Pawfessor is on duty"; isChecked = store.running
            setOnCheckedChangeListener { _, on ->
                if (on && !(Perm.hasOverlay(this@SettingsActivity) && Perm.hasUsage(this@SettingsActivity)))
                    Toast.makeText(this@SettingsActivity, "Allow the first two permissions above too.", Toast.LENGTH_LONG).show()
                store.running = on
                val i = Intent(this@SettingsActivity, PetService::class.java)
                if (on) startForegroundService(i) else stopService(i)
            }
        })
        box.addView(btn("Test Pawfessor now") { startForegroundService(Intent(this, PetService::class.java).setAction("TEST")) })
        box.addView(btn("Choose apps Pawfessor watches") { pickApps() })

        box.addView(tv("Night check-in", 18f, true))
        box.addView(tv("Hour it opens after you unlock (0 = 12 am, 23 = 11 pm)", 13f))
        hourBox = edit("0", store.checkinHour.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        box.addView(hourBox)
        box.addView(btn("Run tonight's check-in now (test)") {
            save(); Midnight.markDue(this)
            startActivity(Intent(this, ChatActivity::class.java).putExtra("checkin", true))
        })

        box.addView(tv("Claude (for chat, brutal check-ins and generated messages)", 18f, true))
        box.addView(tv("Your API key stays on this phone and is sent only to api.anthropic.com.", 13f))
        apiBox = edit("API key", store.apiKey, secret = true); box.addView(apiBox)
        modelBox = edit("Model", store.model); box.addView(modelBox)

        box.addView(tv("Your details", 18f, true))
        box.addView(tv("These fill the blanks in Pawfessor's messages. Leave a field empty and the messages that need it are skipped. Nothing leaves this phone.", 13f))
        for ((k, label, ex) in Fields.all) {
            box.addView(tv(label, 13f))
            val e = edit("e.g. " + ex, store.prof(k)); edits[k] = e; box.addView(e)
        }

        box.addView(tv("Your own messages", 18f, true))
        box.addView(tv("One per line: category|mood|text. Categories: insta pin game exam baby phd any goal night win. Moods: angry judge sad happy xhappy.", 13f))
        customBox = edit("insta|angry|Put the phone down. Chapter 3 misses you.", store.customRaw, multi = true)
        box.addView(customBox)
        box.addView(btn("Generate more messages with Claude") { generate() })
        box.addView(btn("Save") { save(); Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show() })
    }

    private fun save() {
        for ((k, e) in edits) store.setProf(k, e.text.toString())
        store.checkinHour = hourBox.text.toString().toIntOrNull() ?: 0
        store.apiKey = apiBox.text.toString()
        if (modelBox.text.isNotBlank()) store.model = modelBox.text.toString()
        store.customRaw = customBox.text.toString()
        Midnight.schedule(this)
    }

    private fun generate() {
        save()
        if (store.apiKey.isBlank()) { Toast.makeText(this, "Add your Claude API key first.", Toast.LENGTH_LONG).show(); return }
        Bank.load(this)
        val sample = Bank.all(store).shuffled().take(12).joinToString("\n") { it.cat + "|" + it.mood + "|" + it.text }
        val sys = "You write short personalised messages for Pawfessor, a tiger cub pet that nudges the user to study and work. " +
            "Roast sometimes, beg sometimes, motivate sometimes, never give orders. Output ONLY lines in the format category|mood|text. " +
            "Categories: insta, pin, game, exam, baby, phd, any. Moods: angry, judge, sad, happy. Each text under 20 words. " +
            "You may use only these placeholders: {minutes} {count} {days_left} {goal} {reason}. Use the user's details directly in the text."
        val user = "User details: " + store.profileText() + "\nExamples of the style:\n" + sample + "\nWrite 20 new lines."
        Toast.makeText(this, "Generating...", Toast.LENGTH_SHORT).show()
        Claude.ask(store, sys, listOf("user" to user), 1500) { r ->
            if (r == null) Toast.makeText(this, "Could not reach Claude. Check key, model and internet.", Toast.LENGTH_LONG).show()
            else {
                val cats = setOf("insta", "pin", "game", "exam", "baby", "phd", "any")
                val ok = r.lines().filter { Bank.parse(it) != null && it.split("|")[0].trim() in cats }
                store.customRaw = (store.customRaw + "\n" + ok.joinToString("\n")).trim()
                customBox.setText(store.customRaw)
                Toast.makeText(this, "Added " + ok.size + " messages.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickApps() { Apps.pick(this, store) { } }
}
