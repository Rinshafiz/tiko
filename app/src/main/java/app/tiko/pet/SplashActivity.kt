package app.tiko.pet

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SplashActivity : Activity() {
    private val SPLASH_MS = 2500L   // how long the icon and name stay on screen

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFFF4E4D9.toInt())
        }
        col.addView(ImageView(this).apply { setImageResource(R.drawable.tiko_logo) },
            LinearLayout.LayoutParams((160 * d).toInt(), (160 * d).toInt()))
        col.addView(TextView(this).apply {
            text = "Tiko"; textSize = 36f; gravity = Gravity.CENTER; setTextColor(0xFF3A2A20.toInt())
            setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = "Pawfessor is waiting"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(0xFF6B5444.toInt())
        })
        setContentView(col)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java)); finish()
        }, SPLASH_MS)
    }
}
