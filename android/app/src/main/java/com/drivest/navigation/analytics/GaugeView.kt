package com.drivest.navigation.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.drivest.navigation.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Speedometer-style semicircular gauge (0–100).
 * Three colour zones: red (0–39), amber (40–69), green (70–100).
 * Set [progress], [label], and [onDark] programmatically or via XML attrs.
 */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Value 0–100 to display on the gauge. */
    var progress: Int = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }

    /** Short label drawn beneath the arc. */
    var label: String = ""
        set(value) { field = value; invalidate() }

    /** True when rendered on a dark background (hero card). */
    var onDark: Boolean = false
        set(value) { field = value; invalidate() }

    /**
     * When true, colour zones are flipped: green = low values (good), red = high values (bad).
     * Use for hazard-exposure gauges where high exposure = needs work.
     */
    var inverted: Boolean = false
        set(value) { field = value; invalidate() }

    // -- Colours --
    private val colorRed   = Color.parseColor("#EF4444")
    private val colorAmber = Color.parseColor("#F59E0B")
    private val colorGreen = Color.parseColor("#22C55E")

    // -- Paints --
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.GaugeView)
            onDark   = ta.getBoolean(R.styleable.GaugeView_gaugeDark, false)
            label    = ta.getString(R.styleable.GaugeView_gaugeLabel) ?: ""
            inverted = ta.getBoolean(R.styleable.GaugeView_gaugeInverted, false)
            ta.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val desired = (w * 0.65f).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY   -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST   -> min(desired, MeasureSpec.getSize(heightMeasureSpec))
            else                  -> desired
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w  = width.toFloat()
        val cx = w / 2f

        // Cap radius so content always fits within actual height
        val r  = min(w * 0.38f, (height - 32f) / 1.22f)
        val sw = r * 0.22f
        val cy = r + sw + 6f                    // arc-centre Y

        val oval = RectF(cx - r, cy - r, cx + r, cy + r)

        // ── 1. Track (full 180° grey arc) ──────────────────────────────
        arcPaint.strokeWidth = sw
        arcPaint.color = if (onDark) 0x40FFFFFF else Color.parseColor("#E7E9F2")
        canvas.drawArc(oval, 180f, 180f, false, arcPaint)

        // ── 2. Colour zones (gaps of 1° for clean separation) ──────────
        if (inverted) {
            // Inverted: green (low=good), amber (mid), red (high=bad)
            // Green 0–30%  → 0–54°  starting at 180°
            arcPaint.color = colorGreen; canvas.drawArc(oval, 180f, 53f, false, arcPaint)
            // Amber 30–60% → 54–108° starting at 234°
            arcPaint.color = colorAmber; canvas.drawArc(oval, 234f, 53f, false, arcPaint)
            // Red   60–100%→ 108–180° starting at 288°
            arcPaint.color = colorRed;   canvas.drawArc(oval, 288f, 71f, false, arcPaint)
        } else {
            // Normal: red (low=bad), amber (mid), green (high=good)
            // Red   0–40%  → 0–72°  starting at 180°
            arcPaint.color = colorRed;   canvas.drawArc(oval, 180f, 71f, false, arcPaint)
            // Amber 40–70% → 72–126° starting at 252°
            arcPaint.color = colorAmber; canvas.drawArc(oval, 252f, 53f, false, arcPaint)
            // Green 70–100%→ 126–180° starting at 306°
            arcPaint.color = colorGreen; canvas.drawArc(oval, 306f, 53f, false, arcPaint)
        }

        // ── 3. White tick marks (11 marks, every 18°) ──────────────────
        tickPaint.strokeWidth = sw * 0.13f
        for (i in 0..10) {
            val a  = Math.toRadians((180 + i * 18).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            canvas.drawLine(
                cx + (r - sw * 0.58f) * ca,  cy + (r - sw * 0.58f) * sa,
                cx + (r + sw * 0.58f) * ca,  cy + (r + sw * 0.58f) * sa,
                tickPaint
            )
        }

        // ── 4. Needle ──────────────────────────────────────────────────
        val na = Math.toRadians(180.0 + progress * 1.8)
        val nc = cos(na).toFloat()
        val ns = sin(na).toFloat()
        val needleColor = if (inverted) {
            when {
                progress < 30 -> colorGreen
                progress < 60 -> colorAmber
                else          -> colorRed
            }
        } else {
            when {
                progress < 40 -> colorRed
                progress < 70 -> colorAmber
                else          -> colorGreen
            }
        }
        needlePaint.color       = needleColor
        needlePaint.strokeWidth = sw * 0.28f
        canvas.drawLine(
            cx - r * 0.14f * nc,  cy - r * 0.14f * ns,
            cx + r * 0.80f * nc,  cy + r * 0.80f * ns,
            needlePaint
        )

        // ── 5. Pivot dot ───────────────────────────────────────────────
        dotPaint.color = needleColor
        canvas.drawCircle(cx, cy, sw * 0.52f, dotPaint)
        dotPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, sw * 0.30f, dotPaint)

        val primary   = if (onDark) Color.WHITE                   else Color.parseColor("#1F1B26")
        val secondary = if (onDark) Color.parseColor("#DCE7FF")   else Color.parseColor("#686271")

        // ── 6. Value text (inside arc) ─────────────────────────────────
        valuePaint.color    = primary
        valuePaint.textSize = r * 0.38f
        canvas.drawText("$progress%", cx, cy - r * 0.20f, valuePaint)

        // ── 7. Label beneath chord line ────────────────────────────────
        if (label.isNotEmpty()) {
            labelPaint.color     = secondary
            labelPaint.textSize  = r * 0.21f
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, cx, cy + sw * 1.30f, labelPaint)
        }

        // ── 8. Min / Max edge labels ───────────────────────────────────
        labelPaint.color    = secondary
        labelPaint.textSize = r * 0.17f
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("0", cx - r - sw * 0.30f, cy + labelPaint.textSize, labelPaint)
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("100", cx + r + sw * 0.30f, cy + labelPaint.textSize, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER  // reset for next draw
    }
}
