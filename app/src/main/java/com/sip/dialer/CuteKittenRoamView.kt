package com.sip.dialer

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class CuteKittenRoamView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // State: 0 = Walking/Exploring, 1 = Playing with Yarn Ball, 2 = Eating from Bowl, 3 = Sleeping near House
    private var state = 0
    private var stateTimer = 90
    private var catX = 45f
    private var direction = 1
    private var cycle = 0f
    private var ballOffset = 0f

    private val houseWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#92400E") }
    private val houseRoofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
    private val houseDoorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E1B4B") }
    private val bowlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF4444") }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#38BDF8") }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF7ED") }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FB923C") }
    private val earInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FDA4AF") }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
    private val eyeClosedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
    }
    private val zzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDE047")
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val animHandler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            updateSimulation()
            invalidate()
            animHandler.postDelayed(this, 45)
        }
    }

    init {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                state = (state + 1) % 4
                stateTimer = 80
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

    private fun updateSimulation() {
        val w = width.toFloat() / 1.45f
        if (w <= 40f) return
        cycle += 0.35f

        val houseX = 18f
        val bowlX = w * 0.44f
        val ballBaseX = w - 26f

        stateTimer--
        if (stateTimer <= 0) {
            state = Random.nextInt(4)
            stateTimer = when (state) {
                0 -> Random.nextInt(75, 120)
                1 -> Random.nextInt(70, 110)
                2 -> Random.nextInt(55, 85)
                else -> Random.nextInt(80, 130)
            }
        }

        when (state) {
            0 -> { // Roaming between house and ball
                catX += direction * 2.1f
                if (catX >= ballBaseX - 18f) { catX = ballBaseX - 18f; direction = -1 }
                if (catX <= houseX + 22f) { catX = houseX + 22f; direction = 1 }
                ballOffset *= 0.85f
            }
            1 -> { // Move to Yarn Ball & Play!
                val target = ballBaseX - 16f
                if (abs(catX - target) > 3f) {
                    direction = if (target > catX) 1 else -1
                    catX += direction * 2.5f
                } else {
                    direction = 1
                    ballOffset = sin(cycle * 1.4f) * 7f
                }
            }
            2 -> { // Move to Food Bowl & Eat!
                val target = bowlX - 12f
                if (abs(catX - target) > 3f) {
                    direction = if (target > catX) 1 else -1
                    catX += direction * 2.5f
                } else {
                    direction = 1
                }
            }
            3 -> { // Return near Cat House & Sleep!
                val target = houseX + 22f
                if (abs(catX - target) > 3f) {
                    direction = if (target > catX) 1 else -1
                    catX += direction * 2.2f
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scaleFactor = 1.45f
        val w = width.toFloat() / scaleFactor
        val h = height.toFloat() / scaleFactor
        if (w <= 30f || h <= 8f) return
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        val groundY = h - 3f
        val houseX = 18f
        val bowlX = w * 0.44f
        val ballX = (w - 22f) + ballOffset

        // 1. Draw Cat House (Left)
        canvas.drawRect(houseX - 13f, groundY - 20f, houseX + 13f, groundY, houseWallPaint)
        val roof = Path().apply {
            moveTo(houseX - 16f, groundY - 20f)
            lineTo(houseX, groundY - 32f)
            lineTo(houseX + 16f, groundY - 20f)
            close()
        }
        canvas.drawPath(roof, houseRoofPaint)
        canvas.drawArc(RectF(houseX - 6f, groundY - 14f, houseX + 6f, groundY + 4f), 180f, 180f, true, houseDoorPaint)

        // 2. Draw Food Bowl (Middle)
        canvas.drawArc(RectF(bowlX - 8f, groundY - 10f, bowlX + 8f, groundY - 3f), 180f, 180f, true, foodPaint)
        val bowlPath = Path().apply {
            moveTo(bowlX - 10f, groundY - 6f)
            lineTo(bowlX + 10f, groundY - 6f)
            lineTo(bowlX + 7f, groundY)
            lineTo(bowlX - 7f, groundY)
            close()
        }
        canvas.drawPath(bowlPath, bowlPaint)

        // 3. Draw Toy Yarn Ball (Right)
        val ballBounce = if (state == 1) abs(sin(cycle * 1.4f)) * 5f else 0f
        canvas.drawCircle(ballX, groundY - 5.5f - ballBounce, 5.5f, ballPaint)

        // 4. Draw Cute Kitten
        canvas.save()
        canvas.translate(catX, groundY)

        if (state == 3 && abs(catX - (houseX + 22f)) <= 4f) {
            // Sleeping curled up beside house
            val breath = sin(cycle * 0.4f) * 1.2f
            canvas.drawOval(-13f, -12f - breath, 13f, 0f, bodyPaint)
            canvas.drawCircle(9f, -8f, 7.5f, bodyPaint)
            drawTri(canvas, 4f, -13f, 6f, -19f, 9f, -14f, bodyPaint)
            drawTri(canvas, 10f, -13f, 13f, -19f, 15f, -13f, bodyPaint)
            canvas.drawArc(RectF(8f, -9f, 13f, -6f), 0f, 180f, false, eyeClosedPaint)
            val zOff = (cycle * 2f) % 12f
            canvas.drawText("z", 14f, -14f - zOff * 0.5f, zzzPaint)
            canvas.drawText("Z", 19f, -20f - zOff, zzzPaint)
        } else {
            if (direction < 0) canvas.scale(-1f, 1f)
            val isEating = (state == 2 && abs(catX - (bowlX - 12f)) <= 4f)
            val isPlaying = (state == 1 && abs(catX - (w - 42f)) <= 6f)
            val headTilt = if (isEating) abs(sin(cycle * 1.5f)) * 5f else 0f
            val bob = if (!isEating && !isPlaying) sin(cycle) * 1.5f else 0f
            val leg = if (!isEating && !isPlaying) sin(cycle) * 4f else 0f

            // Wagging tail
            val tailPath = Path().apply {
                moveTo(-12f, -10f + bob)
                quadTo(-20f, -18f + sin(cycle) * 4f, -16f, -24f)
            }
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FB923C")
                strokeWidth = 3.2f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(tailPath, tp)

            // Paws (one raised if batting toy ball!)
            canvas.drawCircle(-7f + leg, -2.8f, 3f, bodyPaint)
            canvas.drawCircle(-2f - leg, -2.8f, 3f, stripePaint)
            canvas.drawCircle(6f - leg, -2.8f, 3f, bodyPaint)
            if (isPlaying) {
                val pawY = -8f - abs(sin(cycle * 1.6f)) * 6f
                canvas.drawCircle(13f, pawY, 3.2f, bodyPaint)
            } else {
                canvas.drawCircle(10f + leg, -2.8f, 3f, stripePaint)
            }

            // Body & Spots
            canvas.drawOval(-14f, -15f + bob, 12f, -3f + bob, bodyPaint)
            canvas.drawCircle(-2f, -10f + bob, 3.5f, stripePaint)

            // Head & Ears
            val hy = -14f + bob + headTilt
            canvas.drawCircle(11f, hy, 8.2f, bodyPaint)
            drawTri(canvas, 5f, hy - 6f, 7f, hy - 14f, 11f, hy - 7f, bodyPaint)
            drawTri(canvas, 11f, hy - 6f, 15f, hy - 14f, 17f, hy - 5f, bodyPaint)
            drawTri(canvas, 6.5f, hy - 6.5f, 7.5f, hy - 11.5f, 9.5f, hy - 7f, earInnerPaint)
            drawTri(canvas, 12.5f, hy - 6.5f, 14.5f, hy - 11.5f, 15.5f, hy - 6f, earInnerPaint)

            // Eye & Pink Nose
            canvas.drawCircle(13.5f, hy - 1f, 1.6f, pupilPaint)
            canvas.drawCircle(17.5f, hy + 1f, 1.4f, earInnerPaint)
        }
        canvas.restore()
        canvas.restore()
    }

    private fun drawTri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, p: Paint) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }
        c.drawPath(path, p)
    }
}
