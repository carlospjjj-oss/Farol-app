package com.farol.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Paleta 6.3: grafite + verde-limão (destaque). As cores do semáforo continuam as mesmas. */
object Palette {
    const val BG = 0xFF0E0F11.toInt()
    const val SURFACE = 0xFF17191C.toInt()
    const val SURFACE2 = 0xFF202327.toInt()
    const val STROKE = 0xFF2A2E33.toInt()
    const val TEXT = 0xFFF4F6F3.toInt()
    const val MUTED = 0xFF8D949A.toInt()
    const val ACCENT = 0xFFC8F23C.toInt()   // verde-limão
    const val ACCENT2 = 0xFF8FD42A.toInt()  // limão escuro (degradês)
    const val ON_ACCENT = 0xFF0F1408.toInt()
    const val GREEN = 0xFF22C55E.toInt()
    const val YELLOW = 0xFFF5B014.toInt()
    const val RED = 0xFFEF4444.toInt()

    fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    /** Cor de cada app na lista de ganhos. */
    fun app(name: String) = com.farol.app.core.AppCatalog.color(name).toInt()
}

/** Pequeno kit de componentes desenhados em código (sem bibliotecas extras). */
class Kit(private val ctx: Context) {

    fun dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    fun rounded(color: Int, radiusDp: Number = 20, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        stroke?.let { setStroke(dp(1), it) }
    }

    fun gradient(vararg colors: Int, radiusDp: Number = 24) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, colors
    ).apply { cornerRadius = dp(radiusDp).toFloat() }

    fun ripple(bg: GradientDrawable) = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)

    fun text(s: CharSequence = "", sp: Number = 14, color: Int = Palette.TEXT, face: Typeface? = null) = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        setTextColor(color)
        face?.let { typeface = it }
        includeFontPadding = false
    }

    fun overline(s: String) = text(s.uppercase(), 11, Palette.MUTED, medium).apply { letterSpacing = 0.08f }

    fun vbox(padH: Int = 0, padV: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
    }

    fun hbox() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card() = vbox(18, 18).apply { background = rounded(Palette.SURFACE, 22, Palette.STROKE) }

    fun pill(label: String, bg: Int = Palette.ACCENT, fg: Int = Palette.ON_ACCENT, onClick: () -> Unit) =
        text(label, 15, fg, bold).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = ripple(rounded(bg, 16))
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun ghost(label: String, onClick: () -> Unit) = pill(label, Palette.SURFACE2, Palette.TEXT, onClick)

    fun chip(label: String, color: Int) = text(label, 13, color, bold).apply {
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(Palette.alpha(color, 0x26), 999)
    }

    fun bubble(emoji: String, tint: Int) = text(emoji, 18).apply {
        gravity = Gravity.CENTER
        background = rounded(Palette.alpha(tint, 0x2E), 999)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    fun dot(color: Int, sizeDp: Int = 10) = View(ctx).apply {
        background = rounded(color, 999)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun field(hint: String, value: String = "") = EditText(ctx).apply {
        this.hint = hint
        setText(value)
        setTextColor(Palette.TEXT)
        setHintTextColor(Palette.MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = medium
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        keyListener = DigitsKeyListener.getInstance("0123456789.,")
        setSelectAllOnFocus(true)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Palette.SURFACE2, 14, Palette.STROKE)
    }

    fun space(h: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    /** Barra de progresso fina. */
    fun bar(fraction: Float, color: Int) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(Palette.SURFACE2, 999)
        val f = fraction.coerceIn(0f, 1f)
        addView(View(ctx).apply { background = rounded(color, 999) }, LinearLayout.LayoutParams(0, -1, f.coerceAtLeast(0.001f)))
        addView(View(ctx), LinearLayout.LayoutParams(0, -1, (1f - f).coerceAtLeast(0.001f)))
        layoutParams = LinearLayout.LayoutParams(-1, dp(6))
    }

    /**
     * Android 15 desenha o app por baixo das barras do sistema (edge-to-edge).
     * Isto empurra o conteúdo para baixo da barra de status e para cima da barra de navegação.
     * [onInsets] recebe (topo, base) em pixels para quem precisa ajustar algo além do padding.
     */
    fun fitSystemBars(root: View, onInsets: ((Int, Int) -> Unit)? = null) {
        val baseL = root.paddingLeft; val baseT = root.paddingTop
        val baseR = root.paddingRight; val baseB = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int; val bottom: Int
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                top = bars.top; bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            if (onInsets == null) v.setPadding(baseL, baseT + top, baseR, baseB + bottom)
            else { v.setPadding(baseL, baseT + top, baseR, baseB); onInsets(top, bottom) }
            insets
        }
        root.requestApplyInsets()
    }

    fun lp(w: Int = -1, h: Int = -2, top: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top) }
}

/** Gráfico de barras dos ganhos por dia (desenhado à mão, sem biblioteca). */
class BarChartView(ctx: Context) : View(ctx) {
    var values: List<Float> = emptyList()
    var labels: List<String> = emptyList()
    var highlightLast = true
    /** Índices destacados (se vazio, destaca o último quando highlightLast). */
    var highlight: Set<Int> = emptySet()
    var valueFormatter: (Float) -> String = { it.toInt().toString() }

    private val density = ctx.resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.MUTED; textSize = 11 * density; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT; textSize = 11 * density; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (150 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val n = values.size
        val labelH = 20 * density
        val topPad = 18 * density
        val chartH = height - labelH - topPad
        val slot = width.toFloat() / n
        val barW = slot * 0.56f
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = (v / max * chartH).coerceAtLeast(4 * density)
            val isLast = if (highlight.isNotEmpty()) i in highlight else highlightLast && i == n - 1
            barPaint.color = when {
                v <= 0f -> Palette.SURFACE2
                isLast -> Palette.ACCENT
                else -> Palette.alpha(Palette.ACCENT, 0x66)
            }
            rect.set(cx - barW / 2, topPad + chartH - h, cx + barW / 2, topPad + chartH)
            canvas.drawRoundRect(rect, 8 * density, 8 * density, barPaint)
            if (v > 0f && (isLast || v == max)) canvas.drawText(valueFormatter(v), cx, rect.top - 5 * density, valuePaint)
            textPaint.color = if (isLast) Palette.TEXT else Palette.MUTED
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - 4 * density, textPaint)
        }
    }
}
