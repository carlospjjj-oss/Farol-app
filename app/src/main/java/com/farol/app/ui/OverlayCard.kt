package com.farol.app.ui

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.farol.app.core.Fmt
import com.farol.app.core.OfferResult
import com.farol.app.core.Rating
import com.farol.app.data.Settings
import kotlin.math.abs

/**
 * Cartão flutuante desenhado pelo próprio serviço de acessibilidade
 * (TYPE_ACCESSIBILITY_OVERLAY: não precisa da permissão "sobrepor a outros apps").
 *
 * Toque = fecha. Arraste = muda de lugar (posição fica salva).
 */
class OverlayCard(private val svc: AccessibilityService, private val settings: Settings) {

    private val wm = svc.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvHeader: TextView
    private lateinit var tvPerKm: TextView
    private lateinit var tvLine2: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvWarn: TextView

    val isShowing: Boolean get() = root != null

    fun show(r: OfferResult) {
        ensureView()
        bind(r)
        keepAlive()
    }

    /** Reinicia o tempo de auto-ocultar enquanto a mesma oferta continua na tela. */
    fun keepAlive() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, settings.autoHideSec * 1000L)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    /** Área do cartão na tela (para o OCR ignorar nosso próprio texto). */
    fun screenRect(): Rect? {
        val v = root ?: return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, svc.resources.displayMetrics).toInt()

    private fun text(sizeSp: Float, bold: Boolean = false) = TextView(svc).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(2f, 0f, 1f, 0x55000000)
    }

    private fun ensureView() {
        if (root != null) return
        tvHeader = text(13f)
        tvPerKm = text(30f, bold = true)
        tvLine2 = text(15f, bold = true)
        tvNet = text(15f)
        tvWarn = text(14f, bold = true)

        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            addView(tvHeader); addView(tvPerKm); addView(tvLine2); addView(tvNet); addView(tvWarn)
            elevation = dp(8f).toFloat()
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.cardX
            y = settings.cardY
        }

        attachDrag(card)
        wm.addView(card, params)
        root = card
    }

    private fun attachDrag(v: LinearLayout) {
        val slop = ViewConfiguration.get(svc).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    handler.removeCallbacks(hideRunnable)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        settings.cardX = params.x; settings.cardY = params.y; keepAlive()
                    } else hide()
                }
            }
            true
        }
    }

    private fun bind(r: OfferResult) {
        val (bg, fg) = when (r.rating) {
            Rating.GREEN -> 0xF01B8E3E.toInt() to Color.WHITE
            Rating.YELLOW -> 0xF0F2B705.toInt() to 0xFF1A1A1A.toInt()
            Rating.RED -> 0xF0C62828.toInt() to Color.WHITE
        }
        root!!.background = GradientDrawable().apply {
            cornerRadius = dp(16f).toFloat()
            setColor(bg)
            setStroke(dp(1f), 0x33000000)
        }
        listOf(tvHeader, tvPerKm, tvLine2, tvNet, tvWarn).forEach { it.setTextColor(fg) }

        val o = r.offer
        val tilde = if (r.timeEstimated) "~" else ""
        tvHeader.text = buildString {
            append("${o.app} • ${Fmt.money(o.price)}")
            o.pickupKm?.let { append(" • busca ${Fmt.km(it)}") }
            o.passengerRating?.let { append(" • ${Fmt.dec(it, 2)}★") }
        }
        tvPerKm.text = "${Fmt.money(r.perKm)}/km"
        tvLine2.text = "${Fmt.money(r.perHour)}/h  •  ${Fmt.km(o.totalKm)}  •  $tilde${Fmt.min(r.minutes)}"
        tvNet.text = "Líquido ${Fmt.money(r.net)}  (${Fmt.money(r.netPerHour)}/h)"
        tvWarn.text = if (r.warnings.isEmpty()) "" else "⚠ " + r.warnings.joinToString(" • ")
        tvWarn.visibility = if (r.warnings.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
