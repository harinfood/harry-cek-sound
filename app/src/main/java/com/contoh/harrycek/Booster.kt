package com.contoh.harrycek

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.LoudnessEnhancer
import android.media.MediaPlayer

/**
 * Pemutar sekali-jalan untuk bunyi iTag (bel, rekaman, TTS).
 * Volume stream musik dinaikkan ke maksimum, lalu diperkuat lagi dengan booster (dB).
 */
object Booster {

    private var mp: MediaPlayer? = null
    private var enh: LoudnessEnhancer? = null
    private var volAwal = -1
    private var am: AudioManager? = null

    fun putar(ctx: Context, sumber: (MediaPlayer) -> Unit, onSelesai: (() -> Unit)? = null) {
        stop()
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            sumber(p)
            p.prepare()
        } catch (e: Exception) {
            p.release()
            throw e
        }

        val mgr = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am = mgr
        volAwal = mgr.getStreamVolume(AudioManager.STREAM_MUSIC)
        mgr.setStreamVolume(AudioManager.STREAM_MUSIC, mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        p.setVolume(1f, 1f)

        try {
            enh = LoudnessEnhancer(p.audioSessionId).apply {
                setTargetGain(Pengaturan.boosterDb(ctx) * 100)
                enabled = true
            }
        } catch (e: Exception) {
            enh = null
        }

        p.setOnCompletionListener {
            stop()
            onSelesai?.invoke()
        }
        p.start()
        mp = p
    }

    fun stop() {
        try { mp?.stop() } catch (e: Exception) { }
        mp?.release()
        mp = null
        try { enh?.release() } catch (e: Exception) { }
        enh = null
        val mgr = am
        if (mgr != null && volAwal >= 0) {
            mgr.setStreamVolume(AudioManager.STREAM_MUSIC, volAwal, 0)
        }
        volAwal = -1
        am = null
    }
}
