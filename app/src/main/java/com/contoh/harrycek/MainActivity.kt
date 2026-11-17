package com.contoh.harrycek

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mikro: Mikrofon
    private lateinit var suara: Suara

    // mikrofon
    private lateinit var chipHp: TextView
    private lateinit var chipBt: TextView
    private lateinit var barLevel: ProgressBar
    private lateinit var btnDengar: TextView
    private lateinit var btnRekam: TextView
    private lateinit var swDengarRekam: Switch
    private lateinit var txtTimer: TextView

    // musik latar & pemutar
    private lateinit var txtLagu: TextView
    private lateinit var btnMainUtama: TextView
    private lateinit var swMusik: Switch
    private lateinit var seekMusik: SeekBar
    private lateinit var txtVolMusik: TextView

    // teks ke suara
    private lateinit var etTeks: EditText
    private lateinit var txtSuara: TextView

    // iTag
    private lateinit var txtItag: TextView
    private lateinit var btnItag: TextView
    private lateinit var txtBel: TextView
    private lateinit var seekBooster: SeekBar
    private lateinit var txtBooster: TextView

    private lateinit var listRekaman: LinearLayout

    private var sedangRekam = false
    private var rekamModeAktif = false
    private var mulaiMs = 0L

    private val penyegar = object : Runnable {
        override fun run() {
            if (mikro.sedangJalan()) {
                val detik = (System.currentTimeMillis() - mulaiMs) / 1000
                txtTimer.text = String.format("%02d:%02d", detik / 60, detik % 60)
            }
            segarkanTombolMikro()
            segarkanMusikTombol()
            handler.postDelayed(this, 300L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mikro = Mikrofon(this)
        suara = Suara(this)
        mikro.onLevel = { lv -> barLevel.progress = lv }
        mikro.onError = { pesan -> Toast.makeText(this, pesan, Toast.LENGTH_LONG).show() }
        mikro.onSelesai = { berkas -> selesaiMikro(berkas) }
        PemutarMusik.mikro = mikro

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Tema.latarAtas)
        scroll.addView(buatLayar())
        setContentView(scroll)

        mintaIzin()
        if (Pengaturan.itagMac(this) != null) ItagService.mulai(this)
    }

    override fun onResume() {
        super.onResume()
        ItagService.onStatus = { _ -> segarkanItag() }
        segarkanSemua()
        handler.removeCallbacks(penyegar)
        handler.post(penyegar)
    }

    override fun onPause() {
        handler.removeCallbacks(penyegar)
        Pengaturan.simpanTeks(this, etTeks.text.toString())
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mikro.berhenti()
        if (isFinishing) PemutarMusik.hentikan()
        suara.tutup()
        super.onDestroy()
    }

    private fun mintaIzin() {
        val daftar = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) daftar.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) daftar.add(Manifest.permission.POST_NOTIFICATIONS)
        val kurang = daftar.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (kurang.isNotEmpty()) requestPermissions(kurang.toTypedArray(), 100)
    }

    // ---------- mikrofon ----------

    private fun tekanDengar() {
        if (mikro.sedangJalan()) {
            mikro.berhenti()
            return
        }
        mulaiMs = System.currentTimeMillis()
        mulaiMikro(true, false, null)
    }

    private fun tekanRekam() {
        if (mikro.sedangJalan()) {
            mikro.berhenti()
            return
        }
        mulaiMs = System.currentTimeMillis()
        mulaiMikro(swDengarRekam.isChecked, true, Rekaman.baru(this))
    }

    private fun mulaiMikro(monitor: Boolean, rekam: Boolean, berkas: File?) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            Toast.makeText(this, "Izinkan mikrofon dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = musikUriAktif()
        PemutarMusik.serahkanKeMikro(uri != null)
        mikro.volMusikLive = volMusikAktif()
        mikro.musikPutar = uri != null && PemutarMusik.sedangMain()
        sedangRekam = rekam
        rekamModeAktif = rekam
        mikro.mulai(monitor, rekam, berkas, uri)
        segarkanTombolMikro()
    }

    private fun selesaiMikro(berkas: File?) {
        val rekam = rekamModeAktif
        rekamModeAktif = false
        sedangRekam = false
        barLevel.progress = 0
        PemutarMusik.selesaiMikro()
        if (rekam && berkas != null) {
            Pengaturan.simpanRekamanTerakhir(this, berkas.absolutePath)
            Toast.makeText(this, "Rekaman disimpan", Toast.LENGTH_SHORT).show()
        } else if (rekam) {
            Toast.makeText(this, "Rekaman gagal disimpan", Toast.LENGTH_SHORT).show()
        }
        segarkanRekaman()
        segarkanTombolMikro()
    }

    private fun pilihMic(bt: Boolean) {
        if (mikro.sedangJalan()) return
        Pengaturan.simpanMicBt(this, bt)
        segarkanChip()
    }

    private fun musikUriAktif(): Uri? {
        if (!Pengaturan.musikAktif(this)) return null
        return Playlist.lagu(this, Pengaturan.musikIndex(this))?.let { Uri.parse(it.uri) }
    }

    private fun volMusikAktif(): Float = Pengaturan.musikVol(this) / 100f

    // ---------- musik ----------

    private fun tekanMainUtama() {
        if (PemutarMusik.sedangMain()) {
            PemutarMusik.jeda()
        } else {
            if (Playlist.ambil(this).isEmpty()) {
                Toast.makeText(this, "Tambahkan lagu di Pemutar Musik dulu", Toast.LENGTH_SHORT).show()
                return
            }
            PemutarMusik.mainkan(this)
        }
        segarkanMusikTombol()
    }

    private fun segarkanMusikTombol() {
        val lagu = Playlist.lagu(this, Pengaturan.musikIndex(this))
        val main = PemutarMusik.sedangMain()
        txtLagu.text = if (lagu == null) {
            "Belum ada lagu. Buka Pemutar Musik untuk menambah."
        } else {
            "${if (main) "▶" else "⏸"}  ${lagu.nama}"
        }
        btnMainUtama.text = if (main) "⏸" else "▶"
    }

    private fun segarkanMusik() {
        segarkanMusikTombol()
        swMusik.setOnCheckedChangeListener(null)
        swMusik.isChecked = Pengaturan.musikAktif(this)
        swMusik.setOnCheckedChangeListener { _, on -> Pengaturan.simpanMusikAktif(this, on) }
        val v = Pengaturan.musikVol(this)
        if (seekMusik.progress != v) seekMusik.progress = v
        txtVolMusik.text = "Volume musik $v%  ·  suara kamu 100%"
    }

    // ---------- iTag ----------

    private fun segarkanItag() {
        val mac = Pengaturan.itagMac(this)
        txtItag.text = when {
            mac == null -> "Belum ada iTag dipasangkan"
            ItagService.terhubung -> "✅ iTag terhubung"
            else -> "⏳ iTag tersimpan, belum tersambung"
        }
        btnItag.text = if (mac == null) "📡  Pasangkan iTag" else "📡  Ganti iTag"
        val belNama = Pengaturan.belNama(this)
        txtBel.text = if (Pengaturan.belUri(this) == null) "Belum ada MP3 bel" else "🔔 Bel: $belNama"
        val db = Pengaturan.boosterDb(this)
        if (seekBooster.progress != db) seekBooster.progress = db
        txtBooster.text = "Booster: +$db dB  (volume 100% + penguatan)"
    }

    private fun pilihBel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 402)
    }

    private fun putuskanItag() {
        Pengaturan.simpanItag(this, null)
        ItagService.henti(this)
        segarkanItag()
    }

    // ---------- teks ke suara ----------

    private fun putarTeks() {
        val t = etTeks.text.toString()
        Pengaturan.simpanTeks(this, t)
        suara.bicara(t)
    }

    private fun segarkanSuara() {
        txtSuara.text = "Nada ${String.format("%.2f", Pengaturan.nada(this))}  ·  " +
            "Kecepatan ${String.format("%.2f", Pengaturan.kecepatan(this))}  ·  Gaya ${Pengaturan.gaya(this)}"
    }

    // ---------- rekaman ----------

    private fun segarkanRekaman() {
        listRekaman.removeAllViews()
        val daftar = Rekaman.daftar(this)
        if (daftar.isEmpty()) {
            listRekaman.addView(teks(this, "Belum ada rekaman", 13f, Tema.redup))
            return
        }
        for (f in daftar.take(20)) {
            val baris = linear(this, false)
            baris.gravity = Gravity.CENTER_VERTICAL
            baris.setPadding(dp(10), dp(8), dp(10), dp(8))
            baris.background = kotak(this, Tema.tombol, 12, Tema.garis)
            baris.addView(teks(this, Rekaman.waktuTampil(f), 13f, Tema.teks), lp(0, PAS, 1f))
            baris.addView(ikonAksi("▶", Tema.hijau) { putarRekaman(f) }, lp(PAS, PAS))
            listRekaman.addView(baris, lp(PENUH, PAS, 0f, 0, 0, 0, dp(6)))
        }
    }

    private fun putarRekaman(f: File) {
        try {
            Booster.putar(this, { p -> p.setDataSource(f.absolutePath) })
        } catch (e: Exception) {
            Toast.makeText(this, "Rekaman tidak bisa diputar", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- status tombol ----------

    private fun segarkanSemua() {
        segarkanChip()
        segarkanMusik()
        segarkanItag()
        segarkanRekaman()
        segarkanTombolMikro()
        segarkanSuara()
    }

    private fun segarkanChip() {
        val bt = Pengaturan.micBluetooth(this)
        gayaChip(chipHp, !bt)
        gayaChip(chipBt, bt)
    }

    private fun gayaChip(t: TextView, aktif: Boolean) {
        t.background = riak(this, if (aktif) Tema.biru else Tema.tombol, 12, Tema.garis)
        t.setTextColor(if (aktif) Color.BLACK else Tema.teks)
    }

    private fun segarkanTombolMikro() {
        val jalan = mikro.sedangJalan()
        btnDengar.text = if (jalan && !sedangRekam) "⏹  Berhenti" else "🎧  Dengar langsung"
        btnRekam.text = if (jalan && sedangRekam) "⏹  Berhenti & simpan" else "⏺  Rekam"
        swDengarRekam.isEnabled = !jalan
        chipHp.isEnabled = !jalan
        chipBt.isEnabled = !jalan
        if (!jalan) {
            barLevel.progress = 0
        }
    }

    // ---------- tampilan ----------

    private fun buatLayar(): LinearLayout {
        val root = akarLatar()

        // header
        val header = linear(this, false)
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_cek)
        logo.adjustViewBounds = true
        header.addView(logo, lp(dp(56), dp(56), 0f, 0, 0, dp(10), 0))
        val judulBox = linear(this)
        judulBox.addView(teks(this, "Harry Cek Sound", 20f, Tema.teks, true))
        judulBox.addView(teks(this, "cek microphone by Harinfood", 12f, Tema.redup))
        header.addView(judulBox, lp(0, PAS, 1f))
        root.addView(header, lp(PENUH, PAS, 0f, 0, 0, 0, dp(12)))

        // kartu mikrofon
        val kMic = kartuTema(this)
        kMic.addView(teks(this, "🎤  Mikrofon", 17f, Tema.teks, true))
        val barisMic = linear(this, false)
        chipHp = teks(this, "📱 HP", 13f, Tema.teks, true)
        chipHp.gravity = Gravity.CENTER
        chipHp.setPadding(dp(10), dp(8), dp(10), dp(8))
        chipHp.setOnClickListener { pilihMic(false) }
        chipBt = teks(this, "🎧 Bluetooth", 13f, Tema.teks, true)
        chipBt.gravity = Gravity.CENTER
        chipBt.setPadding(dp(10), dp(8), dp(10), dp(8))
        chipBt.setOnClickListener { pilihMic(true) }
        barisMic.addView(chipHp, lp(0, PAS, 1f, dp(8), 0, dp(6), 0))
        barisMic.addView(chipBt, lp(0, PAS, 1f, dp(8), dp(6), 0, 0))
        kMic.addView(barisMic)

        barLevel = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        barLevel.max = 100
        barLevel.progress = 0
        barLevel.progressTintList = ColorStateList.valueOf(Tema.hijau)
        kMic.addView(barLevel, lp(PENUH, dp(10), 0f, dp(10), 0, 0, 0))

        txtTimer = teks(this, "00:00", 30f, Tema.cyan, true)
        txtTimer.gravity = Gravity.CENTER
        kMic.addView(txtTimer, lp(PENUH, PAS, 0f, dp(8), 0, 0, 0))

        val barisTombol = linear(this, false)
        btnDengar = aksiTombol("🎧  Dengar langsung") { tekanDengar() }
        btnRekam = aksiTombol("⏺  Rekam") { tekanRekam() }
        barisTombol.addView(btnDengar, lp(0, PAS, 1f, dp(10), 0, dp(6), 0))
        barisTombol.addView(btnRekam, lp(0, PAS, 1f, dp(10), dp(6), 0, 0))
        kMic.addView(barisTombol)

        swDengarRekam = Switch(this)
        swDengarRekam.text = "Dengar saat merekam"
        swDengarRekam.setTextColor(Tema.teks)
        kMic.addView(swDengarRekam, lp(PAS, PAS, 0f, dp(8), 0, 0, 0))
        root.addView(kMic, lp(PENUH, PAS, 0f, 0, 0, 0, 0))

        // kartu musik latar & pemutar
        val kMusik = kartuTema(this)
        kMusik.addView(teks(this, "🎵  Musik Latar & Pemutar", 17f, Tema.teks, true))
        txtLagu = teks(this, "", 14f, Tema.redup)
        kMusik.addView(txtLagu, lp(PENUH, PAS, 0f, dp(6), 0, 0, 0))

        val kontrolM = linear(this, false)
        kontrolM.gravity = Gravity.CENTER
        kontrolM.addView(tombolBulatAksi("⏮", 50) { PemutarMusik.sebelum(this) }, lp(dp(50), dp(50), 0f, dp(10), 0, dp(10), 0))
        btnMainUtama = tombolBulatAksi("▶", 60) { tekanMainUtama() }
        kontrolM.addView(btnMainUtama, lp(dp(60), dp(60), 0f, dp(10), 0, dp(10), 0))
        kontrolM.addView(tombolBulatAksi("⏭", 50) { PemutarMusik.berikut(this) }, lp(dp(50), dp(50), 0f, dp(10), 0, 0, 0))
        kMusik.addView(kontrolM, lp(PENUH, PAS))

        kMusik.addView(aksiTombol("🎵  Buka Pemutar & Daftar Lagu") {
            startActivity(Intent(this, PemutarActivity::class.java))
        }, lp(PENUH, PAS, 0f, dp(10), 0, 0, 0))

        swMusik = Switch(this)
        swMusik.text = "Pakai musik latar saat rekam / dengar"
        swMusik.setTextColor(Tema.teks)
        kMusik.addView(swMusik, lp(PENUH, PAS, 0f, dp(10), 0, 0, 0))

        txtVolMusik = teks(this, "", 13f, Tema.cyan, true)
        kMusik.addView(txtVolMusik, lp(PENUH, PAS, 0f, dp(8), 0, 0, 0))
        seekMusik = SeekBar(this)
        seekMusik.max = 100
        seekMusik.progressTintList = ColorStateList.valueOf(Tema.cyan)
        seekMusik.thumbTintList = ColorStateList.valueOf(Tema.biru)
        seekMusik.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, nilai: Int, dariUser: Boolean) {
                if (dariUser) PemutarMusik.aturVolume(this@MainActivity, nilai)
                txtVolMusik.text = "Volume musik $nilai%  ·  suara kamu 100%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kMusik.addView(seekMusik)
        kMusik.addView(
            teks(this, "Musik latar dibuat lebih kecil dari suara kamu agar vokal tetap jelas.", 12f, Tema.redup),
            lp(PENUH, PAS, 0f, dp(6), 0, 0, 0)
        )
        root.addView(kMusik, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))

        // kartu teks ke suara
        val kTts = kartuTema(this)
        kTts.addView(teks(this, "🗣  Teks ke Suara", 17f, Tema.teks, true))
        etTeks = EditText(this)
        etTeks.setText(Pengaturan.teksTts(this))
        etTeks.setTextColor(Tema.teks)
        etTeks.setHintTextColor(Tema.redup)
        etTeks.hint = "Tulis kalimat..."
        etTeks.minLines = 2
        etTeks.setPadding(dp(12), dp(10), dp(12), dp(10))
        etTeks.background = kotak(this, Tema.tombol, 12, Tema.garis)
        kTts.addView(etTeks, lp(PENUH, PAS, 0f, dp(8), 0, 0, 0))
        val barisTts = linear(this, false)
        barisTts.addView(aksiTombol("🔊  Putar Teks") { putarTeks() }, lp(0, PAS, 1f, dp(8), 0, dp(6), 0))
        barisTts.addView(aksiTombol("⚙  Atur Suara") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, lp(0, PAS, 1f, dp(8), dp(6), 0, 0))
        kTts.addView(barisTts)
        txtSuara = teks(this, "", 12f, Tema.redup)
        kTts.addView(txtSuara, lp(PENUH, PAS, 0f, dp(6), 0, 0, 0))
        root.addView(kTts, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))

        // kartu iTag
        val kItag = kartuTema(this)
        kItag.addView(teks(this, "🔔  iTag Tombol", 17f, Tema.teks, true))
        txtItag = teks(this, "", 14f, Tema.redup)
        kItag.addView(txtItag, lp(PENUH, PAS, 0f, dp(6), 0, 0, 0))
        val barisItag = linear(this, false)
        btnItag = aksiTombol("📡  Pasangkan iTag") {
            startActivity(Intent(this, ItagPairActivity::class.java))
        }
        barisItag.addView(btnItag, lp(0, PAS, 1f, dp(8), 0, dp(6), 0))
        barisItag.addView(aksiTombol("✖  Putuskan") { putuskanItag() }, lp(0, PAS, 1f, dp(8), dp(6), 0, 0))
        kItag.addView(barisItag)
        kItag.addView(
            teks(this, "1× tekan: MP3 bel\n2× tekan: putar rekaman\n3× tekan: bacakan teks (TTS)\nBunyi iTag diputar 100% dengan booster.", 13f, Tema.redup),
            lp(PENUH, PAS, 0f, dp(8), 0, 0, 0)
        )
        kItag.addView(aksiTombol("📂  Pilih MP3 Bel") { pilihBel() }, lp(PENUH, PAS, 0f, dp(8), 0, 0, 0))
        txtBel = teks(this, "", 13f, Tema.redup)
        kItag.addView(txtBel, lp(PENUH, PAS, 0f, dp(4), 0, 0, 0))
        txtBooster = teks(this, "", 13f, Tema.cyan, true)
        kItag.addView(txtBooster, lp(PENUH, PAS, 0f, dp(10), 0, 0, 0))
        seekBooster = SeekBar(this)
        seekBooster.max = 15
        seekBooster.progressTintList = ColorStateList.valueOf(Tema.kuning)
        seekBooster.thumbTintList = ColorStateList.valueOf(Tema.kuning)
        seekBooster.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, nilai: Int, dariUser: Boolean) {
                if (dariUser) Pengaturan.simpanBoosterDb(this@MainActivity, nilai)
                txtBooster.text = "Booster: +$nilai dB  (volume 100% + penguatan)"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kItag.addView(seekBooster)
        root.addView(kItag, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))

        // kartu rekaman
        val kRek = kartuTema(this)
        kRek.addView(teks(this, "📼  Rekaman", 17f, Tema.teks, true))
        listRekaman = linear(this)
        kRek.addView(listRekaman, lp(PENUH, PAS, 0f, dp(8), 0, 0, 0))
        root.addView(kRek, lp(PENUH, PAS, 0f, dp(12), 0, 0, 0))

        // pemasangan awal teks dari pengaturan
        segarkanSuara()
        return root
    }

    // ---------- hasil dari pemilih file ----------

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 402 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // izin sementara tetap bisa dipakai
            }
            Pengaturan.simpanBel(this, uri.toString(), namaFileUri(uri))
            segarkanItag()
        }
    }
}
