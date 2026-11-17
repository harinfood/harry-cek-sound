package com.contoh.harrycek

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.UUID

/**
 * Layanan iTag di latar belakang.
 * 1x tekan = MP3 bel khusus.  2x tekan = putar rekaman terakhir.  3x tekan = TTS (teks).
 * Semua bunyi diputar 100% dengan booster.
 */
class ItagService : Service() {

    companion object {
        private val UUID_LAYANAN: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val UUID_KARAKTER: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val ID_NOTIF = 3001
        private const val KANAL = "itag"
        private const val JEDA_PANTUL = 60L
        private const val JENDELA_KETUKAN = 500L

        @Volatile
        var terhubung = false

        /** Dipanggil di thread utama saat status sambungan berubah. */
        var onStatus: ((Boolean) -> Unit)? = null

        fun mulai(c: Context) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    c.startForegroundService(Intent(c, ItagService::class.java))
                } else {
                    c.startService(Intent(c, ItagService::class.java))
                }
            } catch (e: Exception) {
                // Izin atau kebijakan sistem menolak layanan; aplikasi tetap berjalan
            }
        }

        fun henti(c: Context) {
            try {
                c.stopService(Intent(c, ItagService::class.java))
            } catch (e: Exception) { }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var suara: Suara? = null
    private var lastPressMs = 0L
    private var timerAktif = false
    private var jumlahTekan = 0

    private val selesaiTimer = Runnable { selesaiKetukan() }
    private val cobaLagi = Runnable { sambung() }

    private val callbackGatt = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.post { setStatus(true) }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { setStatus(false) }
                handler.postDelayed(cobaLagi, 3000L)
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val karakter = g.getService(UUID_LAYANAN)?.getCharacteristic(UUID_KARAKTER) ?: return
            g.setCharacteristicNotification(karakter, true)
            val cccd = karakter.getDescriptor(UUID_CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            tekanItag()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            tekanItag()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        suara = Suara(this)
        buatKanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notif = bangunNotif(false)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(ID_NOTIF, notif)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Pengaturan.itagMac(this) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        sambung()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun sambung() {
        val mac = Pengaturan.itagMac(this) ?: return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!adapter.isEnabled) {
            handler.postDelayed(cobaLagi, 3000L)
            return
        }
        val dev: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: IllegalArgumentException) {
            return
        }
        gatt?.close()
        try {
            gatt = dev.connectGatt(this, true, callbackGatt, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            // Izin Bluetooth belum diberikan; sambungan dicoba lagi nanti
            handler.postDelayed(cobaLagi, 5000L)
        }
    }

    private fun setStatus(v: Boolean) {
        terhubung = v
        onStatus?.invoke(v)
        getSystemService(NotificationManager::class.java).notify(ID_NOTIF, bangunNotif(v))
    }

    /** Debounce 60 ms, lalu hitung ketukan dalam jendela 500 ms. */
    private fun tekanItag() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPressMs < JEDA_PANTUL) return
        lastPressMs = now
        jumlahTekan++
        if (!timerAktif) {
            timerAktif = true
            handler.postDelayed(selesaiTimer, JENDELA_KETUKAN)
        }
    }

    private fun selesaiKetukan() {
        timerAktif = false
        val n = jumlahTekan
        jumlahTekan = 0
        when {
            n >= 3 -> bacakanBerkas()
            n == 2 -> putarRekaman()
            else -> putarBel()
        }
    }

    /** 1x tekan: MP3 bel khusus. */
    private fun putarBel() {
        val uri = Pengaturan.belUri(this)
        if (uri == null) {
            suara?.bicara("Belum ada MP3 bel")
            return
        }
        mainkanAman { p -> p.setDataSource(this, Uri.parse(uri)) }
    }

    /** 2x tekan: putar rekaman terakhir. */
    private fun putarRekaman() {
        val path = Pengaturan.rekamanTerakhir(this)
        if (path == null || !File(path).exists()) {
            suara?.bicara("Belum ada rekaman")
            return
        }
        mainkanAman { p -> p.setDataSource(path) }
    }

    /** 3x tekan: TTS dibuat jadi berkas dulu, lalu diputar dengan booster. */
    private fun bacakanBerkas() {
        val teks = Pengaturan.teksTts(this)
        if (teks.isBlank()) {
            suara?.bicara("Tulisan masih kosong")
            return
        }
        val s = suara ?: return
        val berkas = File(cacheDir, "itag_tts.wav")
        s.keBerkas(teks, berkas) { ok ->
            if (ok && berkas.exists() && berkas.length() > 44) {
                mainkanAman { p -> p.setDataSource(berkas.absolutePath) }
            } else {
                suara?.bicara(teks)
            }
        }
    }

    private fun mainkanAman(sumber: (MediaPlayer) -> Unit) {
        try {
            Booster.putar(this, sumber)
        } catch (e: Exception) {
            suara?.bicara("Berkas tidak bisa diputar")
        }
    }

    private fun bangunNotif(sudahTerhubung: Boolean): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val isi = if (sudahTerhubung) {
            "iTag terhubung · 1x: bel · 2x: rekaman · 3x: TTS"
        } else {
            "Mencari iTag… tekan tombol iTag setelah tersambung"
        }
        return Notification.Builder(this, KANAL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Harry Cek Sound")
            .setContentText(isi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun buatKanal() {
        val nm = getSystemService(NotificationManager::class.java)
        val kanal = NotificationChannel(KANAL, "Tombol iTag", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(kanal)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        terhubung = false
        onStatus?.invoke(false)
        suara?.tutup()
        Booster.stop()
        super.onDestroy()
    }
}
