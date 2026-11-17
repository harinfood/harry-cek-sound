package com.contoh.harrycek

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.TextView

/** Ukuran lebar/tinggi layout: penuh dan menyesuaikan isi. */
const val PENUH = -1
const val PAS = -2

/** Tombol persegi panjang dengan tulisan. */
fun Context.aksiTombol(label: String, aksi: () -> Unit): TextView {
    val t = teks(this, label, 15f, Tema.teks, true)
    t.gravity = Gravity.CENTER
    t.setPadding(dp(12), dp(12), dp(12), dp(12))
    t.background = riak(this, Tema.tombol, 14, Tema.garis)
    t.setOnClickListener { aksi() }
    return t
}

/** Tombol bulat untuk kontrol pemutar. */
fun Context.tombolBulatAksi(label: String, ukuranDp: Int, aksi: () -> Unit): TextView {
    val t = teks(this, label, 22f, Tema.teks, true)
    t.gravity = Gravity.CENTER
    t.background = riak(this, Tema.tombol, ukuranDp / 2, Tema.biru)
    t.setOnClickListener { aksi() }
    return t
}

/** Ikon kecil yang bisa ditekan (misalnya putar atau hapus). */
fun Context.ikonAksi(label: String, warna: Int, aksi: () -> Unit): TextView {
    val t = teks(this, label, 18f, warna, true)
    t.setPadding(dp(10), dp(4), dp(10), dp(4))
    t.setOnClickListener { aksi() }
    return t
}

/** Nama berkas dari sebuah Uri (untuk ditampilkan). */
fun Context.namaFileUri(uri: Uri): String {
    var nama = "musik.mp3"
    try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) nama = c.getString(i) ?: nama
        }
    } catch (e: Exception) {
        // pakai nama default
    }
    return nama
}
