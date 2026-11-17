package com.contoh.harrycek

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Pemutar musik: play, pause, berikut, sebelum, volume, dan daftar lagu dari perangkat. */
class PemutarActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var txtJudul: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnMain: TextView
    private lateinit var seekVol: SeekBar
    private lateinit var txtVol: TextView
    private lateinit var listLagu: LinearLayout
    private var signature = ""

    private val penyegar = object : Runnable {
        override fun run() {
            segarkan()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buatLayar())
        segarkan()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(penyegar)
        handler.post(penyegar)
    }

    override fun onPause() {
        handler.removeCallbacks(penyegar)
        super.onPause()
    }

    private fun buatLayar(): LinearLayout {
        val root = akarLatar()
        root.addView(barAtas("Pemutar Musik") { finish() })

        val kartu = kartuTema(this)
        kartu.gravity = Gravity.CENTER_HORIZONTAL
        txtJudul = teks(this, "", 18f, Tema.teks, true)
        txtJudul.gravity = Gravity.CENTER
        kartu.addView(txtJudul)

        txtStatus = teks(this, "", 13f, Tema.cyan, true)
        txtStatus.gravity = Gravity.CENTER
        kartu.addView(txtStatus, lp(PENUH, PAS, 0f, dp(4), 0, 0, 0))

        val kontrol = linear(this, false)
        kontrol.gravity = Gravity.CENTER
        kontrol.addView(tombolBulatAksi("⏮", 58) { PemutarMusik.sebelum(this) }, lp(dp(58), dp(58), 0f, 0, 0, dp(12), 0))
        btnMain = tombolBulatAksi("▶", 76) { tekanMain() }
        kontrol.addView(btnMain, lp(dp(76), dp(76), 0f, 0, 0, dp(12), 0))
        kontrol.addView(tombolBulatAksi("⏭", 58) { PemutarMusik.berikut(this) }, lp(dp(58), dp(58)))
        kartu.addView(kontrol, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))

        txtVol = teks(this, "", 13f, Tema.cyan, true)
        kartu.addView(txtVol, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))
        seekVol = SeekBar(this)
        seekVol.max = 100
        seekVol.progress = Pengaturan.musikVol(this)
        seekVol.progressTintList = ColorStateList.valueOf(Tema.cyan)
        seekVol.thumbTintList = ColorStateList.valueOf(Tema.biru)
        seekVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, nilai: Int, dariUser: Boolean) {
                if (dariUser) PemutarMusik.aturVolume(this@PemutarActivity, nilai)
                txtVol.text = "Volume musik $nilai%  ·  suara kamu 100%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kartu.addView(seekVol)
        root.addView(kartu, lp(PENUH, PAS, 0f, 0, 0, 0, 0))

        val baris = linear(this, false)
        baris.addView(aksiTombol("➕  Tambah Lagu") { tambahLagu() }, lp(0, PAS, 1f, dp(12), 0, dp(6), 0))
        baris.addView(aksiTombol("🗑  Kosongkan") { kosongkan() }, lp(0, PAS, 1f, dp(12), dp(6), 0, 0))
        root.addView(baris, lp(PENUH, PAS))

        root.addView(teks(this, "Daftar lagu · tekan lagu untuk memutar", 14f, Tema.redup, true), lp(PENUH, PAS, 0f, dp(8), 0, 0, dp(4)))

        val scroll = ScrollView(this)
        listLagu = linear(this)
        scroll.addView(listLagu)
        root.addView(scroll, LinearLayout.LayoutParams(PENUH, 0, 1f))
        return root
    }

    private fun segarkan() {
        val idx = Pengaturan.musikIndex(this)
        val list = Playlist.ambil(this)
        val lagu = list.getOrNull(idx)
        txtJudul.text = lagu?.nama ?: "Belum ada lagu"
        val main = PemutarMusik.sedangMain()
        txtStatus.text = when {
            list.isEmpty() -> "Playlist kosong"
            main -> "▶  Memutar"
            else -> "⏸  Dijeda"
        }
        btnMain.text = if (main) "⏸" else "▶"
        val sig = "${list.size}|$idx|${list.map { it.uri }.hashCode()}"
        if (sig != signature) {
            signature = sig
            segarkanDaftar(list, idx)
        }
    }

    private fun segarkanDaftar(list: List<Lagu>, idx: Int) {
        listLagu.removeAllViews()
        if (list.isEmpty()) {
            listLagu.addView(
                teks(this, "Belum ada lagu. Tekan ➕ Tambah Lagu untuk memilih MP3 dari perangkat.", 14f, Tema.redup),
                lp(PENUH, PAS, 0f, dp(8), 0, 0, 0)
            )
            return
        }
        for ((i, lagu) in list.withIndex()) {
            val aktif = i == idx
            val baris = linear(this, false)
            baris.gravity = Gravity.CENTER_VERTICAL
            baris.setPadding(dp(12), dp(10), dp(6), dp(10))
            baris.background = kotak(this, if (aktif) Tema.tombol else Tema.kartu, 14, if (aktif) Tema.cyan else Tema.garis)
            baris.addView(teks(this, "${i + 1}", 14f, Tema.redup, true), lp(dp(28), PAS))
            val nama = teks(this, lagu.nama, 15f, if (aktif) Tema.cyan else Tema.teks, aktif)
            nama.maxLines = 2
            baris.addView(nama, lp(0, PAS, 1f))
            baris.addView(ikonAksi("🗑", Tema.merah) { hapus(i) }, lp(PAS, PAS))
            baris.setOnClickListener {
                PemutarMusik.mainkanIndex(this, i)
                segarkan()
            }
            listLagu.addView(baris, lp(PENUH, PAS, 0f, 0, 0, 0, dp(8)))
        }
    }

    private fun tekanMain() {
        if (PemutarMusik.sedangMain()) {
            PemutarMusik.jeda()
        } else {
            if (Playlist.ambil(this).isEmpty()) {
                Toast.makeText(this, "Tambahkan lagu dulu", Toast.LENGTH_SHORT).show()
                return
            }
            PemutarMusik.mainkan(this)
        }
        segarkan()
    }

    private fun hapus(i: Int) {
        val sedang = PemutarMusik.sedangMain()
        val cur = Pengaturan.musikIndex(this)
        Playlist.hapus(this, i)
        if (Playlist.ambil(this).isEmpty() || (i == cur && sedang)) PemutarMusik.hentikan()
        signature = ""
        segarkan()
    }

    private fun kosongkan() {
        PemutarMusik.hentikan()
        Pengaturan.simpanPlaylistJson(this, "[]")
        Pengaturan.simpanMusikIndex(this, 0)
        signature = ""
        segarkan()
    }

    private fun tambahLagu() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 403)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 403 || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        for (u in uris) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // izin sementara tetap bisa dipakai
            }
            Playlist.tambah(this, u.toString(), namaFileUri(u))
        }
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "${uris.size} lagu ditambahkan", Toast.LENGTH_SHORT).show()
        }
        signature = ""
        segarkan()
    }
}
