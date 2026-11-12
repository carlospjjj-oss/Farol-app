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
 * Cartão flutuante.
 *
 * Normal:                                      Compacto:
 *  ┌──┬──────────────────────────────────┐      ┌──┬──────────────┐
 *  │✓ │ R$ 6,56  ★ 4,97            IFOOD │      │✓ │ 2,48 /km     │
 *  │  │ Tempo     Km       Busca         │      │  │ R$ 6,56 IFOOD│
 *  │  │ R$/km     R$/h     Lucro         │      └──┴──────────────┘
 *  │  │ ⚠ avisos · ⭐ favoritos · meta    │
 *  └──┴──────────────────────────────────┘
 *
 * Tamanho P/M/G e modo compacto nos Ajustes. Toque = fecha; arraste = move (posição salva).
 */
class OverlayCard(private val svc: AccessibilityService, private val settings: Settings) {

    /** Linha extra embaixo do cartão: texto e cor. */
    data class Extra(val text: String, val color: Int)

    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }
    private val k = Kit(svc)

    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var builtScale = 0f
    private var builtCompact = false

    val isShowing: Boolean get() = root != null

    fun show(r: OfferResult, extras: List<Extra> = emptyList()) {
        val scale = settings.cardScale
        val compact = settings.cardCompact
        // Se o tamanho/modo mudou nos Ajustes, recria o cartão
        if (root != null && (scale != builtScale || compact != builtCompact)) hide()
        val card = root ?: create().also { root = it; builtScale = scale; builtCompact = compact }
        bind(card, r, extras, scale, compact)
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

    // ---------------------------------------------------------------- janela

    private fun create(): LinearLayout {
        val card = k.hbox().apply {
            gravity = Gravity.FILL_VERTICAL
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
        return card
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

    // ---------------------------------------------------------------- conteúdo

    private fun colorOf(r: Rating) = when (r) {
        Rating.GREEN -> Palette.GREEN
        Rating.YELLOW -> Palette.YELLOW
        Rating.RED -> Palette.RED
    }

    private fun num(v: Double) = Fmt.dec(v, 2)

    private fun bind(card: LinearLayout, r: OfferResult, extras: List<Extra>, sc: Float, compact: Boolean) {
        card.removeAllViews()
        val o = r.offer
        val main = colorOf(r.rating)
        fun t(text: String, sp: Float, color: Int, face: android.graphics.Typeface? = k.bold) = k.text(text, sp * sc, color, face)
        fun d(v: Float) = k.dp(v * sc)

        card.background = k.rounded(Palette.alpha(0xFF12161C.toInt(), 0xF5), 16 * sc, main).apply { setStroke(k.dp(2), main) }

        // faixa lateral
        val icon = when (r.rating) { Rating.GREEN -> "✓"; Rating.YELLOW -> "!"; Rating.RED -> "✕" }
        val stripe = k.vbox().apply {
            gravity = Gravity.CENTER
            background = k.rounded(main, 0)
            addView(t(icon, 22f, if (r.rating == Rating.YELLOW) Palette.ON_ACCENT else 0xFFFFFFFF.toInt(), k.black)
                .apply { gravity = Gravity.CENTER })
        }
        card.addView(stripe, LinearLayout.LayoutParams(d(34f), -1))

        val body = k.vbox().apply { setPadding(d(14f), d(12f), d(14f), d(12f)) }
        card.addView(body, LinearLayout.LayoutParams(-2, -2))

        if (compact) {
            body.addView(k.hbox().apply {
                addView(t(num(r.perKm), 34f, colorOf(r.ratingKm), k.black))
                addView(t(" /km", 14f, Palette.MUTED, k.medium))
            })
            body.addView(k.hbox().apply {
                addView(t(Fmt.money(o.price), 15f, Palette.TEXT))
                addView(t("  ${o.app.uppercase()}", 12f, Palette.MUTED))
            }, k.lp(w = -2, top = 2))
        } else {
            body.addView(k.hbox().apply {
                addView(t(Fmt.money(o.price), 26f, Palette.TEXT, k.black))
                o.passengerRating?.let { addView(t("★ ${Fmt.dec(it, 2)}", 14f, Palette.ACCENT), k.lp(w = -2).apply { marginStart = d(8f) }) }
                addView(View(svc), LinearLayout.LayoutParams(0, 1, 1f))
                addView(t(o.app.uppercase(), 12f, Palette.MUTED).apply { letterSpacing = 0.05f }, k.lp(w = -2).apply { marginStart = d(10f) })
            })
            fun cell(label: String, value: String, color: Int = Palette.TEXT) = k.vbox().apply {
                addView(t(label, 11f, Palette.MUTED, k.medium))
                addView(t(value, 17f, color).apply {
                    if (value.length > 11) setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f * sc)
                }, k.lp(w = -2, top = 2))
            }
            fun row(vararg cells: LinearLayout) = k.hbox().apply {
                gravity = Gravity.TOP
                cells.forEachIndexed { i, c -> addView(c, LinearLayout.LayoutParams(d(78f), -2).apply { if (i > 0) marginStart = d(6f) }) }
            }
            val margin = if (o.price > 0) (r.net / o.price * 100).roundToInt() else 0
            body.addView(row(
                cell("Tempo", (if (r.timeEstimated) "~" else "") + Fmt.min(r.minutes)),
                cell("Km", Fmt.km(o.totalKm)),
                cell("Busca", o.pickupKm?.let { Fmt.km(it) } ?: "—"),
            ), k.lp(w = -2, top = d(10f)))
            body.addView(row(
                cell("R$/km", num(r.perKm), colorOf(r.ratingKm)),
                cell("R$/h", num(r.perHour), colorOf(r.ratingHour)),
                cell("Lucro", "${num(r.net)} ($margin%)", if (r.net >= 0) Palette.GREEN else Palette.RED),
            ), k.lp(w = -2, top = d(8f)))
        }

        // linhas extras: avisos, favoritos, recusas, meta
        if (r.warnings.isNotEmpty()) body.addView(t("⚠ " + r.warnings.joinToString(" • "), 13f, Palette.ACCENT), k.lp(w = -2, top = d(8f)))
        if (r.favorites.isNotEmpty()) body.addView(t("⭐ " + r.favorites.joinToString(" • "), 13f, Palette.GREEN), k.lp(w = -2, top = d(6f)))
        extras.forEach { body.addView(t(it.text, 12f, it.color, k.medium), k.lp(w = -2, top = d(6f))) }
    }

    @Suppress("unused") private val keepTv: Class<TextView> = TextView::class.java
}
