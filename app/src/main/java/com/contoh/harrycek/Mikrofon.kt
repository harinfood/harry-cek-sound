package com.contoh.harrycek

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mikrofon: dengar langsung (monitor) dan/atau rekam ke WAV.
 * Sumber: mic HP (bawaan) atau mic headset Bluetooth (SCO).
 */
class Mikrofon(private val ctx: Context) {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var jalan = false
    private var worker: Thread? = null

    var onLevel: ((Int) -> Unit)? = null
    var onSelesai: ((File?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun sedangJalan(): Boolean = jalan

    fun mulai(monitor: Boolean, rekam: Boolean, berkas: File?) {
        if (jalan) return
        jalan = true
        val bt = Pengaturan.micBluetooth(ctx)
        worker = Thread { jalankan(monitor, rekam, berkas, bt) }.apply {
            name = "mikrofon"
            start()
        }
    }

    fun berhenti() {
        jalan = false
    }

    private fun jalankan(monitor: Boolean, rekam: Boolean, berkas: File?, bt: Boolean) {
        val rate = if (bt) 16000 else 44100
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        var raf: RandomAccessFile? = null
        var hasil: File? = null
        try {
            if (bt) {
                aktifkanBluetooth()
                Thread.sleep(1200L) // beri waktu sambungan SCO siap
            }
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) throw IllegalStateException("Format audio tidak didukung")
            val sumber = if (bt) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            val bufBytes = max(minBuf, 4096)
            val r = AudioRecord(sumber, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes * 2)
            rec = r
            if (r.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Mikrofon tidak bisa dibuka")
            if (bt && Build.VERSION.SDK_INT >= 23) {
                val input = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (input != null) r.setPreferredDevice(input)
            }
            val buf = ShortArray(bufBytes / 2)

            if (monitor) {
                val outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val usage = if (bt) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(outMin, 4096) * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
            }

            if (rekam && berkas != null) {
                berkas.parentFile?.mkdirs()
                val f = RandomAccessFile(berkas, "rw")
                f.setLength(0)
                tulisHeader(f, rate, 0)
                f.seek(44)
                raf = f
                hasil = berkas
            }

            r.startRecording()
            while (jalan) {
                val n = r.read(buf, 0, buf.size)
                if (n <= 0) continue
                track?.write(buf, 0, n)
                val fs = raf
                if (fs != null) {
                    val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n) bb.putShort(buf[i])
                    fs.write(bb.array())
                }
                val cb = onLevel
                if (cb != null) {
                    val lv = level(buf, n)
                    handler.post { cb(lv) }
                }
            }
        } catch (e: Exception) {
            val pesan = e.message ?: "Gagal memakai mikrofon"
            handler.post { onError?.invoke(pesan) }
            hasil = null
        } finally {
            try { rec?.stop() } catch (e: Exception) { }
            rec?.release()
            try { track?.stop() } catch (e: Exception) { }
            track?.release()
            val fs = raf
            if (fs != null) {
                try {
                    tulisHeader(fs, rate, (fs.length() - 44).toInt())
                } catch (e: Exception) { }
                try { fs.close() } catch (e: Exception) { }
            }
            // Rekaman kosong (tanpa data) dibuang
            val h = hasil
            if (h != null && h.length() <= 44L) {
                h.delete()
                hasil = null
            }
            if (bt) matikanBluetooth()
            jalan = false
            val akhir = hasil
            handler.post { onSelesai?.invoke(akhir) }
        }
    }

    private fun level(buf: ShortArray, n: Int): Int {
        var s = 0.0
        for (i in 0 until n) s += buf[i].toDouble() * buf[i].toDouble()
        val rms = sqrt(s / n)
        return (rms / 6000.0 * 100.0).toInt().coerceIn(0, 100)
    }

    @Suppress("DEPRECATION")
    private fun aktifkanBluetooth() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val dev = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (dev != null) audio.setCommunicationDevice(dev)
        } else {
            audio.startBluetoothSco()
            audio.isBluetoothScoOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun matikanBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) {
            audio.clearCommunicationDevice()
        } else {
            audio.stopBluetoothSco()
            audio.isBluetoothScoOn = false
        }
        audio.mode = AudioManager.MODE_NORMAL
    }
}

/** Menulis header WAV (PCM 16-bit mono) di awal berkas. */
fun tulisHeader(raf: RandomAccessFile, rate: Int, dataLen: Int) {
    val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray())
    bb.putInt(36 + dataLen)
    bb.put("WAVE".toByteArray())
    bb.put("fmt ".toByteArray())
    bb.putInt(16)
    bb.putShort(1)
    bb.putShort(1)
    bb.putInt(rate)
    bb.putInt(rate * 2)
    bb.putShort(2)
    bb.putShort(16)
    bb.put("data".toByteArray())
    bb.putInt(dataLen)
    raf.seek(0)
    raf.write(bb.array())
}
