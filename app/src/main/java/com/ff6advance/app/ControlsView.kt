package com.ff6advance.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class ControlsView(c: Context) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private var mask = 0
    private val lRect = RectF()
    private val rRect = RectF()
    private val selRect = RectF()
    private val startRect = RectF()

    private val normal = Color.parseColor("#44FFFFFF")
    private val pressed = Color.parseColor("#99FFFFFF")

    private fun dpadC() = PointF(width * 0.25f, height * 0.55f)
    private fun dpadR() = minOf(width * 0.2f, height * 0.36f)
    private fun aC() = PointF(width * 0.88f, height * 0.42f)
    private fun bC() = PointF(width * 0.68f, height * 0.62f)
    private fun btnR() = minOf(width * 0.085f, height * 0.17f)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        lRect.set(w * 0.03f, h * 0.02f, w * 0.30f, h * 0.18f)
        rRect.set(w * 0.70f, h * 0.02f, w * 0.97f, h * 0.18f)
        selRect.set(w * 0.30f, h * 0.84f, w * 0.48f, h * 0.95f)
        startRect.set(w * 0.52f, h * 0.84f, w * 0.70f, h * 0.95f)
        label.textSize = minOf(w * 0.04f, h * 0.09f)
    }

    private fun on(bit: Int) = (mask shr bit) and 1 == 1
    private fun col(bit: Int) = if (on(bit)) pressed else normal

    override fun onDraw(canvas: Canvas) {
        val dc = dpadC(); val r = dpadR(); val a = r * 0.38f
        fill.color = normal
        canvas.drawRect(dc.x - a, dc.y - a, dc.x + a, dc.y + a, fill)
        fill.color = col(6); canvas.drawRect(dc.x - r, dc.y - a, dc.x - a, dc.y + a, fill)
        fill.color = col(7); canvas.drawRect(dc.x + a, dc.y - a, dc.x + r, dc.y + a, fill)
        fill.color = col(4); canvas.drawRect(dc.x - a, dc.y - r, dc.x + a, dc.y - a, fill)
        fill.color = col(5); canvas.drawRect(dc.x - a, dc.y + a, dc.x + a, dc.y + r, fill)

        val br = btnR()
        fill.color = col(8); canvas.drawCircle(aC().x, aC().y, br, fill)
        fill.color = col(0); canvas.drawCircle(bC().x, bC().y, br, fill)
        canvas.drawText("A", aC().x, aC().y + label.textSize / 3, label)
        canvas.drawText("B", bC().x, bC().y + label.textSize / 3, label)

        fill.color = col(10); canvas.drawRoundRect(lRect, 24f, 24f, fill)
        fill.color = col(11); canvas.drawRoundRect(rRect, 24f, 24f, fill)
        fill.color = col(2); canvas.drawRoundRect(selRect, 24f, 24f, fill)
        fill.color = col(3); canvas.drawRoundRect(startRect, 24f, 24f, fill)
        canvas.drawText("L", lRect.centerX(), lRect.centerY() + label.textSize / 3, label)
        canvas.drawText("R", rRect.centerX(), rRect.centerY() + label.textSize / 3, label)
        canvas.drawText("SELECT", selRect.centerX(), selRect.centerY() + label.textSize / 3, label)
        canvas.drawText("START", startRect.centerX(), startRect.centerY() + label.textSize / 3, label)
    }

    private fun dist(x: Float, y: Float, p: PointF): Float {
        val dx = x - p.x; val dy = y - p.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun compute(e: MotionEvent): Int {
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) return 0
        val skip = if (act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        var m = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            val x = e.getX(i); val y = e.getY(i)
            val dc = dpadC(); val r = dpadR()
            val dx = x - dc.x; val dy = y - dc.y
            if (dx * dx + dy * dy < r * r * 1.7f) {
                val t = r * 0.3f
                if (dx < -t) m = m or (1 shl 6)
                if (dx > t) m = m or (1 shl 7)
                if (dy < -t) m = m or (1 shl 4)
                if (dy > t) m = m or (1 shl 5)
            }
            val br = btnR() * 1.3f
            if (dist(x, y, aC()) < br) m = m or (1 shl 8)
            if (dist(x, y, bC()) < br) m = m or 1
            if (lRect.contains(x, y)) m = m or (1 shl 10)
            if (rRect.contains(x, y)) m = m or (1 shl 11)
            if (selRect.contains(x, y)) m = m or (1 shl 2)
            if (startRect.contains(x, y)) m = m or (1 shl 3)
        }
        return m
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        mask = compute(e)
        Native.setPad(mask)
        invalidate()
        return true
    }
}
