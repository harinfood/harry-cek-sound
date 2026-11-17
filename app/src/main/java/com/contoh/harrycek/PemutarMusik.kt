package com.contoh.harrycek

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * Pemutar musik utama: play, pause, berikut, sebelum, dan volume.
 * Saat merekam dengan musik latar, kendali diteruskan ke mikrofon agar musik ikut tercampur.
 */
object PemutarMusik {

    var mikro: Mikrofon? = null
    var onBerubah: (() -> Unit)? = null

    private var mp: MediaPlayer? = null
    private var sedangMain = false
    private var micMusik = false

    fun sedangMain(): Boolean = sedangMain

    private fun viaMikro(): Boolean = micMusik && mikro?.sedangJalan() == true

    fun mainkanIndex(ctx: Context, i: Int) {
        val lagu = Playlist.lagu(ctx, i) ?: return
        Pengaturan.simpanMusikIndex(ctx, i)
        val vol = Pengaturan.musikVol(ctx) / 100f
        if (viaMikro()) {
            val m = mikro
            if (m != null) {
                m.volMusikLive = vol
                m.musikPutar = true
                m.musikGanti = Uri.parse(lagu.uri)
                sedangMain = true
            }
        } else {
            lepasMp()
            try {
                val p = MediaPlayer()
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                p.setDataSource(ctx, Uri.parse(lagu.uri))
                p.prepare()
                p.setVolume(vol, vol)
                p.setOnCompletionListener { berikut(ctx) }
                p.start()
                mp = p
                sedangMain = true
            } catch (e: Exception) {
                lepasMp()
                sedangMain = false
            }
        }
        onBerubah?.invoke()
    }

    fun mainkan(ctx: Context) {
        if (viaMikro()) {
            mikro?.musikPutar = true
            sedangMain = true
        } else {
            val p = mp
            var sukses = false
            if (p != null) {
                try {
                    p.start()
                    sukses = true
                } catch (e: Exception) {
                    sukses = false
                }
            }
            if (sukses) sedangMain = true else mainkanIndex(ctx, Pengaturan.musikIndex(ctx))
        }
        onBerubah?.invoke()
    }

    fun jeda() {
        if (viaMikro()) {
            mikro?.musikPutar = false
        } else {
            try { mp?.pause() } catch (e: Exception) { }
        }
        sedangMain = false
        onBerubah?.invoke()
    }

    fun berikut(ctx: Context) {
        val n = Playlist.ambil(ctx).size
        if (n == 0) return
        mainkanIndex(ctx, (Pengaturan.musikIndex(ctx) + 1) % n)
    }

    fun sebelum(ctx: Context) {
        val n = Playlist.ambil(ctx).size
        if (n == 0) return
        mainkanIndex(ctx, (Pengaturan.musikIndex(ctx) - 1 + n) % n)
    }

    fun aturVolume(ctx: Context, persen: Int) {
        Pengaturan.simpanMusikVol(ctx, persen)
        val v = persen / 100f
        try { mp?.setVolume(v, v) } catch (e: Exception) { }
        mikro?.volMusikLive = v
    }

    fun hentikan() {
        mikro?.musikPutar = false
        lepasMp()
        sedangMain = false
        onBerubah?.invoke()
    }

    private fun lepasMp() {
        try { mp?.stop() } catch (e: Exception) { }
        mp?.release()
        mp = null
    }

    /** Dipanggil saat mikrofon mulai. Jika memakai musik latar, pemutar biasa dijeda. */
    fun serahkanKeMikro(pakaiMusik: Boolean) {
        micMusik = pakaiMusik
        if (pakaiMusik) {
            try { mp?.pause() } catch (e: Exception) { }
        }
    }

    /** Dipanggil saat mikrofon berhenti. */
    fun selesaiMikro() {
        if (micMusik) {
            micMusik = false
            sedangMain = false
        }
        onBerubah?.invoke()
    }
}
