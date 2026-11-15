package com.app.dialclock.vulkan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Glyph atlas for the Slats Vulkan shader: black text on transparent, so
 * the sampled alpha IS the ink coverage.
 *
 * Layout: 8 columns x 5 rows of 128px cells (1024 x 640).
 *   cells 0..11   hour numbers: "12", "1" .. "11"
 *   cells 12..23  minute numbers: "05", "10" .. "60"
 *   cells 24..33  digits "0" .. "9"
 * Must match the cell math in slats.frag.
 */
object SlatsAtlas {
    const val COLS = 8
    const val ROWS = 5
    const val CELL = 128

    private var cached: Bitmap? = null

    fun hourLabel(i: Int): String = if (i == 0) "12" else i.toString()

    fun minuteLabel(i: Int): String {
        val v = ((i + 1) * 5) % 60
        return "%02d".format(if (v == 0) 60 else v)
    }

    /** One shared bitmap for the whole process; built once, never mutated. */
    fun bitmap(): Bitmap {
        cached?.let { return it }
        val bmp = Bitmap.createBitmap(COLS * CELL, ROWS * CELL, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = CELL * 0.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        fun drawCell(cell: Int, label: String) {
            val cx = (cell % COLS) * CELL + CELL / 2f
            val cy = (cell / COLS) * CELL + CELL / 2f
            c.drawText(label, cx, cy + paint.textSize * 0.35f, paint)
        }
        for (i in 0 until 12) drawCell(i, hourLabel(i))
        for (i in 0 until 12) drawCell(12 + i, minuteLabel(i))
        for (d in 0 until 10) drawCell(24 + d, d.toString())
        cached = bmp
        return bmp
    }
}
