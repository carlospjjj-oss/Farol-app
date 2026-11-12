package com.farol.app.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.farol.app.core.Fmt
import com.farol.app.core.OfferResult
import com.farol.app.core.Rating
import com.farol.app.data.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cartão flutuante compacto:
 *
 *  ┌──┬──────────────────────────────────┐
 *  │✓ │ R$ 6,56  ★ 4,97            iFood │
 *  │  │ Tempo     Km       Busca         │
 *  │  │ 9 min     2,1 km   0,4 km        │
 *  │  │ R$/km     R$/h     Lucro         │
 *  │  │ 2,48      34,70    5,21 (79%)    │
 *  └──┴──────────────────────────────────┘
 *
 * Faixa lateral e borda na cor do veredito; R$/km e R$/h coloridos individualmente.
 * Toque = fecha. Arraste = muda de lugar (posição fica salva).
 * Desenhado pelo serviço de acessibilidade (TYPE_ACCESSIBILITY_OVERLAY).
 */
class OverlayCard(private val svc: AccessibilityService, private val settings: Settings) {

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private val k = Kit(svc)

    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var stripe: LinearLayout
    private lateinit var stripeIcon: TextView
    private lateinit var body: LinearLayout
    private lateinit var tvPrice: TextView
    private lateinit var tvStar: TextView
    private lateinit var tvApp: TextView
    private lateinit var vTime: TextView
    private lateinit var vKm: TextView
    private lateinit var vPickup: TextView
    private lateinit var vPerKm: TextView
    private lateinit var vPerHour: TextView
    private lateinit var vNet: TextView
    private lateinit var tvWarn: TextView

    val isShowing: Boolean get() = root != null

    fun show(r: OfferResult) {
        ensureView()
        bind(r)
        keepAlive()
    }

    fun keepAlive() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, settings.autoHideSec * 1000L)
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    fun screenRect(): Rect? {
        val v = root ?: return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    // ---------------------------------------------------------------- layout

    private fun cell(label: String): Pair<LinearLayout, TextView> {
        val value = k.text("—", 17, Palette.TEXT, k.bold)
        val box = k.vbox().apply {
            addView(k.text(label, 11, Palette.MUTED, k.medium))
            addView(value, k.lp(w = -2, top = 2))
        }
        return box to value
    }

    private fun gridRow(vararg cells: LinearLayout) = k.hbox().apply {
        gravity = Gravity.TOP
        cells.forEachIndexed { i, c ->
            addView(c, LinearLayout.LayoutParams(k.dp(78), -2).apply { if (i > 0) marginStart = k.dp(6) })
        }
    }

    private fun ensureView() {
        if (root != null) return

        stripeIcon = k.text("✓", 22, Palette.ON_ACCENT, k.black).apply { gravity = Gravity.CENTER }
        stripe = k.vbox().apply {
            gravity = Gravity.CENTER
            addView(stripeIcon)
        }

        tvPrice = k.text("", 26, Palette.TEXT, k.black)
        tvStar = k.text("", 14, Palette.ACCENT, k.bold)
        tvApp = k.text("", 12, Palette.MUTED, k.bold).apply { letterSpacing = 0.05f }
        val top = k.hbox().apply {
            addView(tvPrice)
            addView(tvStar, k.lp(w = -2).apply { marginStart = k.dp(8) })
            addView(View(svc), LinearLayout.LayoutParams(0, 1, 1f))
            addView(tvApp, k.lp(w = -2).apply { marginStart = k.dp(10) })
        }

        val (cTime, t1) = cell("Tempo"); vTime = t1
        val (cKm, t2) = cell("Km"); vKm = t2
        val (cPick, t3) = cell("Busca"); vPickup = t3
        val (cPerKm, t4) = cell("R$/km"); vPerKm = t4
        val (cPerH, t5) = cell("R$/h"); vPerHour = t5
        val (cNet, t6) = cell("Lucro"); vNet = t6

        tvWarn = k.text("", 13, Palette.ACCENT, k.bold)

        body = k.vbox(14, 12).apply {
            addView(top)
            addView(gridRow(cTime, cKm, cPick), k.lp(w = -2, top = 10))
            addView(gridRow(cPerKm, cPerH, cNet), k.lp(w = -2, top = 8))
            addView(tvWarn, k.lp(w = -2, top = 8))
        }

        val card = k.hbox().apply {
            gravity = Gravity.FILL_VERTICAL
            addView(stripe, LinearLayout.LayoutParams(k.dp(34), -1))
            addView(body, LinearLayout.LayoutParams(-2, -2))
            elevation = k.dp(10).toFloat()
            clipToOutline = true
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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

    private fun attachDrag(v: View) {
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
                    if (moved) { settings.cardX = params.x; settings.cardY = params.y; keepAlive() } else hide()
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ bind

    private fun colorOf(r: Rating) = when (r) {
        Rating.GREEN -> Palette.GREEN
        Rating.YELLOW -> Palette.YELLOW
        Rating.RED -> Palette.RED
    }

    private fun num(v: Double) = Fmt.dec(v, 2)

    private fun bind(r: OfferResult) {
        val o = r.offer
        val main = colorOf(r.rating)

        // Faixa e borda na cor do veredito
        stripe.background = k.rounded(main, 0)
        stripeIcon.text = when (r.rating) { Rating.GREEN -> "✓"; Rating.YELLOW -> "!"; Rating.RED -> "✕" }
        stripeIcon.setTextColor(if (r.rating == Rating.YELLOW) Palette.ON_ACCENT else 0xFFFFFFFF.toInt())
        root!!.background = k.rounded(Palette.alpha(0xFF12161C.toInt(), 0xF5), 16, main).apply { setStroke(k.dp(2), main) }

        tvPrice.text = Fmt.money(o.price)
        tvStar.text = o.passengerRating?.let { "★ ${Fmt.dec(it, 2)}" } ?: ""
        tvStar.visibility = if (o.passengerRating != null) View.VISIBLE else View.GONE
        tvApp.text = o.app.uppercase()

        vTime.text = (if (r.timeEstimated) "~" else "") + Fmt.min(r.minutes)
        vKm.text = Fmt.km(o.totalKm)
        vPickup.text = o.pickupKm?.let { Fmt.km(it) } ?: "—"

        vPerKm.text = num(r.perKm)
        vPerKm.setTextColor(colorOf(r.ratingKm))
        vPerHour.text = num(r.perHour)
        vPerHour.setTextColor(colorOf(r.ratingHour))

        val margin = if (o.price > 0) (r.net / o.price * 100).roundToInt() else 0
        vNet.text = "${num(r.net)} ($margin%)"
        vNet.setTextColor(if (r.net >= 0) Palette.GREEN else Palette.RED)
        vNet.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (vNet.text.length > 11) 14f else 17f)

        tvWarn.text = if (r.warnings.isEmpty()) "" else "⚠ " + r.warnings.joinToString(" • ")
        tvWarn.visibility = if (r.warnings.isEmpty()) View.GONE else View.VISIBLE
    }
}
