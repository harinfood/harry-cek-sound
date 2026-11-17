package com.contoh.harrycek

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Teks ke suara Bahasa Indonesia, dengan nada dan kecepatan dari pengaturan. */
class Suara(ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private var tts: TextToSpeech? = null
    private var siap = false
    private var tertunda: String? = null
    private var urutan = 0

    init {
        tts = TextToSpeech(appCtx) { status -> aturSiap(status) }
    }

    private fun aturSiap(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val hasil = t.setLanguage(Locale("id", "ID"))
        if (hasil == TextToSpeech.LANG_MISSING_DATA || hasil == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.getDefault())
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                selesai(utteranceId, true)
            }
            override fun onError(utteranceId: String?) {
                selesai(utteranceId, false)
            }
        })
        siap = true
        val p = tertunda
        tertunda = null
        if (p != null) bicara(p)
    }

    private fun selesai(id: String?, ok: Boolean) {
        if (id == null) return
        val cb = callbacks.remove(id) ?: return
        handler.post { cb(ok) }
    }

    private fun terapkan(t: TextToSpeech) {
        t.setPitch(Pengaturan.nada(appCtx))
        t.setSpeechRate(Pengaturan.kecepatan(appCtx))
    }

    fun bicara(teks: String) {
        if (teks.isBlank()) return
        if (!siap) {
            tertunda = teks
            return
        }
        val t = tts ?: return
        terapkan(t)
        urutan++
        t.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "hcs$urutan")
    }

    /** Mengubah teks menjadi berkas WAV, lalu memanggil onSelesai(true/false) di thread utama. */
    fun keBerkas(teks: String, berkas: File, onSelesai: (Boolean) -> Unit) {
        val t = tts
        if (t == null || !siap) {
            handler.post { onSelesai(false) }
            return
        }
        terapkan(t)
        urutan++
        val id = "hcsf$urutan"
        callbacks[id] = onSelesai
        val hasil = t.synthesizeToFile(teks, null, berkas, id)
        if (hasil != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            handler.post { onSelesai(false) }
        }
    }

    fun tutup() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        siap = false
    }
}
