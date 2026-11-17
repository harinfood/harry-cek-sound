package com.contoh.harrycek

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

data class Lagu(val uri: String, val nama: String)

/** Daftar lagu musik latar / pemutar, disimpan sebagai JSON. */
object Playlist {

    fun ambil(c: Context): MutableList<Lagu> {
        val out = mutableListOf<Lagu>()
        try {
            val arr = JSONArray(Pengaturan.playlistJson(c))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Lagu(o.getString("uri"), o.getString("nama")))
            }
        } catch (e: Exception) {
            // data rusak dianggap kosong
        }
        return out
    }

    private fun simpan(c: Context, list: List<Lagu>) {
        val arr = JSONArray()
        for (l in list) {
            arr.put(JSONObject().put("uri", l.uri).put("nama", l.nama))
        }
        Pengaturan.simpanPlaylistJson(c, arr.toString())
    }

    fun tambah(c: Context, uri: String, nama: String) {
        val list = ambil(c)
        if (list.any { it.uri == uri }) return
        list.add(Lagu(uri, nama))
        simpan(c, list)
    }

    fun hapus(c: Context, i: Int) {
        val list = ambil(c)
        if (i !in list.indices) return
        list.removeAt(i)
        simpan(c, list)
        val idx = Pengaturan.musikIndex(c)
        val baru = when {
            i < idx -> idx - 1
            else -> idx
        }
        Pengaturan.simpanMusikIndex(c, baru.coerceIn(0, max(0, list.size - 1)))
    }

    fun lagu(c: Context, i: Int): Lagu? = ambil(c).getOrNull(i)
}
