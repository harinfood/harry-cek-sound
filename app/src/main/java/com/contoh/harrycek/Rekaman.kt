package com.contoh.harrycek

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Berkas rekaman WAV yang tersimpan di penyimpanan aplikasi. */
object Rekaman {

    fun folder(c: Context): File {
        val f = File(c.filesDir, "rekaman")
        if (!f.exists()) f.mkdirs()
        return f
    }

    fun baru(c: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(folder(c), "rekam_$stamp.wav")
    }

    /** Daftar rekaman, yang terbaru di depan. */
    fun daftar(c: Context): List<File> =
        folder(c).listFiles()
            ?.filter { it.name.endsWith(".wav") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun waktuTampil(f: File): String =
        SimpleDateFormat("dd MMM HH:mm:ss", Locale("id", "ID")).format(Date(f.lastModified()))
}
