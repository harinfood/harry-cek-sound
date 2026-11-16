package com.contoh.harrycek

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Pengaturan suara: pilihan gaya dan atur nada serta kecepatan. */
class SettingsActivity : Activity() {

    data class Gaya(val kode: String, val nama: String, val nada: Float, val kecepatan: Float)

    private val gayaList = listOf(
        Gaya("normal", "Normal", 1.0f, 1.0f),
        Gaya("anak", "Suara Anak", 1.6f, 1.15f),
        Gaya("berat", "Suara Berat", 0.7f, 0.9f),
        Gaya("robot", "Robot", 0.55f, 0.8f),
        Gaya("cepat", "Cepat", 1.2f, 1.5f),
        Gaya("pelan", "Pelan & Jelas", 0.9f, 0.7f)
    )

    private lateinit var suara: Suara
    private lateinit var seekNada: SeekBar
    private lateinit var seekKec: SeekBar
    private lateinit var txtNada: TextView
    private lateinit var txtKec: TextView
    private val chipGaya = HashMap<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suara = Suara(this)
        setContentView(buatLayar())
        segarkanChip()
    }

    override fun onDestroy() {
        suara.tutup()
        super.onDestroy()
    }

    private fun nilaiDariSeek(p: Int): Float = (p + 50) / 100f

    private fun seekDariNilai(v: Float): Int = Math.round(v * 100f) - 50

    private fun simpan(gaya: String) {
        val nada = nilaiDariSeek(seekNada.progress)
        val kec = nilaiDariSeek(seekKec.progress)
        Pengaturan.simpanSuara(this, nada, kec, gaya)
        txtNada.text = "Nada: ${String.format("%.2f", nada)}"
        txtKec.text = "Kecepatan: ${String.format("%.2f", kec)}"
        segarkanChip()
    }

    private fun segarkanChip() {
        val aktif = Pengaturan.gaya(this)
        for ((kode, t) in chipGaya) {
            val on = kode == aktif
            t.background = if (on) kotakGradien(this, Tema.biru, Tema.cyan, 14) else kotak(this, Tema.tombol, 14)
            t.setTextColor(if (on) Color.parseColor("#0A1628") else Tema.redup)
        }
    }

    private fun buatLayar(): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Tema.latarAtas)
        val root = akarLatar()
        scroll.addView(root)
        root.addView(barAtas("Atur Suara") { finish() })

        root.addView(teks(this, "Pilih gaya suara", 15f, Tema.teks, true).apply { setPadding(dp(4), dp(6), 0, dp(8)) })
        for (baris in gayaList.chunked(3)) {
            val row = linear(this, false)
            for (g in baris) {
                val t = teks(this, g.nama, 12f, Tema.teks, true)
                t.gravity = Gravity.CENTER
                t.setPadding(dp(4), dp(10), dp(4), dp(10))
                t.setOnClickListener {
                    seekNada.progress = seekDariNilai(g.nada)
                    seekKec.progress = seekDariNilai(g.kecepatan)
                    simpan(g.kode)
                }
                chipGaya[g.kode] = t
                row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), 0, dp(3), dp(8)) })
            }
            root.addView(row)
        }

        val kartu = kartuTema(this)
        kartu.addView(teks(this, "Atur manual", 15f, Tema.teks, true))

        txtNada = teks(this, "", 13f, Tema.redup)
        seekNada = SeekBar(this)
        seekNada.max = 150
        seekNada.progress = seekDariNilai(Pengaturan.nada(this))
        seekNada.progressTintList = android.content.res.ColorStateList.valueOf(Tema.cyan)
        seekNada.thumbTintList = android.content.res.ColorStateList.valueOf(Tema.biru)
        seekNada.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) simpan("kustom") else txtNada.text = "Nada: ${String.format("%.2f", nilaiDariSeek(p))}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kartu.addView(txtNada)
        kartu.addView(seekNada)

        txtKec = teks(this, "", 13f, Tema.redup)
        seekKec = SeekBar(this)
        seekKec.max = 150
        seekKec.progress = seekDariNilai(Pengaturan.kecepatan(this))
        seekKec.progressTintList = android.content.res.ColorStateList.valueOf(Tema.cyan)
        seekKec.thumbTintList = android.content.res.ColorStateList.valueOf(Tema.biru)
        seekKec.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) simpan("kustom") else txtKec.text = "Kecepatan: ${String.format("%.2f", nilaiDariSeek(p))}"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kartu.addView(txtKec)
        kartu.addView(seekKec)
        txtNada.text = "Nada: ${String.format("%.2f", Pengaturan.nada(this))}"
        txtKec.text = "Kecepatan: ${String.format("%.2f", Pengaturan.kecepatan(this))}"
        root.addView(kartu, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(8), 0, 0, 0))

        val coba = teks(this, "🔊  Coba Suara", 16f, Tema.teks, true)
        coba.gravity = Gravity.CENTER
        coba.setPadding(dp(12), dp(14), dp(12), dp(14))
        coba.background = riak(this, Tema.biru, 20)
        coba.setOnClickListener { suara.bicara("Halo, ini contoh suara kamu. Cek microphone bersama Harinfood.") }
        root.addView(coba, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(16), 0, 0, 0))

        return scroll
    }
}
