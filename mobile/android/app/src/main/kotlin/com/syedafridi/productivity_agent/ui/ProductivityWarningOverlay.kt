package com.syedafridi.productivity_agent.ui

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class ProductivityWarningOverlay(
    context: Context,
    private val onTimeExpired: () -> Unit,
    private val onDismiss: () -> Unit
) : LinearLayout(context) {

    private val countdownText: TextView
    private var timer: CountDownTimer? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.parseColor("#CC000000"))
        setPadding(48, 48, 48, 48)

        val warningIcon = TextView(context).apply {
            text = "\u26A0\uFE0F"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
        }
        addView(warningIcon)

        val titleText = TextView(context).apply {
            text = "Are you being productive?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        addView(titleText)

        val subtitleText = TextView(context).apply {
            text = "Navigate to productive content or this app will close."
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        addView(subtitleText)

        countdownText = TextView(context).apply {
            text = "Closing in 30s..."
            setTextColor(Color.parseColor("#FF6B6B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }
        addView(countdownText)

        startCountdown()
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(30_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                countdownText.text = "Closing in ${seconds}s..."
            }
            override fun onFinish() {
                onTimeExpired()
            }
        }.start()
    }

    fun dismiss() {
        timer?.cancel()
        timer = null
        onDismiss()
    }
}
