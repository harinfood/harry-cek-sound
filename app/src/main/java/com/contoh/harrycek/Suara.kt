package com.contoh.harrycek

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Teks ke suara Bahasa Indonesia. Nada dan kecepatan mengikuti pengaturan gaya suara. */
class Suara(ctx: Context) {

    private val appCtx = ctx.applicationContext
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
        siap = true
        val p = tertunda
        tertunda = null
        if (p != null) bicara(p)
    }

    fun bicara(teks: String) {
        if (teks.isBlank()) return
        if (!siap) {
            tertunda = teks
            return
        }
        val t = tts ?: return
        t.setPitch(Pengaturan.nada(appCtx))
        t.setSpeechRate(Pengaturan.kecepatan(appCtx))
        urutan++
        t.speak(teks, TextToSpeech.QUEUE_FLUSH, null, "hcs$urutan")
    }

    fun tutup() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        siap = false
    }
}
