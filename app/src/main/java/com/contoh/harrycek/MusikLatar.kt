
package com.contoh.harrycek

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Membaca file musik (MP3, M4A, dll.) menjadi PCM mono pada sample rate mic. */
object MusikLatar {

    private const val MAKS_DETIK = 600 // batas 10 menit agar memori aman

    fun decode(ctx: Context, uri: Uri, targetRate: Int): ShortArray {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                track = i
                fmt = f
                break
            }
        }
        val f0 = fmt
        if (track < 0 || f0 == null) {
            ex.release()
            throw IllegalArgumentException("Berkas bukan audio")
        }
        ex.selectTrack(track)
        val mime = f0.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("Format tidak dikenal")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(f0, null, null, 0)
        codec.start()

        var rate = f0.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = f0.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val blok = ArrayList<ShortArray>()
        var total = 0
        val info = MediaCodec.BufferInfo()
        var inputSelesai = false
        var outputSelesai = false

        while (!outputSelesai) {
            if (!inputSelesai) {
                val inIdx = codec.dequeueInputBuffer(10000L)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)
                    if (buf != null) {
                        buf.clear()
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputSelesai = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10000L)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (outIdx >= 0) {
                val ob = codec.getOutputBuffer(outIdx)
                if (ob != null && info.size > 0) {
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val sb = ob.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = sb.remaining() / ch
                    val mono = ShortArray(frames)
                    for (fr in 0 until frames) {
                        var s = 0
                        for (c in 0 until ch) s += sb.get(fr * ch + c)
                        mono[fr] = (s / ch).toShort()
                    }
                    blok.add(mono)
                    total += frames
                }
                codec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputSelesai = true
                if (total > rate * MAKS_DETIK) outputSelesai = true
            }
        }
        codec.stop()
        codec.release()
        ex.release()

        val src = ShortArray(total)
        var p = 0
        for (b in blok) {
            System.arraycopy(b, 0, src, p, b.size)
            p += b.size
        }
        return resample(src, rate, targetRate)
    }

    /** Resampling linear sederhana. */
    private fun resample(src: ShortArray, dari: Int, ke: Int): ShortArray {
        if (dari == ke || src.isEmpty()) return src
        val n = (src.size.toLong() * ke / dari).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val pos = i.toDouble() * dari / ke
            val i0 = pos.toInt().coerceAtMost(src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val frac = pos - i0
            out[i] = (src[i0] + (src[i1] - src[i0]) * frac).toInt().toShort()
        }
        return out
    }
}
