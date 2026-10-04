package com.smk.absensikelas

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R

class RiwayatAdapter(private val list: List<Map<String, String>>) :
    RecyclerView.Adapter<RiwayatAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tgl = v.findViewById<TextView>(R.id.tvTanggalRiwayat)
        val status = v.findViewById<TextView>(R.id.tvStatusRiwayat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_riwayat, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = list[position]
        holder.tgl.text = data["tanggal"]

        // Cari status yang bernilai "1" dari Map DatabaseHelper[cite: 2]
        val st = when {
            data["Hadir"] == "1" -> "Hadir"
            data["Sakit"] == "1" -> "Sakit"
            data["Izin"] == "1" -> "Izin"
            data["Alpha"] == "1" -> "Alpha"
            data["Bolos"] == "1" -> "Bolos"
            else -> "-"
        }

        holder.status.text = st
        // Set warna background berdasarkan status
        holder.status.setBackgroundColor(when(st) {
            "Hadir" -> Color.parseColor("#4CAF50")
            "Alpha", "Bolos" -> Color.parseColor("#F44336")
            else -> Color.parseColor("#FFC107")
        })
    }

    override fun getItemCount() = list.size
}