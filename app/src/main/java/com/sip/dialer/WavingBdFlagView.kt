package com.sip.dialer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class WavingBdFlagView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#006A4E") }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F42A41") }
    private val polePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val flagPath = Path()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val poleX = 8f
        canvas.drawLine(poleX, 4f, poleX, h - 2f, polePaint)

        val flagLeft = poleX + 2f
        val flagRight = w - 4f
        val flagTop = 6f
        val flagBottom = h * 0.78f
        val flagW = flagRight - flagLeft
        val flagH = flagBottom - flagTop
        val amp = 3.2f

        flagPath.reset()
        flagPath.moveTo(flagLeft, flagTop)

        val steps = 20
        for (i in 0..steps) {
            val ratio = i.toFloat() / steps
            val x = flagLeft + ratio * flagW
            val dy = sin(ratio * 4.5f - phase).toFloat() * amp * ratio
            flagPath.lineTo(x, flagTop + dy)
        }
        for (i in steps downTo 0) {
            val ratio = i.toFloat() / steps
            val x = flagLeft + ratio * flagW
            val dy = sin(ratio * 4.5f - phase).toFloat() * amp * ratio
            flagPath.lineTo(x, flagBottom + dy)
        }
        flagPath.close()

        canvas.drawPath(flagPath, greenPaint)

        val sunRatio = 0.45f
        val sunX = flagLeft + flagW * sunRatio
        val sunDy = sin(sunRatio * 4.5f - phase).toFloat() * amp * sunRatio
        val sunY = flagTop + flagH * 0.5f + sunDy
        val sunR = flagH * 0.30f
        canvas.drawCircle(sunX, sunY, sunR, redPaint)
    }
}
