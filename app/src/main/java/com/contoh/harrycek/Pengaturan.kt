package com.contoh.harrycek

import android.content.Context

/** Semua pengaturan aplikasi. */
object Pengaturan {
    private const val NAMA = "harry_cek_sound"

    private fun p(c: Context) = c.getSharedPreferences(NAMA, Context.MODE_PRIVATE)

    // ---- teks ke suara ----
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

    // ---- iTag ----
    fun itagMac(c: Context): String? = p(c).getString("itag_mac", null)

    fun simpanItag(c: Context, mac: String?) {
        val e = p(c).edit()
        if (mac == null) e.remove("itag_mac") else e.putString("itag_mac", mac)
        e.apply()
    }

    /** MP3 khusus untuk bunyi bel (1x tekan). */
    fun belUri(c: Context): String? = p(c).getString("bel_uri", null)

    fun belNama(c: Context): String = p(c).getString("bel_nama", "") ?: ""

    fun simpanBel(c: Context, uri: String?, nama: String) {
        val e = p(c).edit()
        if (uri == null) e.remove("bel_uri") else e.putString("bel_uri", uri)
        e.putString("bel_nama", nama).apply()
    }

    /** Booster bunyi iTag dalam dB (0-15), di atas volume 100%. */
    fun boosterDb(c: Context): Int = p(c).getInt("booster_db", 6)

    fun simpanBoosterDb(c: Context, v: Int) {
        p(c).edit().putInt("booster_db", v.coerceIn(0, 15)).apply()
    }

    // ---- mic ----
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

    // ---- musik latar & pemutar ----
    fun musikAktif(c: Context): Boolean = p(c).getBoolean("musik_aktif", false)

    fun simpanMusikAktif(c: Context, v: Boolean) {
        p(c).edit().putBoolean("musik_aktif", v).apply()
    }

    /** Volume musik 0-100. Vokal rekaman selalu 100%. Default 30. */
    fun musikVol(c: Context): Int = p(c).getInt("musik_vol", 30)

    fun simpanMusikVol(c: Context, v: Int) {
        p(c).edit().putInt("musik_vol", v.coerceIn(0, 100)).apply()
    }

    fun musikIndex(c: Context): Int = p(c).getInt("musik_index", 0)

    fun simpanMusikIndex(c: Context, i: Int) {
        p(c).edit().putInt("musik_index", i).apply()
    }

    fun playlistJson(c: Context): String = p(c).getString("playlist", "[]") ?: "[]"

    fun simpanPlaylistJson(c: Context, s: String) {
        p(c).edit().putString("playlist", s).apply()
    }
}
