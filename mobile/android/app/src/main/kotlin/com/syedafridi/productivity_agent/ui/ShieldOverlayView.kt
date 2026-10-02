package com.syedafridi.productivity_agent.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ShieldOverlayView(
    context: Context,
    val targetPackage: String,
    val onReturnToFocus: () -> Unit,
    val onEmergencyPass: () -> Unit
) : FrameLayout(context) {

    private var secondsLeft = 5
    private val handler = Handler(Looper.getMainLooper())

    val statusBadge: TextView
    val titleView: TextView
    val noticeView: TextView
    val countdownText: TextView
    val primaryButton: Button
    val secondaryButton: Button

    private val countdownRunnable: Runnable = object : Runnable {
        override fun run() {
            secondsLeft--
            if (secondsLeft > 0) {
                countdownText.text = "Mindful pause: ${secondsLeft}s"
                handler.postDelayed(this, 1000L)
            } else {
                countdownText.text = "Ready to proceed"
                primaryButton.isEnabled = true
                primaryButton.alpha = 1.0f
            }
        }
    }

    init {
        setBackgroundColor(Color.parseColor("#07080D"))

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val paddingPx = dpToPx(32)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // 1. Status Badge
        statusBadge = TextView(context).apply {
            text = "// SHIELD ACTIVE // FOCUS RESTRICTED"
            setTextColor(Color.parseColor("#FF3366"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        contentLayout.addView(statusBadge)

        // 2. Title
        titleView = TextView(context).apply {
            text = "Distraction Intercepted"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        contentLayout.addView(titleView)

        // 3. Notice
        noticeView = TextView(context).apply {
            text = "$targetPackage is restricted during your Deep Focus session."
            setTextColor(Color.parseColor("#A0A5B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        contentLayout.addView(noticeView)

        // 4. Countdown Text
        countdownText = TextView(context).apply {
            text = "Mindful pause: 5s"
            setTextColor(Color.parseColor("#00D2FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        contentLayout.addView(countdownText)

        // 5. Primary Button ("RETURN TO FOCUS")
        primaryButton = Button(context).apply {
            text = "RETURN TO FOCUS"
            setBackgroundColor(Color.parseColor("#00F5A0"))
            setTextColor(Color.parseColor("#07080D"))
            typeface = Typeface.DEFAULT_BOLD
            isEnabled = false
            alpha = 0.4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(24)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener {
                onReturnToFocus()
            }
        }
        contentLayout.addView(primaryButton)

        // 6. Secondary Button ("EMERGENCY 60s PASS")
        secondaryButton = Button(context).apply {
            text = "EMERGENCY 60s PASS"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#666B80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener {
                onEmergencyPass()
            }
        }
        contentLayout.addView(secondaryButton)

        addView(contentLayout)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(countdownRunnable)
        if (secondsLeft > 0) {
            handler.postDelayed(countdownRunnable, 1000L)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismiss()
    }

    fun dismiss() {
        handler.removeCallbacks(countdownRunnable)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
