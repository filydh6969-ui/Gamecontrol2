package com.gamecontrol

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.roundToInt

class GamepadView(context: Context, private val pad: HidGamepad) : View(context) {

    private class Btn(val key: Int, val label: String, val rect: RectF, val round: Boolean)

    companion object {
        const val A = 0; const val B = 1; const val X = 2; const val Y = 3
        const val LB = 4; const val RB = 5; const val BACK = 6; const val START = 7
        const val LT = 100; const val RT = 101
        const val UP = 110; const val DOWN = 111; const val LEFT = 112; const val RIGHT = 113
    }

    private val btns = ArrayList<Btn>()
    private val pressed = HashSet<Int>()
    private val lc = PointF()
    private val rc = PointF()
    private var stickR = 0f
    private var leftPtr = -1
    private var rightPtr = -1
    private var lDx = 0f; private var lDy = 0f
    private var rDx = 0f; private var rDy = 0f
    private var lastSent = ""

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(90, 92, 105)
        strokeWidth = 6f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        btns.clear()
        val u = h.toFloat()
        stickR = 0.17f * u
        lc.set(0.16f * w, 0.58f * u)
        rc.set(0.64f * w, 0.76f * u)
        text.textSize = 0.055f * u

        fun circ(key: Int, label: String, cx: Float, cy: Float, r: Float) {
            btns.add(Btn(key, label, RectF(cx - r, cy - r, cx + r, cy + r), true))
        }
        fun box(key: Int, label: String, l: Float, t: Float, r: Float, b: Float) {
            btns.add(Btn(key, label, RectF(l, t, r, b), false))
        }

        // A B X Y
        val cx = 0.84f * w; val cy = 0.52f * u; val d = 0.13f * u; val r = 0.07f * u
        circ(Y, "Y", cx, cy - d, r)
        circ(A, "A", cx, cy + d, r)
        circ(X, "X", cx - d, cy, r)
        circ(B, "B", cx + d, cy, r)

        // Cruceta
        val dx = 0.36f * w; val dy = 0.76f * u; val hf = 0.055f * u
        box(UP, "▲", dx - hf, dy - 3 * hf, dx + hf, dy - hf)
        box(DOWN, "▼", dx - hf, dy + hf, dx + hf, dy + 3 * hf)
        box(LEFT, "◀", dx - 3 * hf, dy - hf, dx - hf, dy + hf)
        box(RIGHT, "▶", dx + hf, dy - hf, dx + 3 * hf, dy + hf)

        // Gatillos y bumpers
        box(LT, "LT", 0.04f * w, 0.03f * u, 0.20f * w, 0.15f * u)
        box(LB, "LB", 0.04f * w, 0.17f * u, 0.20f * w, 0.29f * u)
        box(RT, "RT", 0.80f * w, 0.03f * u, 0.96f * w, 0.15f * u)
        box(RB, "RB", 0.80f * w, 0.17f * u, 0.96f * w, 0.29f * u)

        // Back / Start
        circ(BACK, "Bk", 0.43f * w, 0.45f * u, 0.055f * u)
        circ(START, "St", 0.57f * w, 0.45f * u, 0.055f * u)
    }

    private fun colorFor(key: Int): Int = when (key) {
        A -> Color.rgb(60, 150, 70)
        B -> Color.rgb(190, 55, 55)
        X -> Color.rgb(55, 95, 190)
        Y -> Color.rgb(200, 160, 35)
        else -> Color.rgb(52, 54, 64)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(18, 18, 22))
        for (b in btns) {
            fill.color = if (b.key in pressed) Color.rgb(100, 170, 255) else colorFor(b.key)
            if (b.round) c.drawOval(b.rect, fill) else c.drawRoundRect(b.rect, 18f, 18f, fill)
            c.drawText(b.label, b.rect.centerX(), b.rect.centerY() + text.textSize / 3f, text)
        }
        drawStick(c, lc, lDx, lDy)
        drawStick(c, rc, rDx, rDy)
    }

    private fun drawStick(c: Canvas, ctr: PointF, dx: Float, dy: Float) {
        c.drawCircle(ctr.x, ctr.y, stickR, ring)
        fill.color = Color.rgb(75, 78, 92)
        c.drawCircle(ctr.x + dx * stickR, ctr.y + dy * stickR, stickR * 0.45f, fill)
    }

    private fun hit(b: Btn, x: Float, y: Float): Boolean {
        if (!b.round) return b.rect.contains(x, y)
        val r = b.rect.width() / 2f * 1.15f
        return hypot(x - b.rect.centerX(), y - b.rect.centerY()) <= r
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val x = e.getX(i); val y = e.getY(i)
                if (leftPtr == -1 && hypot(x - lc.x, y - lc.y) < stickR * 1.4f) leftPtr = id
                else if (rightPtr == -1 && hypot(x - rc.x, y - rc.y) < stickR * 1.4f) rightPtr = id
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == leftPtr) { leftPtr = -1; lDx = 0f; lDy = 0f }
                if (id == rightPtr) { rightPtr = -1; rDx = 0f; rDy = 0f }
            }
            MotionEvent.ACTION_CANCEL -> {
                leftPtr = -1; rightPtr = -1
                lDx = 0f; lDy = 0f; rDx = 0f; rDy = 0f
                pressed.clear()
                push()
                return true
            }
        }
        val skip = if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1

        pressed.clear()
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            val id = e.getPointerId(i)
            val x = e.getX(i); val y = e.getY(i)
            if (id == leftPtr) {
                val v = norm(x, y, lc); lDx = v.x; lDy = v.y
            } else if (id == rightPtr) {
                val v = norm(x, y, rc); rDx = v.x; rDy = v.y
            } else {
                for (b in btns) if (hit(b, x, y)) pressed.add(b.key)
            }
        }
        push()
        return true
    }

    private fun norm(x: Float, y: Float, c: PointF): PointF {
        var dx = (x - c.x) / stickR
        var dy = (y - c.y) / stickR
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        return PointF(dx, dy)
    }

    private fun ax(v: Float): Int = (128 + v * 127).roundToInt().coerceIn(0, 255)

    private fun push() {
        var bits = 0
        for (k in pressed) if (k in 0..15) bits = bits or (1 shl k)
        val u = UP in pressed; val d = DOWN in pressed
        val l = LEFT in pressed; val r = RIGHT in pressed
        val hat = when {
            u && r -> 2
            d && r -> 4
            d && l -> 6
            u && l -> 8
            u -> 1
            r -> 3
            d -> 5
            l -> 7
            else -> 0
        }
        val lt = if (LT in pressed) 255 else 0
        val rt = if (RT in pressed) 255 else 0
        val key = "${ax(lDx)},${ax(lDy)},${ax(rDx)},${ax(rDy)},$lt,$rt,$hat,$bits"
        if (key != lastSent) {
            lastSent = key
            pad.send(ax(lDx), ax(lDy), ax(rDx), ax(rDy), lt, rt, hat, bits)
        }
        invalidate()
    }
}
