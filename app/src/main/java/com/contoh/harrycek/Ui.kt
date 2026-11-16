package com.contoh.harrycek

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)

fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density

/** Warna tema biru neon Harry Cek Sound. */
object Tema {
    val latarAtas = Color.parseColor("#0A1628")
    val latarBawah = Color.parseColor("#0F2A4A")
    val kartu = Color.parseColor("#132C4F")
    val garis = Color.parseColor("#1F5A96")
    val tombol = Color.parseColor("#1B3F6E")
    val biru = Color.parseColor("#00B0FF")
    val cyan = Color.parseColor("#00E5FF")
    val merah = Color.parseColor("#FF4D6D")
    val hijau = Color.parseColor("#00E676")
    val kuning = Color.parseColor("#FFD600")
    val teks = Color.WHITE
    val redup = Color.parseColor("#8FB3D9")
}

fun kotak(ctx: Context, warna: Int, radiusDp: Int, garis: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(warna)
    g.setCornerRadius(ctx.dpF(radiusDp.toFloat()))
    if (garis != 0) g.setStroke(ctx.dp(1), garis)
    return g
}

fun kotakGradien(ctx: Context, c1: Int, c2: Int, radiusDp: Int): GradientDrawable {
    val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2))
    g.setCornerRadius(ctx.dpF(radiusDp.toFloat()))
    return g
}

fun riak(ctx: Context, warna: Int, radiusDp: Int, garis: Int = 0): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), kotak(ctx, warna, radiusDp, garis), null)

fun teks(ctx: Context, isi: String, sp: Float, warna: Int, tebal: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = isi
    t.textSize = sp
    t.setTextColor(warna)
    if (tebal) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun linear(ctx: Context, vertikal: Boolean = true): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = if (vertikal) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    return l
}

fun lp(
    lebar: Int,
    tinggi: Int,
    bobot: Float = 0f,
    atas: Int = 0,
    kiri: Int = 0,
    kanan: Int = 0,
    bawah: Int = 0
): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(lebar, tinggi, bobot)
    p.setMargins(kiri, atas, kanan, bawah)
    return p
}

fun kartuTema(ctx: Context): LinearLayout {
    val k = linear(ctx)
    k.setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
    k.background = kotak(ctx, Tema.kartu, 20, Tema.garis)
    return k
}

fun Context.akarLatar(): LinearLayout {
    val r = linear(this)
    r.setPadding(dp(14), dp(12), dp(14), dp(24))
    r.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Tema.latarAtas, Tema.latarBawah))
    return r
}

fun Context.barAtas(judul: String, onKembali: () -> Unit): LinearLayout {
    val bar = linear(this, false)
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(0, dp(6), 0, dp(10))
    val kembali = teks(this, "←", 24f, Tema.kuning, true)
    kembali.gravity = Gravity.CENTER
    kembali.setPadding(dp(8), 0, dp(14), 0)
    kembali.setOnClickListener { onKembali() }
    bar.addView(kembali)
    bar.addView(teks(this, judul, 20f, Tema.teks, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    return bar
}
