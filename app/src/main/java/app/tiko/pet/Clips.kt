package app.tiko.pet

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView

object Clips {
    // plays assets/clips/NAME.webp in a loop; shows the still image if the clip is missing
    fun set(ctx: Context, iv: ImageView, name: String, fallback: Int) {
        val d: Drawable? = try {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.assets, "clips/" + name + ".webp"))
        } catch (e: Exception) { null }
        if (d == null) { iv.setImageResource(fallback); return }
        iv.setImageDrawable(d)
        if (d is AnimatedImageDrawable) { d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; d.start() }
    }
}

object Meta {
    var v = 330f   // walking speed in clip pixels per second
    var s = 320f   // clip canvas size
    fun load(ctx: Context) {
        try {
            val j = org.json.JSONObject(ctx.assets.open("clips/meta.json").bufferedReader().readText())
            v = j.optDouble("v", 330.0).toFloat(); s = j.optDouble("S", 320.0).toFloat()
        } catch (e: Exception) { }
    }
}
