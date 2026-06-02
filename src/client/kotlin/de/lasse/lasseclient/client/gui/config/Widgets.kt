package de.lasse.lasseclient.client.gui.config

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Low-level drawing + animation helpers for the custom Lasse Client config screen.
 *
 * Everything here is pure: no state beyond what the caller passes in. The "rounded" rectangles
 * are chamfered (corners cut by `r` px) rather than truly circular — at GUI scale this reads as a
 * clean, soft corner without the cost of per-pixel arc rasterisation.
 */
@Environment(EnvType.CLIENT)
object Widgets {

    const val RADIUS = 3

    // ---- animation ------------------------------------------------------------------------------

    /**
     * Frame-rate-independent exponential smoothing. Moves [current] toward [target]; `rate` is the
     * approximate "speed" (higher = snappier). [dt] is seconds since the last frame.
     */
    fun approach(current: Float, target: Float, dt: Float, rate: Float = 16f): Float {
        if (kotlin.math.abs(target - current) < 0.0005f) return target
        val t = 1f - exp(-rate * dt)
        return current + (target - current) * t
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // ---- colours --------------------------------------------------------------------------------

    fun a(c: Int): Int = (c ushr 24) and 0xFF
    fun r(c: Int): Int = (c ushr 16) and 0xFF
    fun g(c: Int): Int = (c ushr 8) and 0xFF
    fun b(c: Int): Int = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun withAlpha(c: Int, alpha: Int): Int = (c and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    fun scaleAlpha(c: Int, factor: Float): Int =
        withAlpha(c, (a(c) * factor).roundToInt().coerceIn(0, 255))

    fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return argb(
            (a(c1) + (a(c2) - a(c1)) * tt).roundToInt(),
            (r(c1) + (r(c2) - r(c1)) * tt).roundToInt(),
            (g(c1) + (g(c2) - g(c1)) * tt).roundToInt(),
            (b(c1) + (b(c2) - b(c1)) * tt).roundToInt(),
        )
    }

    // ---- shapes ---------------------------------------------------------------------------------

    /** Filled rectangle with chamfered corners of radius [rad]. */
    fun roundedRect(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int, rad: Int = RADIUS) {
        if (w <= 0 || h <= 0) return
        val r = rad.coerceAtMost(minOf(w, h) / 2)
        val x2 = x + w
        val y2 = y + h
        if (r <= 0) {
            ctx.fill(x, y, x2, y2, color)
            return
        }
        ctx.fill(x + r, y, x2 - r, y2, color)
        ctx.fill(x, y + r, x + r, y2 - r, color)
        ctx.fill(x2 - r, y + r, x2, y2 - r, color)
    }

    /**
     * Filled circle, centred on ([cx],[cy]) with radius [rad]. Scans rows and fills a horizontal
     * span per row so the result is a smooth disc rather than the plus-shape that [roundedRect]
     * degenerates into for tiny squares.
     */
    fun filledCircle(ctx: DrawContext, cx: Float, cy: Float, rad: Float, color: Int) {
        if (rad <= 0f) return
        val r2 = rad * rad
        val top = kotlin.math.floor(cy - rad).toInt()
        val bottom = kotlin.math.ceil(cy + rad).toInt()
        for (py in top..bottom) {
            val dy = py + 0.5f - cy
            val inside = r2 - dy * dy
            if (inside < 0f) continue
            val dx = kotlin.math.sqrt(inside)
            val x1 = kotlin.math.round(cx - dx).toInt()
            val x2 = kotlin.math.round(cx + dx).toInt()
            if (x2 > x1) ctx.fill(x1, py, x2, py + 1, color)
        }
    }

    /** 1px chamfered outline. */
    fun roundedOutline(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int, rad: Int = RADIUS) {
        if (w <= 0 || h <= 0) return
        val r = rad.coerceAtMost(minOf(w, h) / 2)
        val x2 = x + w
        val y2 = y + h
        ctx.fill(x + r, y, x2 - r, y + 1, color)
        ctx.fill(x + r, y2 - 1, x2 - r, y2, color)
        ctx.fill(x, y + r, x + 1, y2 - r, color)
        ctx.fill(x2 - 1, y + r, x2, y2 - r, color)
    }

    // ---- composite widgets ----------------------------------------------------------------------

    const val TOGGLE_W = 24
    const val TOGGLE_H = 13

    /** A pill toggle. [on01] is the animated on-ness (0 = off, 1 = on). */
    fun toggle(ctx: DrawContext, x: Int, y: Int, on01: Float, offColor: Int, onColor: Int, knobColor: Int) {
        val track = lerpColor(offColor, onColor, on01)
        roundedRect(ctx, x, y, TOGGLE_W, TOGGLE_H, track, TOGGLE_H / 2)
        val knobR = (TOGGLE_H - 4) / 2f
        val travel = TOGGLE_W - 4 - knobR * 2
        val kcx = x + 2 + knobR + travel * on01
        filledCircle(ctx, kcx, y + TOGGLE_H / 2f, knobR, knobColor)
    }

    /**
     * Horizontal slider. Returns nothing; purely visual. [progress] is 0..1.
     * Track sits vertically centred in [h].
     */
    fun slider(
        ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, progress: Float,
        trackColor: Int, fillColor: Int, knobColor: Int,
    ) {
        val p = progress.coerceIn(0f, 1f)
        val trackH = 4
        val ty = y + (h - trackH) / 2
        roundedRect(ctx, x, ty, w, trackH, trackColor, 2)
        val fillW = (w * p).roundToInt()
        if (fillW > 0) roundedRect(ctx, x, ty, fillW, trackH, fillColor, 2)
        val knobR = 4.5f
        val kcx = (x + knobR + (w - knobR * 2) * p).coerceIn(x + knobR, x + w - knobR)
        filledCircle(ctx, kcx, y + h / 2f, knobR + 0.5f, knobColor)
    }

    /** True if (mx,my) lies inside the rect. */
    fun inside(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx < x + w && my >= y && my < y + h

    // ---- strokes --------------------------------------------------------------------------------

    /** Thin line via DDA stepping; good enough at GUI scale. */
    fun line(ctx: DrawContext, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Int = 1) {
        val dx = x2 - x1
        val dy = y2 - y1
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)).toInt().coerceAtLeast(1)
        val half = thickness / 2
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val px = (x1 + dx * t).roundToInt()
            val py = (y1 + dy * t).roundToInt()
            ctx.fill(px - half, py - half, px - half + thickness, py - half + thickness, color)
        }
    }

    /**
     * Chevron that morphs from pointing right ([anim]=0, ">") to pointing down ([anim]=1, "v").
     * Centred on ([cx],[cy]); [s] is the half-extent.
     */
    fun chevron(ctx: DrawContext, cx: Int, cy: Int, s: Int, anim: Float, color: Int) {
        val t = anim.coerceIn(0f, 1f)
        // right ">": A top-left, B mid-right, C bottom-left
        val rAx = cx - s; val rAy = cy - s
        val rBx = cx + s; val rBy = cy
        val rCx = cx - s; val rCy = cy + s
        // down "v": A left, B mid-bottom, C right
        val dAx = cx - s; val dAy = cy - s / 2
        val dBx = cx;     val dBy = cy + s / 2
        val dCx = cx + s; val dCy = cy - s / 2
        val ax = lerp(rAx.toFloat(), dAx.toFloat(), t); val ay = lerp(rAy.toFloat(), dAy.toFloat(), t)
        val bx = lerp(rBx.toFloat(), dBx.toFloat(), t); val by = lerp(rBy.toFloat(), dBy.toFloat(), t)
        val cxx = lerp(rCx.toFloat(), dCx.toFloat(), t); val cyy = lerp(rCy.toFloat(), dCy.toFloat(), t)
        line(ctx, ax, ay, bx, by, color, 1)
        line(ctx, bx, by, cxx, cyy, color, 1)
    }
}
