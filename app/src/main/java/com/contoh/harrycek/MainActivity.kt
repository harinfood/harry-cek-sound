package com.contoh.harrycek

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Layar utama: mic (dengar & rekam), teks ke suara, iTag, dan daftar rekaman. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mikro: Mikrofon
    private lateinit var suara: Suara
    private var player: MediaPlayer? = null

    private lateinit var chipHp: TextView
    private lateinit var chipBt: TextView
    private lateinit var barLevel: ProgressBar
    private lateinit var btnDengar: TextView
    private lateinit var btnRekam: TextView
    private lateinit var swDengarRekam: Switch
    private lateinit var txtTimer: TextView
    private lateinit var etTeks: EditText
    private lateinit var txtSuara: TextView
    private lateinit var txtItag: TextView
    private lateinit var btnItag: TextView
    private lateinit var listRekaman: LinearLayout
    private lateinit var txtMusik: TextView
    private lateinit var swMusik: Switch
    private lateinit var seekMusik: SeekBar
    private lateinit var txtVolMusik: TextView

    private var sedangRekam = false
    private var mulaiRekamMs = 0L

    private val penyegar = object : Runnable {
        override fun run() {
            if (sedangRekam && mikro.sedangJalan()) {
                val detik = (System.currentTimeMillis() - mulaiRekamMs) / 1000L
                txtTimer.text = "%02d:%02d".format(detik / 60, detik % 60)
            }
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suara = Suara(this)
        mikro = Mikrofon(this)
        mikro.onLevel = { lv -> barLevel.progress = lv }
        mikro.onSelesai = { berkas -> selesaiMikro(berkas) }
        mikro.onError = { pesan -> Toast.makeText(this, pesan, Toast.LENGTH_LONG).show() }
        setContentView(buatLayar())
        etTeks.setText(Pengaturan.teksTts(this))
        mintaIzin()
        if (Pengaturan.itagMac(this) != null) ItagService.mulai(this)
    }

    override fun onResume() {
        super.onResume()
        ItagService.onStatus = { _ -> segarkanItag() }
        segarkanItag()
        segarkanRekaman()
        segarkanSuara()
        segarkanMicChip()
        segarkanMusik()
        segarkanTombol()
        handler.removeCallbacks(penyegar)
        handler.post(penyegar)
    }

    override fun onPause() {
        Pengaturan.simpanTeks(this, etTeks.text.toString())
        ItagService.onStatus = null
        handler.removeCallbacks(penyegar)
        super.onPause()
    }

    override fun onDestroy() {
        mikro.berhenti()
        handler.removeCallbacksAndMessages(null)
        suara.tutup()
        player?.release()
        super.onDestroy()
    }

    // ---------- mic ----------

    private fun izinMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun mintaIzin() {
        val daftar = mutableListOf<String>()
        if (!izinMic()) daftar.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                daftar.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                daftar.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            daftar.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            daftar.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (daftar.isNotEmpty()) requestPermissions(daftar.toTypedArray(), 301)
    }

    private fun tekanDengar() {
        if (!izinMic()) {
            mintaIzin()
            return
        }
        if (mikro.sedangJalan()) {
            mikro.berhenti()
            return
        }
        sedangRekam = false
        mikro.mulai(true, false, null, musikUriAktif(), volMusikAktif())
        segarkanTombol()
    }

    private fun tekanRekam() {
        if (!izinMic()) {
            mintaIzin()
            return
        }
        if (mikro.sedangJalan()) {
            mikro.berhenti()
            return
        }
        sedangRekam = true
        mulaiRekamMs = System.currentTimeMillis()
        txtTimer.text = "00:00"
        mikro.mulai(swDengarRekam.isChecked, true, Rekaman.baru(this), musikUriAktif(), volMusikAktif())
        segarkanTombol()
    }

    private fun selesaiMikro(berkas: File?) {
        sedangRekam = false
        if (berkas != null && berkas.exists()) {
            Pengaturan.simpanRekamanTerakhir(this, berkas.absolutePath)
            Toast.makeText(this, "Rekaman tersimpan", Toast.LENGTH_SHORT).show()
        }
        barLevel.progress = 0
        segarkanRekaman()
        segarkanTombol()
    }

    private fun pilihMic(bt: Boolean) {
        if (mikro.sedangJalan()) {
            Toast.makeText(this, "Hentikan dulu sebelum ganti mic", Toast.LENGTH_SHORT).show()
            return
        }
        Pengaturan.simpanMicBt(this, bt)
        segarkanMicChip()
    }

    private fun segarkanMicChip() {
        val bt = Pengaturan.micBluetooth(this)
        styleChip(chipHp, !bt)
        styleChip(chipBt, bt)
    }

    private fun styleChip(t: TextView, aktif: Boolean) {
        t.background = if (aktif) kotakGradien(this, Tema.biru, Tema.cyan, 14) else kotak(this, Tema.tombol, 14)
        t.setTextColor(if (aktif) Color.parseColor("#0A1628") else Tema.redup)
    }

    private fun segarkanTombol() {
        val jalan = mikro.sedangJalan()
        val rekam = jalan && sedangRekam
        val dengar = jalan && !sedangRekam
        btnDengar.text = if (dengar) "⏹  Stop Dengar" else "▶  Dengar"
        btnRekam.text = if (rekam) "⏹  Stop Rekam" else "⏺  Rekam"
        btnDengar.alpha = if (rekam) 0.4f else 1f
        btnRekam.alpha = if (dengar) 0.4f else 1f
        if (!jalan) {
            barLevel.progress = 0
            if (!sedangRekam) txtTimer.text = "00:00"
        }
    }

    // ---------- teks ke suara ----------

    private fun segarkanSuara() {
        val nama = mapOf(
            "normal" to "Normal", "anak" to "Suara Anak", "berat" to "Suara Berat",
            "robot" to "Robot", "cepat" to "Cepat", "pelan" to "Pelan & Jelas", "kustom" to "Kustom"
        )
        txtSuara.text = "Gaya: ${nama[Pengaturan.gaya(this)] ?: "Normal"}  ·  Nada ${"%.2f".format(Pengaturan.nada(this))}  ·  Kecepatan ${"%.2f".format(Pengaturan.kecepatan(this))}"
    }

    private fun putarTeks() {
        val t = etTeks.text.toString()
        Pengaturan.simpanTeks(this, t)
        if (t.isBlank()) {
            Toast.makeText(this, "Tulis teks dulu", Toast.LENGTH_SHORT).show()
            return
        }
        suara.bicara(t)
    }

    // ---------- iTag ----------

    private fun segarkanItag() {
        val mac = Pengaturan.itagMac(this)
        txtItag.text = when {
            mac == null -> "Belum ada iTag. Tekan tombol di bawah untuk menghubungkan."
            ItagService.terhubung -> "✅  iTag terhubung"
            else -> "⏳  iTag belum terhubung. Nyalakan iTag, sambungan dicoba otomatis."
        }
        txtItag.setTextColor(if (ItagService.terhubung) Tema.hijau else Tema.redup)
        btnItag.text = if (mac == null) "🔗  Hubungkan iTag" else "✖  Lepas iTag"
    }

    // ---------- rekaman ----------

    private fun segarkanRekaman() {
        listRekaman.removeAllViews()
        val daftar = Rekaman.daftar(this).take(10)
        if (daftar.isEmpty()) {
            listRekaman.addView(teks(this, "Belum ada rekaman.", 13f, Tema.redup))
            return
        }
        for (f in daftar) {
            val baris = linear(this, false)
            baris.gravity = Gravity.CENTER_VERTICAL
            val info = linear(this)
            info.addView(teks(this, Rekaman.waktuTampil(f), 14f, Tema.teks, true))
            info.addView(teks(this, "${f.length() / 1024} KB", 12f, Tema.redup))
            baris.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            baris.addView(aksiKecil("▶") { putarFile(f.absolutePath) })
            baris.addView(aksiKecil("🗑") { hapusRekaman(f) })
            listRekaman.addView(baris, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, dp(8)))
        }
    }

    private fun aksiKecil(label: String, aksi: () -> Unit): TextView {
        val t = teks(this, label, 16f, Tema.teks, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        t.background = riak(this, Tema.tombol, 14)
        t.setOnClickListener { aksi() }
        t.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(6)
        }
        return t
    }

    private fun putarFile(path: String) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Rekaman tidak bisa diputar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hapusRekaman(f: File) {
        if (f.absolutePath == Pengaturan.rekamanTerakhir(this)) {
            val lain = Rekaman.daftar(this).firstOrNull { it.absolutePath != f.absolutePath }
            Pengaturan.simpanRekamanTerakhir(this, lain?.absolutePath)
        }
        f.delete()
        segarkanRekaman()
    }

    // ---------- musik latar ----------

    private fun musikUriAktif(): Uri? {
        if (!Pengaturan.musikAktif(this)) return null
        return Pengaturan.musikUri(this)?.let { Uri.parse(it) }
    }

    private fun volMusikAktif(): Float = Pengaturan.musikVol(this) / 100f

    private fun segarkanMusik() {
        val nama = Pengaturan.musikNama(this)
        txtMusik.text = if (nama.isEmpty()) "Belum ada MP3 dipilih" else "🎶 $nama"
        swMusik.setOnCheckedChangeListener(null)
        swMusik.isChecked = Pengaturan.musikAktif(this)
        swMusik.setOnCheckedChangeListener { _, on -> Pengaturan.simpanMusikAktif(this, on) }
        val v = Pengaturan.musikVol(this)
        if (seekMusik.progress != v) seekMusik.progress = v
        txtVolMusik.text = "Volume musik $v%  ·  suara kamu 100%"
    }

    private fun pilihMusik() {
        if (mikro.sedangJalan()) {
            Toast.makeText(this, "Hentikan dulu sebelum ganti musik", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 401)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 401 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Izin sementara tetap bisa dipakai
            }
            Pengaturan.simpanMusik(this, uri.toString(), namaFile(uri))
            Pengaturan.simpanMusikAktif(this, true)
            segarkanMusik()
        }
    }

    private fun namaFile(uri: Uri): String {
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

    // ---------- tampilan ----------

    private fun buatLayar(): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Tema.latarAtas)
        val root = akarLatar()
        scroll.addView(root)

        // Header dengan logo
        val header = linear(this, false)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(4), dp(10), dp(4), dp(14))
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_cek)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        header.addView(logo, LinearLayout.LayoutParams(dp(78), dp(78)).apply { marginEnd = dp(10) })
        val kolom = linear(this)
        kolom.addView(teks(this, "HARRY CEK SOUND", 22f, Tema.cyan, true))
        kolom.addView(teks(this, "cek microphone by Harinfood", 13f, Tema.redup))
        header.addView(kolom, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        // Kartu mikrofon
        val kMic = kartuTema(this)
        kMic.addView(teks(this, "🎤  Microphone", 17f, Tema.teks, true))
        val chips = linear(this, false)
        chipHp = chipPilih("📱  Mic HP") { pilihMic(false) }
        chipBt = chipPilih("🎧  Mic Bluetooth") { pilihMic(true) }
        chips.addView(chipHp, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, dp(10), dp(4), 0) })
        chips.addView(chipBt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(10), 0, 0) })
        kMic.addView(chips)

        barLevel = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        barLevel.max = 100
        barLevel.progressTintList = ColorStateList.valueOf(Tema.cyan)
        barLevel.progressBackgroundTintList = ColorStateList.valueOf(Tema.garis)
        kMic.addView(barLevel, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(14), 0f, dp(12), 0, 0, 0))

        swDengarRekam = Switch(this)
        swDengarRekam.text = "Dengar saat merekam"
        swDengarRekam.setTextColor(Tema.teks)
        swDengarRekam.isChecked = true
        kMic.addView(swDengarRekam, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(8), 0, 0, 0))

        txtTimer = teks(this, "00:00", 34f, Tema.cyan, true)
        txtTimer.gravity = Gravity.CENTER
        kMic.addView(txtTimer, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(6), 0, 0, 0))

        val baris = linear(this, false)
        btnDengar = tombolUtama("▶  Dengar") { tekanDengar() }
        btnRekam = tombolUtama("⏺  Rekam") { tekanRekam() }
        baris.addView(btnDengar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, dp(10), dp(4), 0) })
        baris.addView(btnRekam, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(10), 0, 0) })
        kMic.addView(baris)
        root.addView(kMic, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))

        // Kartu musik latar
        val kMusik = kartuTema(this)
        kMusik.addView(teks(this, "🎵  Musik Latar", 17f, Tema.teks, true))
        txtMusik = teks(this, "", 13f, Tema.redup)
        kMusik.addView(txtMusik, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(8), 0, 0, 0))
        val barisMusik = linear(this, false)
        barisMusik.gravity = Gravity.CENTER_VERTICAL
        barisMusik.addView(tombolUtama("📂  Pilih MP3") { pilihMusik() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, dp(10), dp(6), 0) })
        swMusik = Switch(this)
        swMusik.text = "Pakai"
        swMusik.setTextColor(Tema.teks)
        swMusik.setOnCheckedChangeListener { _, on -> Pengaturan.simpanMusikAktif(this, on) }
        barisMusik.addView(swMusik, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(10), 0, 0) })
        kMusik.addView(barisMusik)
        txtVolMusik = teks(this, "", 13f, Tema.cyan, true)
        kMusik.addView(txtVolMusik, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(10), 0, 0, 0))
        seekMusik = SeekBar(this)
        seekMusik.max = 100
        seekMusik.progressTintList = android.content.res.ColorStateList.valueOf(Tema.cyan)
        seekMusik.thumbTintList = android.content.res.ColorStateList.valueOf(Tema.biru)
        seekMusik.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                Pengaturan.simpanMusikVol(this@MainActivity, p)
                txtVolMusik.text = "Volume musik $p%  ·  suara kamu 100%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        kMusik.addView(seekMusik)
        kMusik.addView(teks(this, "Musik dibuat lebih kecil dari suara kamu agar vokal tetap jelas.", 12f, Tema.redup), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(6), 0, 0, 0))
        root.addView(kMusik, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))

        // Kartu teks ke suara
        val kTts = kartuTema(this)
        kTts.addView(teks(this, "💬  Teks ke Suara", 17f, Tema.teks, true))
        etTeks = EditText(this)
        etTeks.hint = "Tulis teks yang ingin diucapkan..."
        etTeks.setHintTextColor(Tema.redup)
        etTeks.setTextColor(Tema.teks)
        etTeks.minLines = 3
        etTeks.gravity = Gravity.TOP or Gravity.START
        etTeks.background = kotak(this, Tema.tombol, 14)
        etTeks.setPadding(dp(12), dp(10), dp(12), dp(10))
        kTts.addView(etTeks, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(10), 0, 0, 0))
        val barisTts = linear(this, false)
        barisTts.addView(tombolUtama("🔊  Putar") { putarTeks() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, dp(10), dp(4), 0) })
        barisTts.addView(tombolUtama("🎭  Atur Suara") { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(10), 0, 0) })
        kTts.addView(barisTts)
        txtSuara = teks(this, "", 12f, Tema.redup)
        kTts.addView(txtSuara, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(8), 0, 0, 0))
        root.addView(kTts, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))

        // Kartu iTag
        val kItag = kartuTema(this)
        kItag.addView(teks(this, "🔘  Tombol iTag", 17f, Tema.teks, true))
        txtItag = teks(this, "", 14f, Tema.redup)
        kItag.addView(txtItag, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(8), 0, 0, 0))
        kItag.addView(teks(this, "1× tekan = bacakan teks  ·  2× tekan = putar rekaman terakhir", 12f, Tema.cyan), lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(6), 0, 0, 0))
        btnItag = tombolUtama("🔗  Hubungkan iTag") {
            if (Pengaturan.itagMac(this) == null) {
                startActivity(Intent(this, ItagPairActivity::class.java))
            } else {
                ItagService.henti(this)
                Pengaturan.simpanItag(this, null)
                segarkanItag()
            }
        }
        kItag.addView(btnItag, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(10), 0, 0, 0))
        root.addView(kItag, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))

        // Kartu rekaman
        val kRek = kartuTema(this)
        kRek.addView(teks(this, "📼  Rekaman", 17f, Tema.teks, true))
        listRekaman = linear(this)
        kRek.addView(listRekaman, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(10), 0, 0, 0))
        root.addView(kRek, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))

        return scroll
    }

    private fun chipPilih(label: String, aksi: () -> Unit): TextView {
        val t = teks(this, label, 13f, Tema.teks, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(12), dp(8), dp(12))
        t.setOnClickListener { aksi() }
        return t
    }

    private fun tombolUtama(label: String, aksi: () -> Unit): TextView {
        val t = teks(this, label, 15f, Tema.teks, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(10), dp(14), dp(10), dp(14))
        t.background = riak(this, Tema.biru, 18)
        t.setOnClickListener { aksi() }
        return t
    }
}
