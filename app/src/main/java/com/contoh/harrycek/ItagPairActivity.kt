package com.contoh.harrycek

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Mencari iTag di sekitar lalu menyimpannya sebagai perangkat tombol. */
class ItagPairActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val perangkat = LinkedHashMap<String, String>()
    private lateinit var daftar: LinearLayout
    private lateinit var txtInfo: TextView
    private var scanner: BluetoothLeScanner? = null
    private var sedangScan = false

    private val callbackScan = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val nama = result.scanRecord?.deviceName ?: d.name ?: "(tanpa nama)"
            if (!perangkat.containsKey(d.address)) {
                perangkat[d.address] = nama
                handler.post { tampilDaftar() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buatLayar())
    }

    override fun onDestroy() {
        berhentiScan()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buatLayar(): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Tema.latarAtas)
        val root = akarLatar()
        scroll.addView(root)
        root.addView(barAtas("Hubungkan iTag") { finish() })

        val kartu = kartuTema(this)
        txtInfo = teks(this, "Tekan tombol di bawah, lalu nyalakan iTag di dekat HP.", 14f, Tema.redup)
        kartu.addView(txtInfo)
        val cari = teks(this, "🔍  Cari iTag", 16f, Tema.teks, true)
        cari.gravity = Gravity.CENTER
        cari.setPadding(dp(12), dp(14), dp(12), dp(14))
        cari.background = riak(this, Tema.biru, 20)
        cari.setOnClickListener { mulaiScan() }
        kartu.addView(cari, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))
        root.addView(kartu)

        daftar = linear(this)
        root.addView(daftar, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(12), 0, 0, 0))
        return scroll
    }

    private fun izinBluetoothAda(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun mintaIzin() {
        val list = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissions(list, 102)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (izinBluetoothAda()) mulaiScan()
        else Toast.makeText(this, "Izin Bluetooth diperlukan", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun mulaiScan() {
        if (!izinBluetoothAda()) {
            mintaIzin()
            return
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Nyalakan Bluetooth dulu", Toast.LENGTH_SHORT).show()
            return
        }
        berhentiScan()
        perangkat.clear()
        tampilDaftar()
        scanner = adapter.bluetoothLeScanner
        sedangScan = true
        txtInfo.text = "Mencari perangkat Bluetooth… (12 detik)"
        scanner?.startScan(callbackScan)
        handler.postDelayed({
            berhentiScan()
            txtInfo.text = if (perangkat.isEmpty()) "Tidak ada perangkat. Pastikan iTag menyala, lalu coba lagi." else "Pilih iTag kamu:"
        }, 12000L)
    }

    @SuppressLint("MissingPermission")
    private fun berhentiScan() {
        if (!sedangScan) return
        sedangScan = false
        try { scanner?.stopScan(callbackScan) } catch (e: Exception) { }
    }

    private fun tampilDaftar() {
        daftar.removeAllViews()
        // Perangkat yang namanya mengandung "tag" ditampilkan paling atas
        val urut = perangkat.entries.sortedByDescending { it.value.lowercase().contains("tag") }
        for ((mac, nama) in urut) {
            val t = teks(this, "$nama\n$mac", 15f, Tema.teks, true)
            t.setPadding(dp(16), dp(14), dp(16), dp(14))
            val itagLike = nama.lowercase().contains("tag")
            t.background = kotak(this, if (itagLike) Color.parseColor("#0F4C75") else Tema.kartu, 16, Tema.garis)
            t.setOnClickListener { pilih(mac, nama) }
            daftar.addView(t, lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, 0, 0, 0, dp(8)))
        }
    }

    private fun pilih(mac: String, nama: String) {
        berhentiScan()
        Pengaturan.simpanItag(this, mac)
        ItagService.mulai(this)
        Toast.makeText(this, "iTag \"$nama\" disimpan", Toast.LENGTH_SHORT).show()
        finish()
    }
}
