package com.sip.dialer

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class CuteKittenRoamView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var catX = 60f
    private var direction = 1 // 1 = right, -1 = left
    private var isSleeping = false
    private var walkCycle = 0f
    private var sleepTimer = 0
    private var walkStepsSinceSleep = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF7ED") }
    private val earInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDA4AF") }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDBA74") }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDE047")
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val animHandler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            updateKittenState()
            invalidate()
            animHandler.postDelayed(this, 45)
        }
    }

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                isSleeping = false
                walkStepsSinceSleep = 0
                direction *= -1
                true
            } else false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animHandler.removeCallbacks(loopRunnable)
        animHandler.post(loopRunnable)
    }

    override fun onDetachedFromWindow() {
        animHandler.removeCallbacks(loopRunnable)
        super.onDetachedFromWindow()
    }

    private fun updateKittenState() {
        val maxW = (width - 85f).coerceAtLeast(100f)
        walkCycle += 0.35f

        if (isSleeping) {
            sleepTimer--
            if (sleepTimer <= 0) {
                isSleeping = false
                walkStepsSinceSleep = 0
                if (Random.nextBoolean()) direction *= -1
            }
        } else {
            catX += direction * 3.4f
            walkStepsSinceSleep++

            if (catX >= maxW) {
                catX = maxW
                direction = -1
            } else if (catX <= 25f) {
                catX = 25f
                direction = 1
            }

            if (walkStepsSinceSleep > 110 && Random.nextInt(100) < 3) {
                isSleeping = true
                sleepTimer = Random.nextInt(70, 120)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val baseY = height - 8f
        canvas.save()
        canvas.translate(catX + 35f, baseY)

        if (isSleeping) {
            // Curled sleeping kitten
            val breath = sin(walkCycle * 0.4f) * 1.8f
            canvas.drawOval(-24f, -20f - breath, 24f, 0f, bodyPaint)
            canvas.drawArc(RectF(-18f, -18f, 18f, 0f), 200f, 120f, false, stripePaint)
            // Head resting
            canvas.drawCircle(16f, -13f, 12f, bodyPaint)
            // Ears
            drawTriangle(canvas, 8f, -21f, 12f, -31f, 17f, -22f, bodyPaint)
            drawTriangle(canvas, 17f, -21f, 22f, -31f, 25f, -20f, bodyPaint)
            // Closed sleepy eye
            canvas.drawArc(RectF(15f, -14f, 23f, -9f), 0f, 180f, false, eyePaint)
            // Floating zZz
            val zOffset = (walkCycle * 3f) % 18f
            zzzPaint.alpha = (255 - (zOffset * 10).toInt()).coerceIn(60, 255)
            canvas.drawText("z", 26f, -22f - zOffset * 0.5f, zzzPaint)
            canvas.drawText("Z", 34f, -30f - zOffset, zzzPaint)
        } else {
            // Flip horizontally if walking left
            if (direction < 0) canvas.scale(-1f, 1f)

            val bob = sin(walkCycle) * 2.2f
            val legSwing = sin(walkCycle) * 6f

            // Wagging Tail
            val tailWave = sin(walkCycle * 1.2f) * 6f
            val tailPath = Path().apply {
                moveTo(-20f, -16f + bob)
                quadTo(-34f, -28f + tailWave, -28f, -36f + tailWave)
            }
            val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FDBA74")
                strokeWidth = 5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(tailPath, tailPaint)

            // Cute Little Paws
            canvas.drawCircle(-12f + legSwing, -4f, 4.5f, bodyPaint)
            canvas.drawCircle(-4f - legSwing, -4f, 4.5f, stripePaint)
            canvas.drawCircle(10f - legSwing, -4f, 4.5f, bodyPaint)
            canvas.drawCircle(16f + legSwing, -4f, 4.5f, stripePaint)

            // Body
            canvas.drawOval(-22f, -23f + bob, 20f, -5f + bob, bodyPaint)
            canvas.drawCircle(-4f, -16f + bob, 5f, stripePaint)

            // Head
            canvas.drawCircle(18f, -22f + bob, 13f, bodyPaint)

            // Ears
            drawTriangle(canvas, 9f, -31f + bob, 12f, -43f + bob, 18f, -33f + bob, bodyPaint)
            drawTriangle(canvas, 18f, -32f + bob, 24f, -43f + bob, 27f, -30f + bob, bodyPaint)
            drawTriangle(canvas, 11f, -32f + bob, 13f, -39f + bob, 16f, -33f + bob, earInnerPaint)
            drawTriangle(canvas, 20f, -32f + bob, 23f, -39f + bob, 25f, -31f + bob, earInnerPaint)

            // Eyes & Pink Nose
            canvas.drawCircle(22f, -23f + bob, 2.4f, pupilPaint)
            canvas.drawCircle(28f, -20f + bob, 2f, earInnerPaint)
        }

        canvas.restore()
    }

    private fun drawTriangle(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, p: Paint) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }
        c.drawPath(path, p)
    }
}
