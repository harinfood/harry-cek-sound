package com.contoh.harrycek

import android.content.Context

/** Semua pengaturan aplikasi: teks, gaya suara, iTag, mic, dan rekaman terakhir. */
object Pengaturan {
    private const val NAMA = "harry_cek_sound"

    private fun p(c: Context) = c.getSharedPreferences(NAMA, Context.MODE_PRIVATE)

    fun teksTts(c: Context): String = p(c).getString("teks", "Halo, ini suara saya.") ?: ""

    fun simpanTeks(c: Context, t: String) {
        p(c).edit().putString("teks", t).apply()
    }

    fun nada(c: Context): Float = p(c).getFloat("nada", 1.0f)

    fun kecepatan(c: Context): Float = p(c).getFloat("kecepatan", 1.0f)

    fun gaya(c: Context): String = p(c).getString("gaya", "normal") ?: "normal"

    fun simpanSuara(c: Context, nada: Float, kecepatan: Float, gaya: String) {
        p(c).edit().putFloat("nada", nada).putFloat("kecepatan", kecepatan).putString("gaya", gaya).apply()
    }

    fun itagMac(c: Context): String? = p(c).getString("itag_mac", null)

    fun simpanItag(c: Context, mac: String?) {
        val e = p(c).edit()
        if (mac == null) e.remove("itag_mac") else e.putString("itag_mac", mac)
        e.apply()
    }

    fun micBluetooth(c: Context): Boolean = p(c).getBoolean("mic_bt", false)

    fun simpanMicBt(c: Context, v: Boolean) {
        p(c).edit().putBoolean("mic_bt", v).apply()
    }

    fun rekamanTerakhir(c: Context): String? = p(c).getString("rekaman_terakhir", null)

    fun simpanRekamanTerakhir(c: Context, path: String?) {
        val e = p(c).edit()
        if (path == null) e.remove("rekaman_terakhir") else e.putString("rekaman_terakhir", path)
        e.apply()
    }
}
